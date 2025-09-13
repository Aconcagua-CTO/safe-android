package io.gnosis.data.models

import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import java.math.BigInteger

/**
 * Known tokens for each chain to provide better token information
 * when the API doesn't return complete token details.
 */
object KnownTokens {
    
    /**
     * Get known tokens for a specific chain ID
     */
    fun getKnownTokens(chainId: BigInteger): List<KnownToken> {
        return when (chainId) {
            Chain.ID_ROOTSTOCK_TESTNET -> getRootstockTestnetTokens()
            Chain.ID_ROOTSTOCK -> getRootstockMainnetTokens()
            // Add more chains as needed
            else -> emptyList()
        }
    }
    
    /**
     * Get token information by address for a specific chain
     */
    fun getTokenInfo(chainId: BigInteger, tokenAddress: String): KnownToken? {
        return getKnownTokens(chainId).find { 
            it.address.equals(tokenAddress, ignoreCase = true) 
        }
    }
    
    // ===== ROOTSTOCK TESTNET TOKENS =====
    
    private fun getRootstockTestnetTokens(): List<KnownToken> = listOf(
        KnownToken(
            chainId = Chain.ID_ROOTSTOCK_TESTNET,
            address = "0xC5090913C70fdFDb27f69762DA34e9B9C556CB8a",
            name = "ACON18WBTC",
            symbol = "ACON18WBTC",
            decimals = 18,
            logoUri = "https://assets.safe.rootstock.io/tokens/logos/0xC5090913C70fdFDb27f69762DA34e9B9C556CB8a.png",
            tokenType = io.gnosis.data.models.assets.TokenType.ERC20
        )
        // Add more Rootstock testnet tokens as needed
    )
    
    // ===== ROOTSTOCK MAINNET TOKENS =====
    
    private fun getRootstockMainnetTokens(): List<KnownToken> = listOf(
        // Add known Rootstock mainnet tokens here
        // Example:
        // KnownToken(
        //     chainId = Chain.ID_ROOTSTOCK,
        //     address = "0x...",
        //     name = "Token Name",
        //     symbol = "SYMBOL",
        //     decimals = 18,
        //     logoUri = "https://...",
        //     tokenType = io.gnosis.data.models.assets.TokenType.ERC20
        // )
    )
}

/**
 * Represents a known token with complete information
 */
data class KnownToken(
    val chainId: BigInteger,
    val address: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val logoUri: String?,
    val tokenType: io.gnosis.data.models.assets.TokenType
) {
    /**
     * Convert to TokenInfo for use in the app
     */
    fun toTokenInfo(): io.gnosis.data.models.assets.TokenInfo {
        return io.gnosis.data.models.assets.TokenInfo(
            tokenType = tokenType,
            address = address.asEthereumAddress()!!,
            decimals = decimals,
            symbol = symbol,
            name = name,
            logoUri = logoUri
        )
    }
}

