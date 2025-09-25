package io.gnosis.safe.multichain.ui.assets.coins

import io.gnosis.data.models.Chain
import java.math.BigDecimal

/**
 * View data types for multichain coin balance display
 * Represents aggregated balance information across multiple chains
 */
sealed class MultichainCoinsViewData {

    /**
     * Total balance display across all chains
     */
    data class TotalBalance(
        val totalBalance: String,
        val chainCount: Int = 0,
        val hasErrors: Boolean = false
    ) : MultichainCoinsViewData()

    /**
     * Banner for user onboarding and information
     */
    data class Banner(
        val type: Type
    ) : MultichainCoinsViewData() {
        enum class Type {
            ADD_OWNER_KEY,
            PASSCODE,
            NONE
        }
    }

    /**
     * Aggregated coin balance across multiple chains
     * Shows total balance for a token across all chains where it exists
     */
    data class AggregatedCoinBalance(
        val tokenAddress: String,
        val decimals: Int,
        val symbol: String,
        val logoUri: String?,
        val totalBalance: BigDecimal,
        val displayBalance: String,
        val fiatBalance: String,
        val chainsWithBalance: String, // Comma-separated chain names
        val chainCount: Int,
        val isExpanded: Boolean = false
    ) : MultichainCoinsViewData()

    /**
     * Chain-specific balance details (shown when expanded)
     */
    data class ChainSpecificBalance(
        val chain: Chain,
        val balance: io.gnosis.data.models.assets.Balance,
        val displayBalance: String,
        val fiatBalance: String
    ) : MultichainCoinsViewData()

    /**
     * Loading state for balance aggregation
     */
    data class LoadingBalance(
        val message: String = "Loading balances across chains...",
        val chainsLoaded: Int = 0,
        val totalChains: Int = 0
    ) : MultichainCoinsViewData()

    /**
     * Error state for failed balance loading
     */
    data class BalanceError(
        val message: String,
        val failedChains: List<Chain> = emptyList(),
        val canRetry: Boolean = true
    ) : MultichainCoinsViewData()

    /**
     * Empty state when no balances are found
     */
    data class EmptyBalances(
        val message: String = "No assets found across any chains"
    ) : MultichainCoinsViewData()
}
