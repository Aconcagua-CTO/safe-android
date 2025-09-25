package io.gnosis.safe.multichain

import android.util.Log
import io.gnosis.safe.BuildConfig
import io.gnosis.safe.ui.settings.app.SettingsHandler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feature flag controller for multichain functionality
 * Handles enabling/disabling multichain features and gradual rollout logic
 */
@Singleton
class MultichainFeatureFlag @Inject constructor(
    private val settingsHandler: SettingsHandler
) {
    
    /**
     * Check if multichain mode is enabled for the current user
     */
    fun isMultichainEnabled(): Boolean {
        val isEnabled = settingsHandler.isMultichainModeEnabled
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "isMultichainEnabled() = $isEnabled")
        }
        return isEnabled
    }
    
    /**
     * Enable multichain mode for the current user
     */
    fun enableMultichainMode() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "enableMultichainMode() - enabling multichain mode")
        }
        settingsHandler.isMultichainModeEnabled = true
    }
    
    /**
     * Disable multichain mode for the current user
     */
    fun disableMultichainMode() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "disableMultichainMode() - disabling multichain mode")
        }
        settingsHandler.isMultichainModeEnabled = false
    }
    
    /**
     * Check if the user should see multichain features
     * This can be used for A/B testing or gradual rollout
     */
    fun shouldShowMultichainFeatures(): Boolean {
        val isEnabled = isMultichainEnabled()
        val isStable = isMultichainStable()
        val isEligible = isUserEligibleForMultichain()
        val shouldShow = isEnabled && isStable && isEligible
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "shouldShowMultichainFeatures() = $shouldShow")
            Log.d(TAG, "  - enabled: $isEnabled")
            Log.d(TAG, "  - stable: $isStable")
            Log.d(TAG, "  - eligible: $isEligible")
            Log.d(TAG, "  - build type: ${if (BuildConfig.DEBUG) "debug" else "release"}")
        }
        return shouldShow
    }
    
    /**
     * Check if multichain features are stable enough for production use
     * Can be controlled by remote config or build flags
     */
    private fun isMultichainStable(): Boolean {
        val isStable = true // TODO: Implement remote config check or build flag
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "isMultichainStable() = $isStable")
        }
        return isStable
    }
    
    /**
     * Check if the current user is eligible for multichain features
     * Can be used for gradual rollout or A/B testing
     */
    private fun isUserEligibleForMultichain(): Boolean {
        // For debug builds, always eligible
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "isUserEligibleForMultichain() = true (debug build)")
            return true
        }
        
        // For release builds, implement rollout logic
        val userId = settingsHandler.userDefaultFiat // Use fiat as proxy for user ID
        val isEligible = isEligibleForMultichain(userId, rolloutPercentage = 100) // 100% rollout for now
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "isUserEligibleForMultichain() = $isEligible (userId hash based)")
        }
        
        return isEligible
    }
    
    /**
     * Force enable multichain for testing/debugging
     */
    fun forceEnableForTesting() {
        settingsHandler.isMultichainModeEnabled = true
    }
    
    companion object {
        private const val TAG = "MultichainFeatureFlag"
        
        /**
         * Check if user is eligible for multichain based on user ID
         * Used for gradual rollout strategies
         */
        fun isEligibleForMultichain(userId: String, rolloutPercentage: Int = 20): Boolean {
            if (rolloutPercentage >= 100) return true
            if (rolloutPercentage <= 0) return false
            
            // Use consistent hash-based rollout
            return userId.hashCode().let { hash ->
                kotlin.math.abs(hash % 100) < rolloutPercentage
            }
        }
        
        /**
         * Check if user is in beta group for multichain features
         */
        fun isBetaUser(userId: String): Boolean {
            // Beta users are determined by specific hash ranges
            return userId.hashCode().let { hash ->
                kotlin.math.abs(hash % 1000) < 50 // 5% beta users
            }
        }
        
        /**
         * Check if multichain should be enabled based on build type
         */
        fun isEnabledForBuildType(isDebugBuild: Boolean, isInternalBuild: Boolean): Boolean {
            return isDebugBuild || isInternalBuild
        }
    }
}
