package io.gnosis.safe.multichain.navigation

import android.util.Log
import androidx.navigation.NavController
import io.gnosis.safe.BuildConfig
import io.gnosis.safe.R
import io.gnosis.safe.multichain.MultichainFeatureFlag
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Navigation helper for routing between single-chain and multichain flows
 * Handles feature flag-based navigation decisions
 */
@Singleton
class MultichainNavigationHelper @Inject constructor(
    private val multichainFeatureFlag: MultichainFeatureFlag
) {
    
    /**
     * Navigate to the appropriate Safe selection dialog based on feature flag
     */
    fun navigateToSafeSelection(navController: NavController) {
        val useMultichain = multichainFeatureFlag.shouldShowMultichainFeatures()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "navigateToSafeSelection() - useMultichain: $useMultichain")
        }
        
        if (useMultichain) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "navigateToSafeSelection() - navigating to multichain safe selection dialog")
            }
            navController.navigate(R.id.multichainSafeSelectionDialog)
        } else {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "navigateToSafeSelection() - navigating to standard safe selection dialog")
            }
            navController.navigate(R.id.safeSelectionDialog)
        }
    }
    
    /**
     * Check if multichain assets view should be used
     */
    fun shouldUseMultichainAssets(): Boolean {
        return multichainFeatureFlag.shouldShowMultichainFeatures()
    }
    
    /**
     * Check if multichain Safe selection should be used
     */
    fun shouldUseMultichainSafeSelection(): Boolean {
        return multichainFeatureFlag.shouldShowMultichainFeatures()
    }
    
    /**
     * Get the appropriate Safe selection fragment/dialog based on feature flag
     */
    fun getSafeSelectionDestination(): Int {
        return if (multichainFeatureFlag.shouldShowMultichainFeatures()) {
            R.id.multichainSafeSelectionDialog
        } else {
            R.id.safeSelectionDialog
        }
    }
    
    /**
     * Check if user should see multichain-related UI elements
     */
    fun shouldShowMultichainUI(): Boolean {
        return multichainFeatureFlag.shouldShowMultichainFeatures()
    }
    
    /**
     * Navigate to appropriate assets view based on feature flag
     */
    fun navigateToAssets(navController: NavController) {
        if (multichainFeatureFlag.shouldShowMultichainFeatures()) {
            // TODO: Navigate to multichain assets when implemented
            // navController.navigate(R.id.multichainAssetsFragment)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "navigateToAssets() - multichain enabled but assets not implemented yet, using standard assets")
            }
            navController.navigate(R.id.assetsFragment)
        } else {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "navigateToAssets() - navigating to standard assets fragment")
            }
            navController.navigate(R.id.assetsFragment)
        }
    }
    
    companion object {
        private const val TAG = "MultichainNavigationHelper"
    }
}
