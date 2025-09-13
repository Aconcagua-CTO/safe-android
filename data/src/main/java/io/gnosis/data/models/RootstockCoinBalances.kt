package io.gnosis.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.gnosis.data.models.assets.Balance
import io.gnosis.data.models.assets.CoinBalances
import io.gnosis.data.models.assets.TokenInfo
import io.gnosis.data.models.assets.TokenType
import pm.gnosis.common.adapters.moshi.BigDecimalNumber
import pm.gnosis.common.adapters.moshi.DecimalNumber
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import java.math.BigDecimal
import java.math.BigInteger

// Rootstock returns a simple array of balances, not an object with fiatTotal and items
typealias RootstockBalancesArray = List<RootstockBalance>

@JsonClass(generateAdapter = true)
data class RootstockBalance(
    @Json(name = "tokenAddress") val tokenAddress: String?,
    @Json(name = "token") val token: RootstockTokenInfo?,
    @Json(name = "balance") @field:DecimalNumber val balance: BigInteger
)

@JsonClass(generateAdapter = true)
data class RootstockTokenInfo(
    @Json(name = "name") val name: String,
    @Json(name = "symbol") val symbol: String,
    @Json(name = "decimals") val decimals: Int,
    @Json(name = "logoUri") val logoUri: String?
)

fun RootstockBalancesArray.toCoinBalances(chainId: BigInteger): CoinBalances {
    val balances = this.map { it.toBalance(chainId) }
    val fiatTotal = balances.sumOf { it.fiatBalance }
    return CoinBalances(fiatTotal, balances)
}

fun RootstockBalance.toBalance(chainId: BigInteger): Balance {
    val tokenInfo = if (tokenAddress != null) {
        // Check if we have known token information
        val knownToken = KnownTokens.getTokenInfo(chainId, tokenAddress)
        if (knownToken != null) {
            // Use known token information
            knownToken.toTokenInfo()
        } else if (token != null) {
            // Use token info from API response
            TokenInfo(
                io.gnosis.data.models.assets.TokenType.ERC20,
                tokenAddress.asEthereumAddress()!!,
                token.decimals,
                token.symbol,
                token.name,
                token.logoUri
            )
        } else {
            // Fallback for unknown token
            TokenInfo(
                io.gnosis.data.models.assets.TokenType.ERC20,
                tokenAddress.asEthereumAddress()!!,
                18, // Default decimals
                "Unknown",
                "Unknown Token",
                null
            )
        }
    } else {
        // Native currency (tokenAddress is null)
        val nativeSymbol = if (chainId == Chain.ID_ROOTSTOCK_TESTNET) "tRBTC" else "RBTC"
        val nativeName = if (chainId == Chain.ID_ROOTSTOCK_TESTNET) "Test Smart Bitcoin" else "Smart Bitcoin"
        
        TokenInfo(
            io.gnosis.data.models.assets.TokenType.NATIVE_CURRENCY,
            Solidity.Address(BigInteger.ZERO), // Use zero address for native currency
            18, // Default decimals for native currency
            nativeSymbol,
            nativeName,
            null
        )
    }
    
    // For now, set fiat balance to 0 since Rootstock API doesn't provide it
    // In a real implementation, you'd need to fetch exchange rates
    val fiatBalance = BigDecimal.ZERO
    
    return Balance(tokenInfo, balance, fiatBalance)
}
