package io.gnosis.safe.ui.transactions.execution

import androidx.annotation.VisibleForTesting
import io.gnosis.data.backend.rpc.RpcClient
import io.gnosis.data.models.Chain
import io.gnosis.data.models.Owner
import io.gnosis.data.models.Safe
import io.gnosis.data.models.baseRpcUrl
import io.gnosis.data.models.transaction.DetailedExecutionInfo
import io.gnosis.data.models.transaction.TxData
import io.gnosis.data.repositories.CredentialsRepository
import io.gnosis.data.repositories.TransactionLocalRepository
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.data.utils.toSignature
import io.gnosis.safe.Tracker
import io.gnosis.safe.TxExecField
import io.gnosis.safe.ui.base.AppDispatchers
import io.gnosis.safe.ui.base.BaseStateViewModel
import io.gnosis.safe.ui.base.BaseStateViewModel.ViewAction.Loading
import io.gnosis.safe.ui.settings.app.SettingsHandler
import io.gnosis.safe.ui.settings.owner.list.OwnerViewData
import io.gnosis.safe.ui.transactions.details.SigningMode
import io.gnosis.safe.utils.BalanceFormatter
import io.gnosis.safe.utils.convertAmount
import pm.gnosis.crypto.ECDSASignature
import pm.gnosis.model.Solidity
import pm.gnosis.models.Transaction
import pm.gnosis.models.Wei
import pm.gnosis.svalinn.accounts.utils.hash
import pm.gnosis.svalinn.accounts.utils.rlp
import pm.gnosis.utils.asEthereumAddressString
import pm.gnosis.utils.toHexString
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

class TxReviewViewModel
@Inject constructor(
    private val safeRepository: SafeRepository,
    private val credentialsRepository: CredentialsRepository,
    private val localTxRepository: TransactionLocalRepository,
    private val settingsHandler: SettingsHandler,
    private val rpcClient: RpcClient,
    private val balanceFormatter: BalanceFormatter,
    private val tracker: Tracker,
    appDispatchers: AppDispatchers
) : BaseStateViewModel<TxReviewState>(appDispatchers) {

    lateinit var activeSafe: Safe
        private set

    var executionKey: OwnerViewData? = null
        private set

    var minNonce: BigInteger? = null
        private set

    var nonce: BigInteger? = null
        private set

    var gasLimit: BigInteger? = null
        private set

    var gasPrice: BigDecimal? = null
        private set

    var maxPriorityFeePerGas: BigDecimal? = null
        private set

    var maxFeePerGas: BigDecimal? = null
        private set

    private var txData: TxData? = null

    private var executionInfo: DetailedExecutionInfo? = null

    private var userEditedFeeData: Boolean = false

    private var ethTx: Transaction? = null

    private var ethTxSignature: ECDSASignature? = null

    init {
        safeLaunch {
            activeSafe = safeRepository.getActiveSafe()!!
            rpcClient.updateRpcUrl(activeSafe.chain)
        }
    }

    override fun initialState() = TxReviewState(viewAction = null)

    fun setTxData(txData: TxData, executionInfo: DetailedExecutionInfo) {
        this.txData = txData
        this.executionInfo = executionInfo
        loadDefaultKey()
    }

    fun isInitialized(): Boolean = txData != null && executionInfo != null

    fun isLoading(): Boolean {
        val viewAction = (state.value as TxReviewState).viewAction
        return (viewAction is Loading && viewAction.isLoading)
    }

    fun isLegacy(): Boolean {
        return ethTx is Transaction.Legacy
    }

    @VisibleForTesting
    fun loadDefaultKey() {
        safeLaunch {
            updateState {
                TxReviewState(viewAction = Loading(true))
            }
            val owners = credentialsRepository.owners().map { OwnerViewData(it.address, it.name, it.type) }
            
            // FIXED: Match web frontend behavior - any local key can execute (not just Safe signers)
            val acceptedOwners = owners.filter { localOwner ->
                // Allow any key type except Ledger Nano X and Tangem (hardware wallets can't execute)
                localOwner.type != Owner.Type.LEDGER_NANO_X && localOwner.type != Owner.Type.TANGEM
            }
            
            android.util.Log.i("TxReviewViewModel", "═══ EXECUTION KEY SELECTION ═══")
            android.util.Log.i("TxReviewViewModel", "Total local owners: ${owners.size}")
            android.util.Log.i("TxReviewViewModel", "Accepted for execution: ${acceptedOwners.size}")
            owners.forEach { owner ->
                val isAccepted = acceptedOwners.contains(owner)
                android.util.Log.i("TxReviewViewModel", "Owner: ${owner.address.asEthereumAddressString()} (${owner.type}) - Accepted: $isAccepted")
            }
            android.util.Log.i("TxReviewViewModel", "Safe signers:")
            activeSafe.signingOwners.forEach { signer ->
                android.util.Log.i("TxReviewViewModel", "Safe signer: ${signer.asEthereumAddressString()}")
            }
                // select owner with highest balance
                kotlin.runCatching {
                    rpcClient.getBalances(acceptedOwners.map { it.address })
                }.onSuccess {
                    executionKey = acceptedOwners
                        // TODO: Remove this filter when Ledger tx execution is implemented
                        .filter {
                            it.type != Owner.Type.LEDGER_NANO_X && it.type != Owner.Type.TANGEM
                        }
                        .mapIndexed { index, owner ->
                            owner to it[index]
                        }
                        .sortedByDescending {
                            it.second?.value
                        }.map {
                            it.first.copy(
                                balance = balanceString(it.second?.value ?: BigInteger.ZERO),
                                zeroBalance = it.second?.value == BigInteger.ZERO
                            )
                        }
                        .first()

                    updateState {
                        TxReviewState(viewAction = DefaultKey(key = executionKey))
                    }
                    estimate()

                }.onFailure { error ->
                    android.util.Log.e("TxReviewViewModel", "═══ BALANCE LOADING FAILED ═══")
                    android.util.Log.e("TxReviewViewModel", "Error: ${error.message}")
                    android.util.Log.e("TxReviewViewModel", "Error type: ${error.javaClass.simpleName}")
                    android.util.Log.e("TxReviewViewModel", "Accepted owners count: ${acceptedOwners.size}")
                    android.util.Log.e("TxReviewViewModel", "RPC URL: ${activeSafe.chain.baseRpcUrl()}")
                    
                    if (acceptedOwners.isEmpty()) {
                        android.util.Log.e("TxReviewViewModel", "❌ NO ACCEPTED OWNERS - Cannot proceed with execution")
                        throw IllegalStateException("No execution keys available")
                    } else {
                        android.util.Log.w("TxReviewViewModel", "⚠️ Using first owner without balance check")
                        // Fallback: use first available owner without balance check
                        executionKey = acceptedOwners.first().copy(
                            balance = "Unknown",
                            zeroBalance = false
                        )
                        updateState {
                            TxReviewState(viewAction = DefaultKey(key = executionKey))
                        }
                        estimate()
                    }
                }
        }
    }

    fun updateDefaultKey(address: Solidity.Address) {
        safeLaunch {
            address.let {
                val owner = credentialsRepository.owner(it)!!
                if (executionKey?.address != address) {
                    tracker.logTxExecKeyChanged()
                }
                executionKey = OwnerViewData(owner.address, owner.name, owner.type)
                updateState {
                    TxReviewState(viewAction = DefaultKey(key = executionKey))
                }
                estimate()
            }
        }
    }

    fun onSelectKey() {
        safeLaunch {
            updateState {
                TxReviewState(
                    viewAction = ViewAction.NavigateTo(
                        TxReviewFragmentDirections.actionTxReviewFragmentToSigningOwnerSelectionFragment(
                            missingSigners = null,
                            signingMode = SigningMode.EXECUTION,
                            chain = activeSafe.chain
                        )
                    )
                )
            }
            updateState {
                TxReviewState(
                    viewAction = ViewAction.None
                )
            }
        }
    }

    fun estimate() {
        safeLaunch {
            if (executionInfo is DetailedExecutionInfo.MultisigExecutionDetails) {
                executionKey?.let {

                    updateState {
                        TxReviewState(viewAction = Loading(true))
                    }

                    kotlin.runCatching {

                        ethTx = rpcClient.ethTransaction(
                            activeSafe,
                            it.address,
                            txData!!,
                            executionInfo as DetailedExecutionInfo.MultisigExecutionDetails
                        )

                        rpcClient.estimate(ethTx!!)

                    }.onSuccess { estimationParams ->

                        executionKey = executionKey!!.copy(
                            balance = balanceString(estimationParams.balance),
                            zeroBalance = estimationParams.gasPrice == BigInteger.ZERO
                        )

                        updateState {
                            TxReviewState(viewAction = DefaultKey(key = executionKey))
                        }

                        //  base fee amount
                        val baseFee = estimationParams.gasPrice
                        minNonce = estimationParams.nonce
                        // adjust nonce if it is lower than the minimum
                        // this can happen if other transactions have been sent from the same account
                        // while the user was on the tx review screen
                        if (nonce ?: BigInteger.ZERO < minNonce) {
                            nonce = minNonce
                        }
                        // If user has not edited the fee data, we set the fee values
                        // Otherwise, we keep the user's values
                        if (!userEditedFeeData) {
                            nonce = minNonce
                            gasLimit = estimationParams.estimate

                            // fix for a bug in Nethermind requiring 30% increase in the gas estimation
                            // on gnosis chain for transactions with positive safeTxGas
                            if (activeSafe.chainId == Chain.ID_GNOSIS &&
                                (executionInfo as DetailedExecutionInfo.MultisigExecutionDetails).safeTxGas > BigInteger.ZERO) {
                                gasLimit = gasLimit!!.multiply(BigInteger.valueOf(130)).divide(BigInteger.valueOf(100))
                            }

                            if (isLegacy()) {
                                gasPrice = Wei(baseFee).toGWei(activeSafe.chain.currency.decimals)
                            } else {
                                maxPriorityFeePerGas =
                                    Wei(BigInteger.valueOf(DEFAULT_MINER_TIP)).toGWei(activeSafe.chain.currency.decimals)
                                // base fee amount + miner tip
                                maxFeePerGas = Wei(baseFee).toGWei(activeSafe.chain.currency.decimals)
                                    .plus(maxPriorityFeePerGas!!)
                            }
                        }

                        updateState {
                            TxReviewState(viewAction = UpdateFee(fee = totalFee()))
                        }

                        if (totalFeeValue() ?: BigInteger.ZERO > estimationParams.balance) {
                            throw InsufficientExecutionBalance
                        } else if (!estimationParams.callResult) {
                            throw TxFails
                        }

                    }.onFailure {
                        throw TxEstimationFailed(it.cause ?: it)
                    }
                }
            }
        }
    }

    fun updateEstimationParams(
        nonce: BigInteger,
        gasLimit: BigInteger,
        maxPriorityFeePerGas: BigDecimal,
        maxFeePerGas: BigDecimal
    ) {
        if (nonce != this.nonce ||
            gasLimit != this.gasLimit ||
            maxPriorityFeePerGas != this.maxPriorityFeePerGas ||
            maxFeePerGas != this.maxFeePerGas
        ) {
            val changedFieldTrackingIds = mutableListOf<TxExecField>()
            if (nonce != this.nonce) {
                changedFieldTrackingIds.add(TxExecField.NONCE)
            }
            if (gasLimit != this.gasLimit) {
                changedFieldTrackingIds.add(TxExecField.GAS_LIMIT)
            }
            if (maxPriorityFeePerGas != this.maxPriorityFeePerGas) {
                changedFieldTrackingIds.add(TxExecField.MAX_PRIORITY_FEE)
            }
            if (maxFeePerGas != this.maxFeePerGas) {
                changedFieldTrackingIds.add(TxExecField.MAX_FEE)
            }
            tracker.logTxExecFieldsEdit(changedFieldTrackingIds)
        }
        this.userEditedFeeData = true
        this.nonce = nonce
        this.gasLimit = gasLimit
        this.maxPriorityFeePerGas = maxPriorityFeePerGas
        this.maxFeePerGas = maxFeePerGas
        safeLaunch {
            updateState {
                TxReviewState(viewAction = UpdateFee(fee = totalFee()))
            }
        }
    }

    fun updateLegacyEstimationParams(
        nonce: BigInteger,
        gasLimit: BigInteger,
        gasPrice: BigDecimal
    ) {
        if (nonce != this.nonce ||
            gasLimit != this.gasLimit ||
            gasPrice != this.gasPrice
        ) {
            val changedFieldTrackingIds = mutableListOf<TxExecField>()
            if (nonce != this.nonce) {
                changedFieldTrackingIds.add(TxExecField.NONCE)
            }
            if (gasLimit != this.gasLimit) {
                changedFieldTrackingIds.add(TxExecField.GAS_LIMIT)
            }
            if (gasPrice != this.gasPrice) {
                changedFieldTrackingIds.add(TxExecField.GAS_PRICE)
            }
            tracker.logTxExecFieldsEdit(changedFieldTrackingIds)
        }
        this.userEditedFeeData = true
        this.nonce = nonce
        this.gasLimit = gasLimit
        this.gasPrice = gasPrice
        safeLaunch {
            updateState {
                TxReviewState(viewAction = UpdateFee(fee = totalFee()))
            }
        }
    }

    private fun updateEthTxWithEstimationData() {
        when (ethTx) {
            is Transaction.Eip1559 -> {
                val ethTxEip1559 = ethTx as Transaction.Eip1559
                ethTxEip1559.gas = gasLimit!!
                ethTxEip1559.maxPriorityFee = Wei.fromGWei(maxPriorityFeePerGas!!).value
                ethTxEip1559.maxFeePerGas = Wei.fromGWei(maxFeePerGas!!).value
                ethTx = ethTxEip1559.copy(nonce = nonce!!)
            }
            is Transaction.Legacy -> {
                val ethTxLegacy = ethTx as Transaction.Legacy
                ethTxLegacy.gas = gasLimit!!
                ethTxLegacy.gasPrice = Wei.fromGWei(this@TxReviewViewModel.gasPrice!!).value
                ethTx = ethTxLegacy.copy(nonce = nonce!!)
            }
            null -> {
                // Handle null case - this shouldn't happen in normal flow
                throw IllegalStateException("ethTx is null")
            }
        }
    }

    private fun getEthTxHash(ownerType: Owner.Type): ByteArray {
        return when (ethTx) {
            is Transaction.Eip1559 -> {
                val ethTxEip1559 = ethTx as Transaction.Eip1559
                if (ownerType == Owner.Type.KEYSTONE) {
                    byteArrayOf(ethTxEip1559.type, *ethTxEip1559.rlp())
                } else {
                    ethTxEip1559.hash()
                }
            }
            is Transaction.Legacy -> {
                val ethTxLegacy = ethTx as Transaction.Legacy
                if (ownerType == Owner.Type.KEYSTONE) {
                    ethTxLegacy.rlp()
                } else {
                    ethTxLegacy.hash()
                }
            }
            else -> throw IllegalStateException("Unknown transaction type")
        }
    }

    fun signAndExecute() {
        safeLaunch {
            executionKey?.let {
                updateEthTxWithEstimationData()
                val owner = credentialsRepository.owner(it.address)!!
                val ethTxHash = getEthTxHash(owner.type)
                when (owner.type) {
                    Owner.Type.IMPORTED, Owner.Type.GENERATED -> {
                        ethTxSignature = credentialsRepository.signWithOwner(owner, ethTxHash)
                        if (settingsHandler.usePasscode && settingsHandler.requirePasscodeForConfirmations) {
                            updateState {
                                TxReviewState(
                                    viewAction = ViewAction.NavigateTo(
                                        TxReviewFragmentDirections.actionTxReviewFragmentToEnterPasscodeFragment()
                                    )
                                )
                            }
                            updateState {
                                TxReviewState(
                                    viewAction = ViewAction.None
                                )
                            }

                        } else {
                            sendForExecution()
                        }
                    }

                    Owner.Type.LEDGER_NANO_X -> {

                    }

                    Owner.Type.KEYSTONE -> {
                        updateState {
                            TxReviewState(
                                viewAction = ViewAction.NavigateTo(
                                    TxReviewFragmentDirections.actionTxReviewFragmentToKeystoneRequestSignatureFragment(
                                        SigningMode.EXECUTION,
                                        activeSafe.chain,
                                        it.address.asEthereumAddressString(),
                                        ethTxHash.toHexString(),
                                        isLegacy()
                                    )
                                )
                            )
                        }
                        updateState {
                            TxReviewState(
                                viewAction = ViewAction.None
                            )
                        }
                    }
                    Owner.Type.TANGEM -> {
                        // ═══ TANGEM EXECUTION EXPERIMENT ═══
                        Timber.i("TxReviewViewModel: ═══ TANGEM EXECUTION EXPERIMENT ═══")
                        Timber.i("TxReviewViewModel: 🧪 TESTING: Can SignRaw handle Ethereum transaction hashes?")
                        Timber.i("TxReviewViewModel: 📋 CONTEXT: SignRaw worked for safeTxHash, trying with ethTxHash")
                        Timber.i("TxReviewViewModel: 🔧 APPROACH: Use same SignRaw method for ethTxHash")
                        
                        // Log execution context
                        Timber.i("TxReviewViewModel: ═══ EXECUTION CONTEXT ═══")
                        Timber.i("TxReviewViewModel: Owner address: ${it.address.asEthereumAddressString()}")
                        Timber.i("TxReviewViewModel: Owner type: TANGEM")
                        Timber.i("TxReviewViewModel: Ethereum transaction hash: ${ethTxHash.toHexString()}")
                        Timber.i("TxReviewViewModel: Hash length: ${ethTxHash.size} bytes")
                        
                        // Log Safe transaction details
                        val executionInfo = executionInfo as DetailedExecutionInfo.MultisigExecutionDetails
                        Timber.i("TxReviewViewModel: ═══ SAFE TRANSACTION DETAILS ═══")
                        Timber.i("TxReviewViewModel: Safe address: ${activeSafe.address.asEthereumAddressString()}")
                        Timber.i("TxReviewViewModel: Safe tx hash: ${executionInfo.safeTxHash}")
                        Timber.i("TxReviewViewModel: Nonce: ${executionInfo.nonce}")
                        Timber.i("TxReviewViewModel: Confirmations required: ${executionInfo.confirmationsRequired}")
                        Timber.i("TxReviewViewModel: Confirmations submitted: ${executionInfo.confirmations.size}")
                        
                        // Log existing confirmations
                        Timber.i("TxReviewViewModel: ═══ EXISTING CONFIRMATIONS ═══")
                        executionInfo.confirmations.forEachIndexed { index, confirmation ->
                            Timber.i("TxReviewViewModel: Confirmation $index: signer=${confirmation.signer.value}, signature=${confirmation.signature}")
                        }
                        
                        // Log execution transaction details
                        Timber.i("TxReviewViewModel: ═══ ETHEREUM TRANSACTION DETAILS ═══")
                        ethTx?.let { tx ->
                            when (tx) {
                                is Transaction.Eip1559 -> {
                                    Timber.i("TxReviewViewModel: Type: EIP1559")
                                    Timber.i("TxReviewViewModel: Chain ID: ${tx.chainId}")
                                    Timber.i("TxReviewViewModel: From: ${tx.from?.asEthereumAddressString()}")
                                    Timber.i("TxReviewViewModel: To (Safe): ${tx.to?.asEthereumAddressString()}")
                                    Timber.i("TxReviewViewModel: Gas: ${tx.gas}")
                                    Timber.i("TxReviewViewModel: Max fee: ${tx.maxFeePerGas}")
                                    Timber.i("TxReviewViewModel: Data available: ${tx.data != null}")
                                }
                                is Transaction.Legacy -> {
                                    Timber.i("TxReviewViewModel: Type: Legacy")
                                    Timber.i("TxReviewViewModel: Chain ID: ${tx.chainId}")
                                    Timber.i("TxReviewViewModel: From: ${tx.from?.asEthereumAddressString()}")
                                    Timber.i("TxReviewViewModel: To (Safe): ${tx.to?.asEthereumAddressString()}")
                                    Timber.i("TxReviewViewModel: Gas: ${tx.gas}")
                                    Timber.i("TxReviewViewModel: Gas price: ${tx.gasPrice}")
                                    Timber.i("TxReviewViewModel: Data available: ${tx.data != null}")
                                }
                            }
                        }
                        
                        // Log SignRaw expectations
                        Timber.i("TxReviewViewModel: ═══ SIGNRAW EXECUTION EXPERIMENT ═══")
                        Timber.i("TxReviewViewModel: 🎯 HYPOTHESIS: SignRaw can handle ethTxHash like safeTxHash")
                        Timber.i("TxReviewViewModel: 🎯 EXPECTATION: Same derivation path as successful confirmation")
                        Timber.i("TxReviewViewModel: 🎯 EXPECTATION: Should produce valid ECDSA signature for Ethereum transaction")
                        Timber.i("TxReviewViewModel: 🔧 SDK STATUS: Modified to use SigningMethod.Code.SignRaw")
                        
                        // Log comparison with successful confirmation flow
                        Timber.i("TxReviewViewModel: ═══ HASH TYPE COMPARISON ═══")
                        Timber.i("TxReviewViewModel: ✅ CONFIRMATION: safeTxHash = ${executionInfo.safeTxHash}")
                        Timber.i("TxReviewViewModel: 🧪 EXECUTION: ethTxHash = ${ethTxHash.toHexString()}")
                        Timber.i("TxReviewViewModel: 📋 DIFFERENCE: Different hash algorithms, same SignRaw method")
                        
                        Timber.i("TxReviewViewModel: 🚀 ATTEMPTING TANGEM EXECUTION WITH SIGNRAW")
                        
                        updateState {
                            TxReviewState(
                                viewAction = ViewAction.NavigateTo(
                                    TxReviewFragmentDirections.actionTxReviewFragmentToTangemSignDialog(
                                        it.address.asEthereumAddressString(),
                                        ethTxHash.toHexString(),
                                        true
                                    )
                                )
                            )
                        }
                        updateState {
                            TxReviewState(
                                viewAction = ViewAction.None
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setSignature(signatueString: String) {
        ethTxSignature = signatueString.toSignature()
        if (ethTxSignature!!.v <= 1) {
            ethTxSignature!!.v = (ethTxSignature!!.v + 27).toByte()

        }
    }

    fun resumeExecutionFlow(signatureString: String? = null) {
        safeLaunch {
            Timber.i("TxReviewViewModel: ═══ RESUME EXECUTION FLOW ═══")
            Timber.i("TxReviewViewModel: 🔄 RETURNING FROM TANGEM SIGNING")
            
            signatureString?.let { signature ->
                Timber.i("TxReviewViewModel: ✅ SIGNATURE RECEIVED FROM TANGEM")
                Timber.i("TxReviewViewModel: Signature: $signature")
                Timber.i("TxReviewViewModel: Signature length: ${signature.length} chars")
                Timber.i("TxReviewViewModel: Expected format: 0x[r(64)][s(64)][v(2)] = 132 chars")
                
                if (signature.length == 132) {
                    Timber.i("TxReviewViewModel: ✅ SIGNATURE FORMAT: Correct length")
                    val r = signature.substring(2, 66)
                    val s = signature.substring(66, 130)
                    val v = signature.substring(130, 132)
                    Timber.i("TxReviewViewModel: r: $r")
                    Timber.i("TxReviewViewModel: s: $s")
                    Timber.i("TxReviewViewModel: v: $v")
                } else {
                    Timber.w("TxReviewViewModel: ⚠️ SIGNATURE FORMAT: Unexpected length")
                }
                
                Timber.i("TxReviewViewModel: 🔧 SETTING SIGNATURE FOR EXECUTION")
                setSignature(signature)
            } ?: run {
                Timber.w("TxReviewViewModel: ⚠️ NO SIGNATURE PROVIDED")
                Timber.w("TxReviewViewModel: This might be a passcode unlock or other flow")
            }
            
            Timber.i("TxReviewViewModel: 🚀 PROCEEDING TO EXECUTION")
            sendForExecution()
        }
    }

    fun sendForExecution() {
        safeLaunch {
            Timber.i("TxReviewViewModel: ═══ SEND FOR EXECUTION ═══")
            Timber.i("TxReviewViewModel: 🎯 FINAL STEP: Execute transaction on blockchain")
            
            ethTxSignature?.let { signature ->
                Timber.i("TxReviewViewModel: ✅ SIGNATURE AVAILABLE FOR EXECUTION")
                Timber.i("TxReviewViewModel: Signature: r=${signature.r}, s=${signature.s}, v=${signature.v}")
                
                ethTx?.let { tx ->
                    Timber.i("TxReviewViewModel: ═══ FINAL ETHEREUM TRANSACTION ═══")
                    when (tx) {
                        is Transaction.Eip1559 -> {
                            Timber.i("TxReviewViewModel: Type: EIP1559")
                            Timber.i("TxReviewViewModel: Chain ID: ${tx.chainId}")
                            Timber.i("TxReviewViewModel: From: ${tx.from?.asEthereumAddressString()}")
                            Timber.i("TxReviewViewModel: To (Safe): ${tx.to?.asEthereumAddressString()}")
                            Timber.i("TxReviewViewModel: Gas: ${tx.gas}")
                            Timber.i("TxReviewViewModel: Max fee: ${tx.maxFeePerGas}")
                                    Timber.i("TxReviewViewModel: Data available: ${tx.data != null}")
                        }
                        is Transaction.Legacy -> {
                            Timber.i("TxReviewViewModel: Type: Legacy")
                            Timber.i("TxReviewViewModel: Chain ID: ${tx.chainId}")
                            Timber.i("TxReviewViewModel: From: ${tx.from?.asEthereumAddressString()}")
                            Timber.i("TxReviewViewModel: To (Safe): ${tx.to?.asEthereumAddressString()}")
                            Timber.i("TxReviewViewModel: Gas: ${tx.gas}")
                            Timber.i("TxReviewViewModel: Gas price: ${tx.gasPrice}")
                                    Timber.i("TxReviewViewModel: Data available: ${tx.data != null}")
                        }
                    }
                }
                
                Timber.i("TxReviewViewModel: 🚀 SENDING TRANSACTION TO BLOCKCHAIN")
                
                kotlin.runCatching {
                    rpcClient.send(ethTx!!, signature)
                }.onSuccess { txHash ->
                    Timber.i("TxReviewViewModel: ═══ EXECUTION SUCCESS ═══")
                    Timber.i("TxReviewViewModel: ✅ TRANSACTION SUBMITTED TO BLOCKCHAIN")
                    Timber.i("TxReviewViewModel: Transaction hash: $txHash")
                    Timber.i("TxReviewViewModel: 🎉 TANGEM EXECUTION FLOW COMPLETE!")
                    
                    tracker.logTxExecSubmitted()
                    val executionInfo = executionInfo as DetailedExecutionInfo.MultisigExecutionDetails
                    
                    Timber.i("TxReviewViewModel: 💾 SAVING TRANSACTION LOCALLY")
                    Timber.i("TxReviewViewModel: Safe tx hash: ${executionInfo.safeTxHash}")
                    Timber.i("TxReviewViewModel: Safe nonce: ${executionInfo.nonce}")
                    
                    localTxRepository.saveLocally(
                        tx = ethTx!!,
                        txHash = txHash,
                        safeTxHash = executionInfo.safeTxHash,
                        safeTxNonce = executionInfo.nonce,
                        submittedAt = executionInfo.submittedAt.time
                    )
                    
                    Timber.i("TxReviewViewModel: 🎊 NAVIGATING TO SUCCESS SCREEN")
                    updateState {
                        TxReviewState(
                            viewAction =
                            ViewAction.NavigateTo(
                                TxReviewFragmentDirections.actionTxReviewFragmentToTxSuccessFragment()
                            )
                        )
                    }
                }.onFailure { error ->
                    Timber.e("TxReviewViewModel: ═══ EXECUTION FAILURE ═══")
                    Timber.e("TxReviewViewModel: ❌ TRANSACTION EXECUTION FAILED")
                    Timber.e("TxReviewViewModel: Error: ${error.message}")
                    Timber.e("TxReviewViewModel: Error type: ${error.javaClass.simpleName}")
                    error.printStackTrace()
                    
                    Timber.e("TxReviewViewModel: 🔍 DEBUGGING INFORMATION")
                    Timber.e("TxReviewViewModel: - Check if RPC endpoint is accessible")
                    Timber.e("TxReviewViewModel: - Check if signature is valid for ethTxHash")
                    Timber.e("TxReviewViewModel: - Check if gas estimation is sufficient")
                    Timber.e("TxReviewViewModel: - Check if account has sufficient balance")
                    
                    throw TxSumbitFailed(error.cause ?: error)
                }
            } ?: run {
                Timber.e("TxReviewViewModel: ❌ NO SIGNATURE AVAILABLE")
                Timber.e("TxReviewViewModel: Cannot execute transaction without signature")
                throw IllegalStateException("No signature available for execution")
            }
        }
    }

    private fun balanceString(balance: BigInteger): String {
        return "${
            balanceFormatter.shortAmount(
                balance.convertAmount(
                    activeSafe.chain.currency.decimals
                )
            )
        } ${activeSafe.chain.currency.symbol}"
    }

    private fun totalFeeValue(): BigInteger? {
        return if (isLegacy()) {
            if (gasLimit == null || gasPrice == null) {
                null
            } else {
                gasLimit!! * Wei.fromGWei(gasPrice!!).value
            }
        } else {
            if (gasLimit == null || maxFeePerGas == null) {
                null
            } else {
                gasLimit!! * Wei.fromGWei(maxFeePerGas!!).value
            }
        }
    }
    fun totalFee(): String? {
        return totalFeeValue()?.let { balanceString(it) }
    }

    fun isChainPrefixPrependEnabled() = settingsHandler.chainPrefixPrepend

    fun isChainPrefixCopyEnabled() = settingsHandler.chainPrefixCopy

    companion object {
        const val DEFAULT_MINER_TIP = 1_500_000_000L
    }
}

data class TxReviewState(
    override var viewAction: BaseStateViewModel.ViewAction?
) : BaseStateViewModel.State

data class DefaultKey(
    val key: OwnerViewData?
) : BaseStateViewModel.ViewAction

data class UpdateFee(
    val fee: String?
) : BaseStateViewModel.ViewAction

class TxEstimationFailed(override val cause: Throwable) : Throwable(cause)

class TxSumbitFailed(override val cause: Throwable) : Throwable(cause)

object TxFails : Throwable()

object InsufficientExecutionBalance : Throwable()

object LoadBalancesFailed : Throwable()
