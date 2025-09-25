package io.gnosis.safe.multichain.services

import android.util.Log
import io.gnosis.data.models.Chain
import io.gnosis.data.models.Safe
import io.gnosis.data.models.assets.CoinBalances
import io.gnosis.data.repositories.TokenRepository
import io.gnosis.safe.BuildConfig
import io.gnosis.safe.multichain.models.MultichainBalanceData
import io.gnosis.safe.multichain.models.MultichainBalanceResult
import io.gnosis.safe.multichain.models.MultichainSafe
import io.gnosis.safe.multichain.services.TokenGroupingStrategy
import io.gnosis.safe.ui.base.AppDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.TimeoutCancellationException
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for loading and aggregating balance data across multiple chains
 * Handles parallel loading, error handling, and timeout management
 */
@Singleton
class MultichainBalanceService @Inject constructor(
    private val tokenRepository: TokenRepository,
    private val appDispatchers: AppDispatchers
) {
    
    /**
     * Load aggregated balances for a multichain Safe across all its deployed chains
     * Uses parallel loading for optimal performance
     */
    suspend fun loadAggregatedBalances(
        multichainSafe: MultichainSafe,
        fiatCode: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): MultichainBalanceData = withContext(appDispatchers.background) {
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "loadAggregatedBalances() - loading balances for ${multichainSafe.localName} across ${multichainSafe.chainCount} chains")
            Log.d(TAG, "loadAggregatedBalances() - chains: ${multichainSafe.chainNames}")
            Log.d(TAG, "loadAggregatedBalances() - fiat: $fiatCode, timeout: ${timeoutMs}ms")
        }
        
        val balancesByChain = mutableMapOf<Chain, CoinBalances>()
        val errors = mutableMapOf<Chain, Exception>()
        
        // Load balances from all chains in parallel with timeout
        val deferredResults = multichainSafe.safesPerChain.values.map { safe ->
            async {
                try {
                    withTimeout(timeoutMs) {
                        val balance = tokenRepository.loadBalanceOf(safe, fiatCode)
                        safe.chain to Result.success(balance)
                    }
                } catch (e: TimeoutCancellationException) {
                    safe.chain to Result.failure(Exception("Timeout loading balance for ${safe.chain.name}", e))
                } catch (e: Exception) {
                    safe.chain to Result.failure(e)
                }
            }
        }
        
        // Collect results
        deferredResults.awaitAll().forEach { (chain, result) ->
            result.fold(
                onSuccess = { balance -> balancesByChain[chain] = balance },
                onFailure = { error -> errors[chain] = error as? Exception ?: Exception(error) }
            )
        }
        
        // Calculate total fiat value from successful chains
        val totalFiat = balancesByChain.values.sumOf { it.fiatTotal }
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "loadAggregatedBalances() - completed: ${balancesByChain.size} successful, ${errors.size} failed")
            Log.d(TAG, "loadAggregatedBalances() - total fiat value: $totalFiat $fiatCode")
            
            // Analyze token distribution for debugging
            val analysis = TokenGroupingStrategy.analyzeTokenDistribution(balancesByChain)
            Log.d(TAG, "loadAggregatedBalances() - token analysis: ${analysis.totalUniqueTokens} unique tokens")
            Log.d(TAG, "loadAggregatedBalances() - native token types: ${analysis.nativeTokenTypes}")
            Log.d(TAG, "loadAggregatedBalances() - ERC20 token types: ${analysis.erc20TokenTypes}")
            
            if (errors.isNotEmpty()) {
                errors.forEach { (chain, error) ->
                    Log.w(TAG, "loadAggregatedBalances() - failed to load ${chain.name}: ${error.message}")
                }
            }
        }
        
        MultichainBalanceData(
            totalFiatValue = totalFiat,
            balancesByChain = balancesByChain,
            errors = errors
        )
    }
    
    /**
     * Load balance for a single chain with error handling
     */
    suspend fun loadBalanceForChain(
        safe: Safe,
        fiatCode: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Result<CoinBalances> = withContext(appDispatchers.background) {
        try {
            withTimeout(timeoutMs) {
                Result.success(tokenRepository.loadBalanceOf(safe, fiatCode))
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Timeout loading balance for ${safe.chain.name}", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Load balances with progress updates
     * Useful for showing loading progress in UI
     */
    suspend fun loadAggregatedBalancesWithProgress(
        multichainSafe: MultichainSafe,
        fiatCode: String,
        onProgress: (loaded: Int, total: Int, chain: Chain?) -> Unit,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): MultichainBalanceData = withContext(appDispatchers.background) {
        
        val balancesByChain = mutableMapOf<Chain, CoinBalances>()
        val errors = mutableMapOf<Chain, Exception>()
        val totalChains = multichainSafe.safesPerChain.size
        var loadedChains = 0
        
        // Initial progress
        onProgress(0, totalChains, null)
        
        // Load balances from all chains in parallel
        val deferredResults = multichainSafe.safesPerChain.values.map { safe ->
            async {
                try {
                    withTimeout(timeoutMs) {
                        val balance = tokenRepository.loadBalanceOf(safe, fiatCode)
                        synchronized(this@withContext) {
                            loadedChains++
                            onProgress(loadedChains, totalChains, safe.chain)
                        }
                        safe.chain to Result.success(balance)
                    }
                } catch (e: Exception) {
                    synchronized(this@withContext) {
                        loadedChains++
                        onProgress(loadedChains, totalChains, safe.chain)
                    }
                    safe.chain to Result.failure(e)
                }
            }
        }
        
        // Collect results
        deferredResults.awaitAll().forEach { (chain, result) ->
            result.fold(
                onSuccess = { balance -> balancesByChain[chain] = balance },
                onFailure = { error -> errors[chain] = error as? Exception ?: Exception(error) }
            )
        }
        
        // Calculate total fiat value
        val totalFiat = balancesByChain.values.sumOf { it.fiatTotal }
        
        MultichainBalanceData(
            totalFiatValue = totalFiat,
            balancesByChain = balancesByChain,
            errors = errors
        )
    }
    
    /**
     * Retry failed balance loads for specific chains
     */
    suspend fun retryFailedBalances(
        multichainSafe: MultichainSafe,
        failedChains: List<Chain>,
        fiatCode: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): MultichainBalanceData = withContext(appDispatchers.background) {
        
        val balancesByChain = mutableMapOf<Chain, CoinBalances>()
        val errors = mutableMapOf<Chain, Exception>()
        
        val safesToRetry = failedChains.mapNotNull { chain ->
            multichainSafe.getSafeForChain(chain.chainId)
        }
        
        // Retry failed chains in parallel
        val deferredResults = safesToRetry.map { safe ->
            async {
                try {
                    withTimeout(timeoutMs) {
                        val balance = tokenRepository.loadBalanceOf(safe, fiatCode)
                        safe.chain to Result.success(balance)
                    }
                } catch (e: Exception) {
                    safe.chain to Result.failure(e)
                }
            }
        }
        
        // Collect retry results
        deferredResults.awaitAll().forEach { (chain, result) ->
            result.fold(
                onSuccess = { balance -> balancesByChain[chain] = balance },
                onFailure = { error -> errors[chain] = error as? Exception ?: Exception(error) }
            )
        }
        
        // Calculate total fiat value
        val totalFiat = balancesByChain.values.sumOf { it.fiatTotal }
        
        MultichainBalanceData(
            totalFiatValue = totalFiat,
            balancesByChain = balancesByChain,
            errors = errors
        )
    }
    
    /**
     * Get balance summary statistics
     */
    fun getBalanceSummary(balanceData: MultichainBalanceData): BalanceSummary {
        return BalanceSummary(
            totalChains = balanceData.balancesByChain.size + balanceData.errors.size,
            successfulChains = balanceData.balancesByChain.size,
            failedChains = balanceData.errors.size,
            totalFiatValue = balanceData.totalFiatValue,
            hasPartialData = balanceData.isPartiallyLoaded
        )
    }
    
    companion object {
        private const val TAG = "MultichainBalanceService"
        private const val DEFAULT_TIMEOUT_MS = 30_000L // 30 seconds
    }
}

/**
 * Summary statistics for balance loading results
 */
data class BalanceSummary(
    val totalChains: Int,
    val successfulChains: Int,
    val failedChains: Int,
    val totalFiatValue: BigDecimal,
    val hasPartialData: Boolean
) {
    val successRate: Float get() = if (totalChains > 0) successfulChains.toFloat() / totalChains else 0f
    val isFullyLoaded: Boolean get() = failedChains == 0
}
