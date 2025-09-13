package io.gnosis.safe.ui.settings.owner.list

import io.gnosis.data.backend.rpc.RpcClient
import io.gnosis.data.models.Chain
import io.gnosis.data.models.Owner
import io.gnosis.data.repositories.CredentialsRepository
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.safe.ui.base.AppDispatchers
import io.gnosis.safe.ui.base.BaseStateViewModel
import io.gnosis.safe.ui.settings.app.SettingsHandler
import io.gnosis.safe.ui.settings.owner.ledger.LedgerDeviceListFragment
import io.gnosis.safe.ui.transactions.details.ConfirmConfirmation
import io.gnosis.safe.ui.transactions.details.ConfirmRejection
import io.gnosis.safe.ui.transactions.details.SigningMode
import io.gnosis.safe.ui.transactions.details.SigningOwnerSelectionFragmentDirections
import io.gnosis.safe.utils.BalanceFormatter
import io.gnosis.safe.utils.convertAmount
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddressString
import java.math.BigInteger
import javax.inject.Inject

class OwnerListViewModel
@Inject constructor(
    private val safeRepository: SafeRepository,
    private val credentialsRepository: CredentialsRepository,
    private val settingsHandler: SettingsHandler,
    private val rpcClient: RpcClient,
    private val balanceFormatter: BalanceFormatter,
    appDispatchers: AppDispatchers
) : BaseStateViewModel<OwnerListState>(appDispatchers) {

    override fun initialState() = OwnerListState(ViewAction.Loading(true))

    fun loadOwners(missingSigners: List<String>? = null) {
        safeLaunch {
            updateState {
                OwnerListState(viewAction = ViewAction.Loading(true))
            }
            val owners = credentialsRepository.owners()
                .map {
                    OwnerViewData(it.address, it.name, it.type)
                }
                .sortedBy { it.name }
            missingSigners?.let {
                val acceptedOwners = owners.filter { localOwner ->
                    missingSigners.any {
                        localOwner.address.asEthereumAddressString() == it
                    }
                }
                updateState {
                    OwnerListState(viewAction = LocalOwners(acceptedOwners))
                }
            } ?: updateState {
                OwnerListState(viewAction = LocalOwners(owners))
            }
        }
    }

    fun loadExecutingOwners() {
        safeLaunch {
            val activeSafe = safeRepository.getActiveSafe()
            activeSafe?.let { safe ->
                updateState {
                    OwnerListState(viewAction = ViewAction.Loading(true))
                }
                val owners =
                    credentialsRepository.owners()
                        .map { OwnerViewData(it.address, it.name, it.type) }
                        .sortedBy { it.name }
                // FIXED: Match web frontend behavior and TxReviewViewModel logic
                // Any local key can execute (not just Safe signers)
                // CRITICAL FIX: Exclude Tangem from execution - only local keys can execute
                val acceptedOwners = owners.filter { localOwner ->
                    //TODO: Modify this check when we have tx execution on Ledger Nano X
                    localOwner.type != Owner.Type.LEDGER_NANO_X && localOwner.type != Owner.Type.TANGEM
                }
                
                android.util.Log.i("OwnerListViewModel", "═══ EXECUTION KEY SELECTION SCREEN ═══")
                android.util.Log.i("OwnerListViewModel", "Total local owners: ${owners.size}")
                android.util.Log.i("OwnerListViewModel", "Accepted for execution: ${acceptedOwners.size}")
                owners.forEach { owner ->
                    val isAccepted = acceptedOwners.contains(owner)
                    android.util.Log.i("OwnerListViewModel", "Owner: ${owner.address.asEthereumAddressString()} (${owner.type}) - Accepted: $isAccepted")
                }
                android.util.Log.i("OwnerListViewModel", "Safe signers:")
                safe.signingOwners.forEach { signer ->
                    android.util.Log.i("OwnerListViewModel", "Safe signer: ${signer.asEthereumAddressString()}")
                }
                kotlin.runCatching {
                    rpcClient.getBalances(acceptedOwners.map { it.address })
                }.onSuccess { balances ->
                    android.util.Log.i("OwnerListViewModel", "✅ BALANCE LOADING SUCCESS")
                    updateState {
                        OwnerListState(
                            viewAction =
                            LocalOwners(
                                acceptedOwners.mapIndexed { index, ownerViewData ->
                                    ownerViewData.copy(
                                        balance = "${
                                            balanceFormatter.shortAmount(
                                                balances[index]!!.value.convertAmount(
                                                    safe.chain.currency.decimals
                                                )
                                            )
                                        } ${safe.chain.currency.symbol}",
                                        zeroBalance = balances[index]!!.value == BigInteger.ZERO
                                    )
                                }
                            )
                        )
                    }
                }.onFailure { error ->
                    android.util.Log.e("OwnerListViewModel", "═══ BALANCE LOADING FAILED ═══")
                    android.util.Log.e("OwnerListViewModel", "Error: ${error.message}")
                    android.util.Log.e("OwnerListViewModel", "Accepted owners count: ${acceptedOwners.size}")
                    
                    if (acceptedOwners.isEmpty()) {
                        android.util.Log.e("OwnerListViewModel", "❌ NO ACCEPTED OWNERS")
                        updateState {
                            OwnerListState(viewAction = BaseStateViewModel.ViewAction.ShowError(Exception("No execution keys available")))
                        }
                    } else {
                        android.util.Log.w("OwnerListViewModel", "⚠️ Using owners without balance info")
                        // Fallback: show owners without balance info
                        updateState {
                            OwnerListState(
                                viewAction =
                                LocalOwners(
                                    acceptedOwners.map { ownerViewData ->
                                        ownerViewData.copy(
                                            balance = "Unknown",
                                            zeroBalance = false
                                        )
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun selectKeyForSigning(
        owner: Solidity.Address,
        type: Owner.Type,
        signingMode: SigningMode,
        chain: Chain,
        safeTxHash: String? = null
    ) {
        val isConfirmation =
            signingMode == SigningMode.CONFIRMATION || signingMode == SigningMode.INITIATE_TRANSFER
        safeLaunch {
            when (type) {
                Owner.Type.LEDGER_NANO_X -> {
                    updateState {
                        OwnerListState(
                            ViewAction.NavigateTo(
                                SigningOwnerSelectionFragmentDirections.actionSigningOwnerSelectionFragmentToLedgerDeviceListFragmet(
                                    if (isConfirmation) LedgerDeviceListFragment.Mode.CONFIRMATION.name else LedgerDeviceListFragment.Mode.REJECTION.name,
                                    owner.asEthereumAddressString(),
                                    safeTxHash
                                )
                            )
                        )
                    }
                }

                Owner.Type.KEYSTONE -> {
                    updateState {
                        OwnerListState(
                            ViewAction.NavigateTo(
                                SigningOwnerSelectionFragmentDirections.actionSigningOwnerSelectionFragmentToKeystoneRequestSignatureFragment(
                                    owner = owner.asEthereumAddressString(),
                                    signingMode = signingMode,
                                    chain = chain,
                                    safeTxHash = safeTxHash
                                )
                            )
                        )
                    }
                    updateState { OwnerListState(ViewAction.None) }
                }

                Owner.Type.TANGEM -> {
                    // CRITICAL FIX: Tangem can only be used for CONFIRMATION, not EXECUTION
                    if (signingMode == SigningMode.EXECUTION) {
                        // Tangem cannot execute transactions - only local keys can
                        // This should never happen if filtering is correct, but add safety check
                        throw IllegalStateException("Tangem cannot be used for execution - only for confirmation")
                    }
                    
                    updateState {
                        OwnerListState(
                            ViewAction.NavigateTo(
                                SigningOwnerSelectionFragmentDirections.actionSigningOwnerSelectionFragmentToTangemSignFragment(
                                    owner = owner.asEthereumAddressString(),
                                    signingMode = signingMode,
                                    chain = chain,
                                    safeTxHash = safeTxHash
                                )
                            )
                        )
                    }
                    updateState { OwnerListState(ViewAction.None) }
                }

                else -> {
                    if (settingsHandler.usePasscode && settingsHandler.requirePasscodeForConfirmations) {
                        updateState {
                            OwnerListState(
                                ViewAction.NavigateTo(
                                    SigningOwnerSelectionFragmentDirections.actionSigningOwnerSelectionFragmentToEnterPasscodeFragment(
                                        selectedOwner = owner.asEthereumAddressString()
                                    )
                                )
                            )
                        }
                        updateState { OwnerListState(ViewAction.None) }
                    } else {
                        if (isConfirmation) {
                            updateState { OwnerListState(ConfirmConfirmation(owner)) }
                            updateState { OwnerListState(ViewAction.None) }
                        } else {
                            updateState { OwnerListState(ConfirmRejection(owner)) }
                            updateState { OwnerListState(ViewAction.None) }
                        }
                    }
                }
            }
        }
    }

    fun selectKeyForExecution(
        owner: Solidity.Address,
    ) {
        safeLaunch {
            updateState { OwnerListState(ExecutionKey(owner)) }
            updateState { OwnerListState(ViewAction.None) }
        }
    }
}

data class OwnerListState(
    override var viewAction: BaseStateViewModel.ViewAction?
) : BaseStateViewModel.State

data class LocalOwners(
    val owners: List<OwnerViewData>
) : BaseStateViewModel.ViewAction

data class ExecutionKey(
    val owner: Solidity.Address
) : BaseStateViewModel.ViewAction
