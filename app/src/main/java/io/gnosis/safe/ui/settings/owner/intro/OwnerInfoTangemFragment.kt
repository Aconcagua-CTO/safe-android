package io.gnosis.safe.ui.settings.owner.intro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import io.gnosis.safe.R
import io.gnosis.safe.ScreenId
import io.gnosis.safe.databinding.FragmentOwnerInfoTangemBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.fragment.BaseViewBindingFragment
import io.gnosis.safe.ui.settings.owner.tangem.TangemCardInfoHolder
import io.gnosis.safe.ui.settings.owner.tangem.TangemController
import io.gnosis.safe.ui.settings.owner.tangem.TangemResult
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import javax.inject.Inject

class OwnerInfoTangemFragment : BaseViewBindingFragment<FragmentOwnerInfoTangemBinding>() {

    @Inject
    lateinit var tangemController: TangemController

    override fun screenId() = ScreenId.OWNER_TANGEM_INFO

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun viewModelProvider() = this

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentOwnerInfoTangemBinding =
        FragmentOwnerInfoTangemBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set up activity context for TangemController
        (activity as? ComponentActivity)?.let { componentActivity ->
            tangemController.activity = componentActivity
            Timber.d("TangemController activity context set")
        }
        
        with(binding) {
            nextButton.setOnClickListener {
                startTangemCardScan()
            }
            backButton.setOnClickListener { findNavController().navigateUp() }
        }
    }
    
    private fun startTangemCardScan() {
        Timber.d("Starting Tangem card scan")
        
        // Update UI to show scanning state
        with(binding) {
            nextButton.isEnabled = false
            nextButton.text = getString(R.string.tangem_card_scanning)
        }
        
        lifecycleScope.launchWhenStarted {
            tangemController.scanCard()
                .catch { exception ->
                    Timber.e(exception, "Error during card scan")
                    handleScanError("Card scan failed: ${exception.message}")
                }
                .collect { result ->
                    when (result) {
                        is TangemResult.Success -> {
                            val cardInfo = result.data
                            Timber.i("Card scan successful - cardId: ${cardInfo.cardId}")
                            
                            // Store card info in a temporary static holder for immediate access
                            TangemCardInfoHolder.setCardInfo(cardInfo)
                            
                            // Navigate to owner selection with actual card ID
                            findNavController().navigate(
                                OwnerInfoTangemFragmentDirections.actionOwnerInfoTangemFragmentToTangemOwnerSelectionFragment(
                                    cardId = cardInfo.cardId
                                )
                            )
                        }
                        is TangemResult.Error -> {
                            Timber.e("Card scan failed: ${result.message}")
                            
                            // Handle specific error cases
                            if (result.message.contains("cancelled by user")) {
                                handleScanError("Card scan was cancelled. Please try again.")
                            } else {
                                handleScanError(result.message)
                            }
                        }
                    }
                }
        }
    }
    
    private fun handleScanError(errorMessage: String) {
        Timber.w("Handling scan error: $errorMessage")
        
        with(binding) {
            nextButton.isEnabled = true
            nextButton.text = getString(R.string.tangem_card_tap_instruction)
            
            // Show error message to user
            // TODO: Add error display in layout if needed
        }
    }
}
