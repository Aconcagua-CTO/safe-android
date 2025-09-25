package io.gnosis.safe.multichain.models

import io.gnosis.data.models.Chain
import io.gnosis.data.models.Safe
import io.gnosis.data.models.assets.CoinBalances
import pm.gnosis.model.Solidity
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Represents a Safe vault that can be deployed across multiple blockchains
 * Groups individual Safe instances by their shared address
 */
data class MultichainSafe(
    val address: Solidity.Address,
    val localName: String,
    val safesPerChain: Map<BigInteger, Safe> // chainId -> Safe
) {
    val deployedChains: List<Chain> get() = safesPerChain.values.map { it.chain }
    val chainNames: String get() = deployedChains.joinToString(", ") { it.name }
    val chainCount: Int get() = safesPerChain.size
    
    /**
     * Get Safe instance for a specific chain
     */
    fun getSafeForChain(chainId: BigInteger): Safe? = safesPerChain[chainId]
    
    /**
     * Check if Safe is deployed on a specific chain
     */
    fun isDeployedOnChain(chainId: BigInteger): Boolean = safesPerChain.containsKey(chainId)
    
    /**
     * Get all chain IDs where this Safe is deployed
     */
    val chainIds: List<BigInteger> get() = safesPerChain.keys.toList()
}

/**
 * Aggregated balance data across multiple chains for a multichain Safe
 */
data class MultichainBalanceData(
    val totalFiatValue: BigDecimal,
    val balancesByChain: Map<Chain, CoinBalances>,
    val isLoading: Boolean = false,
    val errors: Map<Chain, Exception> = emptyMap()
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()
    val successfulChains: List<Chain> get() = balancesByChain.keys.toList()
    val failedChains: List<Chain> get() = errors.keys.toList()
    val isPartiallyLoaded: Boolean get() = balancesByChain.isNotEmpty() && hasErrors
    val loadedChainCount: Int get() = balancesByChain.size
    val errorChainCount: Int get() = errors.size
}

/**
 * View data types for multichain Safe selection UI
 */
sealed class MultichainSafeSelectionViewData {
    object AddSafeHeader : MultichainSafeSelectionViewData()
    
    data class MultichainSafeItem(
        val multichainSafe: MultichainSafe
    ) : MultichainSafeSelectionViewData()
}

/**
 * Result wrapper for balance loading operations
 */
sealed class MultichainBalanceResult {
    data class Success(val balanceData: MultichainBalanceData) : MultichainBalanceResult()
    data class PartialSuccess(val balanceData: MultichainBalanceData) : MultichainBalanceResult()
    data class Error(val exception: Exception) : MultichainBalanceResult()
    object Loading : MultichainBalanceResult()
}
