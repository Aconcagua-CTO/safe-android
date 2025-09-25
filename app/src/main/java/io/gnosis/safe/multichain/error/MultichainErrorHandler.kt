package io.gnosis.safe.multichain.error

import android.util.Log
import io.gnosis.data.models.Chain
import io.gnosis.safe.BuildConfig
import io.gnosis.safe.multichain.MultichainFeatureFlag
import io.gnosis.safe.multichain.models.MultichainBalanceData
import io.gnosis.safe.multichain.models.MultichainSafe
import kotlinx.coroutines.TimeoutCancellationException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized error handling for multichain operations
 * Provides fallback strategies and comprehensive error logging
 */
@Singleton
class MultichainErrorHandler @Inject constructor(
    private val multichainFeatureFlag: MultichainFeatureFlag
) {
    
    /**
     * Handle balance loading errors with fallback strategies
     */
    fun handleBalanceLoadingError(
        multichainSafe: MultichainSafe,
        error: Exception,
        partialData: MultichainBalanceData? = null
    ): BalanceErrorStrategy {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "handleBalanceLoadingError() - error loading balances for ${multichainSafe.localName}")
            Log.e(TAG, "handleBalanceLoadingError() - error type: ${error::class.simpleName}")
            Log.e(TAG, "handleBalanceLoadingError() - error message: ${error.message}")
            Log.e(TAG, "handleBalanceLoadingError() - has partial data: ${partialData != null}")
        }
        
        val strategy = when (error) {
            is TimeoutCancellationException -> {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "handleBalanceLoadingError() - timeout error, suggesting retry with longer timeout")
                }
                BalanceErrorStrategy.RetryWithLongerTimeout
            }
            
            is UnknownHostException -> {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "handleBalanceLoadingError() - network error, suggesting network check")
                }
                BalanceErrorStrategy.CheckNetworkConnection
            }
            
            else -> {
                if (partialData != null && partialData.successfulChains.isNotEmpty()) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "handleBalanceLoadingError() - partial success, showing available data")
                        Log.d(TAG, "handleBalanceLoadingError() - successful chains: ${partialData.successfulChains.size}")
                        Log.d(TAG, "handleBalanceLoadingError() - failed chains: ${partialData.failedChains.size}")
                    }
                    BalanceErrorStrategy.ShowPartialData(partialData)
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, "handleBalanceLoadingError() - complete failure, falling back to single-chain")
                    }
                    BalanceErrorStrategy.FallbackToSingleChain
                }
            }
        }
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "handleBalanceLoadingError() - selected strategy: ${strategy::class.simpleName}")
        }
        
        return strategy
    }
    
    /**
     * Handle Safe selection errors
     */
    fun handleSafeSelectionError(
        error: Exception,
        fallbackSafe: MultichainSafe? = null
    ): SafeSelectionErrorStrategy {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "handleSafeSelectionError() - error in safe selection")
            Log.e(TAG, "handleSafeSelectionError() - error: ${error.message}", error)
            Log.e(TAG, "handleSafeSelectionError() - has fallback: ${fallbackSafe != null}")
        }
        
        val strategy = when {
            fallbackSafe != null -> {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "handleSafeSelectionError() - using fallback safe: ${fallbackSafe.localName}")
                }
                SafeSelectionErrorStrategy.UseFallbackSafe(fallbackSafe)
            }
            
            multichainFeatureFlag.isMultichainEnabled() -> {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "handleSafeSelectionError() - disabling multichain mode due to error")
                }
                SafeSelectionErrorStrategy.DisableMultichainMode
            }
            
            else -> {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "handleSafeSelectionError() - showing error to user")
                }
                SafeSelectionErrorStrategy.ShowErrorToUser(error.message ?: "Unknown error")
            }
        }
        
        return strategy
    }
    
    /**
     * Handle multichain feature flag errors
     */
    fun handleFeatureFlagError(error: Exception): FeatureFlagErrorStrategy {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "handleFeatureFlagError() - error with feature flag")
            Log.e(TAG, "handleFeatureFlagError() - error: ${error.message}", error)
        }
        
        return FeatureFlagErrorStrategy.FallbackToSingleChain
    }
    
    /**
     * Log performance metrics for debugging
     */
    fun logPerformanceMetrics(
        operation: String,
        startTime: Long,
        endTime: Long,
        success: Boolean,
        details: Map<String, Any> = emptyMap()
    ) {
        if (BuildConfig.DEBUG) {
            val duration = endTime - startTime
            Log.d(TAG, "logPerformanceMetrics() - operation: $operation")
            Log.d(TAG, "logPerformanceMetrics() - duration: ${duration}ms")
            Log.d(TAG, "logPerformanceMetrics() - success: $success")
            
            details.forEach { (key, value) ->
                Log.d(TAG, "logPerformanceMetrics() - $key: $value")
            }
        }
    }
    
    /**
     * Log chain-specific errors for analysis
     */
    fun logChainError(chain: Chain, error: Exception, operation: String) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "logChainError() - chain: ${chain.name} (${chain.chainId})")
            Log.w(TAG, "logChainError() - operation: $operation")
            Log.w(TAG, "logChainError() - error: ${error.message}", error)
        }
    }
    
    companion object {
        private const val TAG = "MultichainErrorHandler"
    }
}

/**
 * Error handling strategies for balance loading
 */
sealed class BalanceErrorStrategy {
    object RetryWithLongerTimeout : BalanceErrorStrategy()
    object CheckNetworkConnection : BalanceErrorStrategy()
    object FallbackToSingleChain : BalanceErrorStrategy()
    data class ShowPartialData(val partialData: MultichainBalanceData) : BalanceErrorStrategy()
}

/**
 * Error handling strategies for Safe selection
 */
sealed class SafeSelectionErrorStrategy {
    data class UseFallbackSafe(val safe: MultichainSafe) : SafeSelectionErrorStrategy()
    object DisableMultichainMode : SafeSelectionErrorStrategy()
    data class ShowErrorToUser(val message: String) : SafeSelectionErrorStrategy()
}

/**
 * Error handling strategies for feature flag operations
 */
sealed class FeatureFlagErrorStrategy {
    object FallbackToSingleChain : FeatureFlagErrorStrategy()
    object RetryOperation : FeatureFlagErrorStrategy()
    data class ShowErrorMessage(val message: String) : FeatureFlagErrorStrategy()
}
