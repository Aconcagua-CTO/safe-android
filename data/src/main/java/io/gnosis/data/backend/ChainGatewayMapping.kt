package io.gnosis.data.backend

import io.gnosis.data.models.Chain
import java.math.BigInteger

/**
 * Maps chain IDs to their specific Safe API gateway URLs.
 * This allows different chains to use different gateways.
 */
object ChainGatewayMapping {
    
    // Rootstock gateways
    private const val ROOTSTOCK_MAINNET_GATEWAY = "https://transaction.safe.rootstock.io/"
    private const val ROOTSTOCK_TESTNET_GATEWAY = "https://transaction-testnet.safe.rootstock.io/"
    
    // Base Sepolia uses the standard Safe Client Gateway (production)
    private const val SAFE_CLIENT_GATEWAY_PRODUCTION = "https://safe-client.safe.global/"
    
    // Default gateway (from BuildConfig)
    private val DEFAULT_GATEWAY = io.gnosis.data.BuildConfig.CLIENT_GATEWAY_URL
    
    /**
     * Returns the appropriate gateway URL for the given chain ID.
     * If no specific mapping exists, returns the default gateway.
     */
    fun getGatewayUrl(chainId: BigInteger): String {
        return when (chainId) {
            Chain.ID_ROOTSTOCK -> ROOTSTOCK_MAINNET_GATEWAY
            Chain.ID_ROOTSTOCK_TESTNET -> ROOTSTOCK_TESTNET_GATEWAY
            else -> SAFE_CLIENT_GATEWAY_PRODUCTION // Use production gateway for all standard chains
        }
    }
    
    /**
     * Returns the appropriate gateway URL for the given chain.
     * If no specific mapping exists, returns the default gateway.
     */
    fun getGatewayUrl(chain: Chain): String {
        return getGatewayUrl(chain.chainId)
    }
    
    /**
     * Checks if a chain has a custom gateway URL.
     */
    fun hasCustomGateway(chainId: BigInteger): Boolean {
        return when (chainId) {
            Chain.ID_ROOTSTOCK, Chain.ID_ROOTSTOCK_TESTNET -> true
            else -> true  // All standard chains now use production gateway (different from staging default)
        }
    }

    fun useCustomPath(chainId: BigInteger): Boolean {
        return when (chainId) {
            Chain.ID_ROOTSTOCK, Chain.ID_ROOTSTOCK_TESTNET -> true
            // Base Sepolia uses standard Client Gateway path, not custom
            else -> false
        }
    }
}
