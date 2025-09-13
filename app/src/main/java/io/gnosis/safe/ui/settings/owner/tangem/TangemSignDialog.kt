package io.gnosis.safe.ui.settings.owner.tangem

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import io.gnosis.safe.R
import io.gnosis.safe.ScreenId
import io.gnosis.safe.databinding.DialogTangemSignBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.BaseStateViewModel.ViewAction.*
import io.gnosis.safe.ui.base.SafeOverviewBaseFragment
import io.gnosis.safe.ui.base.fragment.BaseBottomSheetDialogFragment
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import timber.log.Timber
import javax.inject.Inject

class TangemSignDialog : BaseBottomSheetDialogFragment<DialogTangemSignBinding>() {

    companion object {
        private const val TAG = "TangemSignDialog"
    }

    private val navArgs by navArgs<TangemSignDialogArgs>()
    private val confirmation by lazy { navArgs.confirmation }
    private val owner by lazy { navArgs.owner.asEthereumAddress()!! }
    private val safeTxHash by lazy { navArgs.safeTxHash }

    override fun screenId() = ScreenId.TANGEM_SIGN

    @Inject
    lateinit var viewModel: TangemSignViewModel

    override fun inject(viewComponent: ViewComponent) {
        viewComponent.inject(this)
    }

    override fun viewModelProvider() = this

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        DialogTangemSignBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        Timber.i("$TAG: Dialog created - owner: ${owner.asEthereumAddressString()}, confirmation: $confirmation, hash: $safeTxHash")

        with(binding) {
            actionLabel.text = getString(if (confirmation) R.string.tangem_sign_confirm else R.string.tangem_sign_reject)
            hash.text = viewModel.getPreviewHash(safeTxHash)
            cancel.setOnClickListener {
                Timber.d("$TAG: Cancel button clicked")
                navigateBack()
            }
        }

        viewModel.state.observe(viewLifecycleOwner, Observer { state ->
            val viewAction = state.viewAction
            Timber.d("$TAG: State changed - viewAction: $viewAction")
            
            when (viewAction) {
                is Loading -> {
                    Timber.d("$TAG: Loading state - isLoading: ${viewAction.isLoading}")
                    binding.progress.visibility = if (viewAction.isLoading) View.VISIBLE else View.GONE
                }
                is Signature -> {
                    Timber.i("$TAG: ═══ TANGEM EXECUTION SIGNATURE SUCCESS ═══")
                    Timber.i("$TAG: ✅ SIGNATURE RECEIVED FROM TANGEM CARD")
                    Timber.i("$TAG: Signature: ${viewAction.signature}")
                    Timber.i("$TAG: Signature length: ${viewAction.signature.length} chars")
                    
                    if (viewAction.signature.length == 132) {
                        Timber.i("$TAG: ✅ SIGNATURE FORMAT: Correct Ethereum format")
                        val r = viewAction.signature.substring(2, 66)
                        val s = viewAction.signature.substring(66, 130)
                        val v = viewAction.signature.substring(130, 132)
                        Timber.i("$TAG: r: $r")
                        Timber.i("$TAG: s: $s") 
                        Timber.i("$TAG: v: $v")
                    } else {
                        Timber.w("$TAG: ⚠️ SIGNATURE FORMAT: Unexpected length")
                    }
                    
                    Timber.i("$TAG: 🔄 RETURNING TO EXECUTION FLOW")
                    Timber.i("$TAG: Will call TxReviewViewModel.resumeExecutionFlow() with signature")
                    navigateBack(viewAction.signature)
                }
                is NavigateTo -> {
                    Timber.d("$TAG: Navigating to: ${viewAction.navDirections}")
                    findNavController().navigate(viewAction.navDirections)
                }
                is None -> {
                    Timber.d("$TAG: No action required")
                    // Do nothing
                }
            }
        })

        // Start the signing process
        Timber.i("$TAG: Starting signature generation process")
            viewModel.prepareSigningData(owner, safeTxHash)
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("$TAG: Dialog destroyed")
        // Note: Card disconnection is handled by TangemController lifecycle
    }

    private fun navigateBack(signedSafeTxHash: String? = null) {
        Timber.d("$TAG: Navigating back - confirmation: $confirmation, signedHash: $signedSafeTxHash")
        
        if (confirmation) {
            findNavController().popBackStack(R.id.signingOwnerSelectionFragment, true)
            signedSafeTxHash?.let {
                Timber.d("$TAG: Setting result data - owner: ${owner.asEthereumAddressString()}, signedHash: $it")
                findNavController().currentBackStackEntry?.savedStateHandle?.set(
                    SafeOverviewBaseFragment.OWNER_SELECTED_RESULT,
                    owner.asEthereumAddressString()
                )
                findNavController().currentBackStackEntry?.savedStateHandle?.set(
                    SafeOverviewBaseFragment.OWNER_SIGNED_RESULT,
                    it
                )
            }
        } else {
            findNavController().popBackStack(R.id.signingOwnerSelectionFragment, true)
        }
    }
}
