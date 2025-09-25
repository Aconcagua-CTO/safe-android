package io.gnosis.safe.multichain.services

import android.util.Log
import io.gnosis.data.models.Chain
import io.gnosis.data.models.assets.Balance
import io.gnosis.data.models.assets.TokenType
import io.gnosis.safe.BuildConfig
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import java.math.BigInteger

/**
 * Strategy for grouping tokens across multiple chains
 * Handles the special case of native tokens that share the same zero address
 */
object TokenGroupingStrategy {
    
    private const val TAG = "TokenGroupingStrategy"
    private val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"
    
    /**
     * Generate a unique grouping key for a token that works across chains
     * For native tokens: combines address + symbol (since they all use 0x0000... address)
     * For ERC20 tokens: uses just the address (since addresses are unique)
     */
    fun getTokenGroupingKey(balance: Balance): String {
        val address = balance.tokenInfo.address.asEthereumAddressChecksumString()
        val symbol = balance.tokenInfo.symbol
        val isNativeToken = balance.tokenInfo.tokenType == TokenType.NATIVE_CURRENCY || 
                           address.equals(ZERO_ADDRESS, ignoreCase = true)
        
        val groupingKey = if (isNativeToken) {
            // For native tokens, group by symbol to separate ETH from RBTC, etc.
            "NATIVE_${symbol.uppercase()}"
        } else {
            // For ERC20 tokens, group by address (addresses are unique across chains)
            "ERC20_$address"
        }
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "getTokenGroupingKey() - token: ${symbol}, address: ${address}")
            Log.d(TAG, "getTokenGroupingKey() - isNative: $isNativeToken, key: $groupingKey")
        }
        
        return groupingKey
    }
    
    /**
     * Check if a token is a native currency
     */
    fun isNativeToken(balance: Balance): Boolean {
        val address = balance.tokenInfo.address.asEthereumAddressChecksumString()
        return balance.tokenInfo.tokenType == TokenType.NATIVE_CURRENCY || 
               address.equals(ZERO_ADDRESS, ignoreCase = true)
    }
    
    /**
     * Get a display name for a grouped token
     */
    fun getTokenDisplayName(groupingKey: String, chainBalances: List<Pair<Chain, Balance>>): String {
        val firstBalance = chainBalances.first().second
        val symbol = firstBalance.tokenInfo.symbol
        
        return if (groupingKey.startsWith("NATIVE_")) {
            // For native tokens, use the symbol (ETH, RBTC, etc.)
            symbol
        } else {
            // For ERC20 tokens, use the full name if available
            firstBalance.tokenInfo.name.ifEmpty { symbol }
        }
    }
    
    /**
     * Get chains where a token is available as a comma-separated string
     */
    fun getChainsDisplayString(chainBalances: List<Pair<Chain, Balance>>): String {
        return chainBalances.map { it.first.name }.joinToString(", ")
    }
    
    /**
     * Analyze token distribution across chains for debugging
     */
    fun analyzeTokenDistribution(
        balancesByChain: Map<Chain, io.gnosis.data.models.assets.CoinBalances>
    ): TokenDistributionAnalysis {
        val tokenGroups = mutableMapOf<String, MutableList<Pair<Chain, Balance>>>()
        
        balancesByChain.forEach { (chain, coinBalances) ->
            coinBalances.items.forEach { balance ->
                val groupingKey = getTokenGroupingKey(balance)
                tokenGroups.getOrPut(groupingKey) { mutableListOf() }
                    .add(chain to balance)
            }
        }
        
        val nativeTokenGroups = tokenGroups.filter { it.key.startsWith("NATIVE_") }
        val erc20TokenGroups = tokenGroups.filter { it.key.startsWith("ERC20_") }
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "analyzeTokenDistribution() - found ${tokenGroups.size} unique tokens")
            Log.d(TAG, "analyzeTokenDistribution() - native tokens: ${nativeTokenGroups.size}")
            Log.d(TAG, "analyzeTokenDistribution() - ERC20 tokens: ${erc20TokenGroups.size}")
            
            nativeTokenGroups.forEach { (key, chains) ->
                val symbol = chains.first().second.tokenInfo.symbol
                val chainNames = chains.map { it.first.name }
                Log.d(TAG, "analyzeTokenDistribution() - native $symbol on: ${chainNames.joinToString(", ")}")
            }
        }
        
        return TokenDistributionAnalysis(
            totalUniqueTokens = tokenGroups.size,
            nativeTokenTypes = nativeTokenGroups.size,
            erc20TokenTypes = erc20TokenGroups.size,
            tokenGroups = tokenGroups
        )
    }
}

/**
 * Analysis result for token distribution across chains
 */
data class TokenDistributionAnalysis(
    val totalUniqueTokens: Int,
    val nativeTokenTypes: Int, // Different native currencies (ETH, RBTC, etc.)
    val erc20TokenTypes: Int,
    val tokenGroups: Map<String, List<Pair<Chain, Balance>>>
)
