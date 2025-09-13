package io.gnosis.safe.ui.settings.owner.tangem

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import io.gnosis.safe.R
import io.gnosis.safe.ScreenId
import io.gnosis.safe.databinding.FragmentTangemSignBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.BaseStateViewModel
import io.gnosis.safe.ui.base.fragment.BaseViewBindingFragment
import io.gnosis.safe.ui.base.SafeOverviewBaseFragment
import io.gnosis.safe.ui.transactions.details.SigningMode
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import pm.gnosis.utils.asEthereumAddress
import timber.log.Timber
import javax.inject.Inject

class TangemSignFragment : BaseViewBindingFragment<FragmentTangemSignBinding>() {

    companion object {
        private const val TAG = "TangemSignFragment"
    }

    @Inject
    lateinit var viewModel: TangemSignViewModel

    @Inject
    lateinit var tangemController: TangemController

    private val args: TangemSignFragmentArgs by navArgs()

    override fun screenId() = ScreenId.OWNER_TANGEM_SIGN

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun viewModelProvider() = this

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentTangemSignBinding =
        FragmentTangemSignBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set up activity context for TangemController
        (requireActivity() as? ComponentActivity)?.let { componentActivity ->
            tangemController.activity = componentActivity
            Timber.d("$TAG: TangemController activity context set")
        } ?: run {
            Timber.e("$TAG: Activity is not ComponentActivity - Tangem signing may not work")
        }

        with(binding) {
            backButton.setOnClickListener {
                findNavController().navigateUp()
            }

            // Set up UI based on signing mode
            when (args.signingMode) {
                SigningMode.CONFIRMATION -> {
                    title.text = getString(io.gnosis.safe.R.string.tangem_sign_confirm_title)
                    subtitle.text = getString(io.gnosis.safe.R.string.tangem_sign_confirm_subtitle)
                }
                SigningMode.REJECTION -> {
                    title.text = getString(io.gnosis.safe.R.string.tangem_sign_reject_title)
                    subtitle.text = getString(io.gnosis.safe.R.string.tangem_sign_reject_subtitle)
                }
                else -> {
                    title.text = getString(io.gnosis.safe.R.string.tangem_sign_title)
                    subtitle.text = getString(io.gnosis.safe.R.string.tangem_sign_subtitle)
                }
            }

            // Show transaction hash preview
            args.safeTxHash?.let { hash ->
                val previewHash = viewModel.getPreviewHash(hash)
                transactionHash.text = previewHash
                transactionHashContainer.visibility = View.VISIBLE
            } ?: run {
                transactionHashContainer.visibility = View.GONE
            }

            // Set initial instruction
            scanCardInstruction.text = getString(io.gnosis.safe.R.string.tangem_sign_tap_instruction)
        }

        // Start signing process
        startSigning()
    }

    override fun onResume() {
        super.onResume()
        viewModel.state.observe(this) { state ->
            updateState(state)
        }
    }

    private fun startSigning() {
        Timber.i("$TAG: Starting Tangem signing process")
        
        try {
            val ownerAddress = args.owner.asEthereumAddress()
            val safeTxHash = args.safeTxHash
            
            if (ownerAddress == null) {
                Timber.e("$TAG: Invalid owner address: ${args.owner}")
                showError("Invalid owner address")
                return
            }
            
            if (safeTxHash == null) {
                Timber.e("$TAG: Missing transaction hash")
                showError("Missing transaction hash")
                return
            }
            
            Timber.d("$TAG: Starting signature generation for owner: ${args.owner}, hash: $safeTxHash")
            
            with(binding) {
                scanCardInstruction.text = getString(io.gnosis.safe.R.string.tangem_sign_scan_first)
                progressIndicator.visibility = View.VISIBLE
            }
            
            viewModel.prepareSigningData(ownerAddress, safeTxHash)
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Exception starting signing process")
            showError("Failed to start signing: ${e.message}")
        }
    }

    private fun updateState(state: TangemSignState) {
        when (val action = state.viewAction) {
            is BaseStateViewModel.ViewAction.Loading -> {
                Timber.d("$TAG: Loading state: ${action.isLoading}")
                with(binding) {
                    progressIndicator.visibility = if (action.isLoading) View.VISIBLE else View.GONE
                    if (action.isLoading) {
                        scanCardInstruction.text = getString(io.gnosis.safe.R.string.tangem_sign_processing)
                    }
                }
            }
            is NeedCardScan -> {
                Timber.d("$TAG: Need to scan card first")
                with(binding) {
                    progressIndicator.visibility = View.VISIBLE
                    scanCardInstruction.text = getString(io.gnosis.safe.R.string.tangem_sign_scan_first)
                }
                // Start card scanning
                performCardScan(action.cardId, action.hashBytes, action.derivationPath)
            }
            is ReadyToSign -> {
                Timber.d("$TAG: Ready to sign with cached wallet info")
                with(binding) {
                    progressIndicator.visibility = View.VISIBLE
                    scanCardInstruction.text = getString(io.gnosis.safe.R.string.tangem_sign_processing)
                }
                // Perform signing
                performSigning(action.cardId, action.hashBytes, action.walletPublicKey, action.derivationPath)
            }
            is DirectSign -> {
                Timber.d("$TAG: Starting direct signing without pre-scan")
                with(binding) {
                    progressIndicator.visibility = View.VISIBLE
                    scanCardInstruction.text = getString(io.gnosis.safe.R.string.tangem_sign_subtitle)
                }
                // Perform direct signing without pre-scanning for wallet info
                performDirectSigning(action.cardId, action.hashBytes, action.derivationPath)
            }
            is Signature -> {
                Timber.i("$TAG: ✅ Signature received: ${action.signature}")
                
                with(binding) {
                    progressIndicator.visibility = View.GONE
                    scanCardInstruction.text = getString(io.gnosis.safe.R.string.tangem_sign_success)
                    
                    // Show success state briefly before navigating back
                    view?.postDelayed({
                        try {
                            // Navigate back with result using the same pattern as Ledger and Keystone
                            navigateBackWithSignature(action.signature)
                        } catch (e: Exception) {
                            Timber.e(e, "$TAG: Error navigating back with signature")
                        }
                    }, 1500)
                }
            }
            is BaseStateViewModel.ViewAction.ShowError -> {
                Timber.e("$TAG: Error state: ${action.error.message}")
                showError(action.error.message ?: "Unknown error")
            }
            else -> {
                // Handle other states if needed
                Timber.d("$TAG: Unhandled state: $action")
            }
        }
    }
    
    private fun performCardScan(cardId: String, hashBytes: ByteArray, derivationPath: String) {
        Timber.i("$TAG: Starting card scan for cardId: $cardId")
        
        lifecycleScope.launch {
            try {
                tangemController.scanCard().collect { scanResult ->
                    when (scanResult) {
                        is TangemResult.Success -> {
                            viewModel.handleCardScanResult(scanResult.data, cardId, hashBytes, derivationPath)
                        }
                        is TangemResult.Error -> {
                            viewModel.handleSigningError("Card scan failed: ${scanResult.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Exception during card scan")
                viewModel.handleSigningError("Card scan failed: ${e.message}")
            }
        }
    }
    
    private fun navigateBackWithSignature(signedSafeTxHash: String) {
        Timber.d("$TAG: Navigating back with signature: $signedSafeTxHash")
        
        // Pop back to the signing owner selection fragment
        findNavController().popBackStack(R.id.signingOwnerSelectionFragment, true)
        
        // Set the result data using the same pattern as Ledger and Keystone
        findNavController().currentBackStackEntry?.savedStateHandle?.set(
            SafeOverviewBaseFragment.OWNER_SELECTED_RESULT,
            args.owner
        )
        findNavController().currentBackStackEntry?.savedStateHandle?.set(
            SafeOverviewBaseFragment.OWNER_SIGNED_RESULT,
            signedSafeTxHash
        )
        
        Timber.i("$TAG: Successfully set navigation results - owner: ${args.owner}, signature: $signedSafeTxHash")
    }
    
    private fun performDirectSigning(cardId: String, hashBytes: ByteArray, derivationPath: String?) {
        Timber.i("$TAG: Starting direct signing without wallet public key")
        
        lifecycleScope.launch {
            try {
                val result = tangemController.signHashDirect(
                    cardId = cardId,
                    hash = hashBytes,
                    derivationPath = derivationPath
                )
                
                when (result) {
                    is TangemResult.Success -> {
                        viewModel.handleSigningResult(result.data.signature)
                    }
                    is TangemResult.Error -> {
                        viewModel.handleSigningError("Direct signing failed: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Exception during direct signing")
                viewModel.handleSigningError("Direct signing exception: ${e.message}")
            }
        }
    }
    
    private fun performSigning(cardId: String, hashBytes: ByteArray, walletPublicKey: ByteArray, derivationPath: String) {
        Timber.i("$TAG: Starting signing operation")
        
        lifecycleScope.launch {
            try {
                val result = tangemController.signHash(
                    cardId = cardId,
                    hash = hashBytes,
                    walletPublicKey = walletPublicKey,
                    derivationPath = derivationPath
                )
                
                when (result) {
                    is TangemResult.Success -> {
                        viewModel.handleSigningResult(result.data.signature)
                    }
                    is TangemResult.Error -> {
                        viewModel.handleSigningError(result.message)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Exception during signing")
                viewModel.handleSigningError("Signing failed: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        with(binding) {
            progressIndicator.visibility = View.GONE
            scanCardInstruction.text = message
            
            // Show error state and allow retry
            view?.postDelayed({
                scanCardInstruction.text = getString(io.gnosis.safe.R.string.tangem_sign_tap_instruction)
            }, 3000)
        }
    }

    override fun onPause() {
        super.onPause()
        // Note: Card disconnection is handled by TangemController lifecycle
    }
}
