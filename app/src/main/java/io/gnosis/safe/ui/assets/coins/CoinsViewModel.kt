package io.gnosis.safe.ui.assets.coins

import android.util.Log
import io.gnosis.data.models.Chain
import io.gnosis.data.models.assets.CoinBalances
import io.gnosis.data.repositories.CredentialsRepository
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.data.repositories.TokenRepository
import io.gnosis.safe.BuildConfig
import io.gnosis.safe.Tracker
import io.gnosis.safe.multichain.navigation.MultichainNavigationHelper
import io.gnosis.safe.multichain.repositories.MultichainSafeRepository
import io.gnosis.safe.multichain.services.MultichainBalanceService
import io.gnosis.safe.ui.assets.coins.CoinsViewData.Banner
import io.gnosis.safe.ui.base.AppDispatchers
import io.gnosis.safe.ui.base.BaseStateViewModel
import io.gnosis.safe.ui.settings.app.SettingsHandler
import io.gnosis.safe.utils.BalanceFormatter
import io.gnosis.safe.utils.convertAmount
import kotlinx.coroutines.flow.collect
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.utils.asEthereumAddressString
import java.math.RoundingMode
import javax.inject.Inject

class CoinsViewModel
@Inject constructor(
    private val tokenRepository: TokenRepository,
    private val safeRepository: SafeRepository,
    private val credentialsRepository: CredentialsRepository,
    private val settingsHandler: SettingsHandler,
    private val balanceFormatter: BalanceFormatter,
    private val tracker: Tracker,
    private val multichainNavigationHelper: MultichainNavigationHelper,
    private val multichainSafeRepository: MultichainSafeRepository,
    private val multichainBalanceService: MultichainBalanceService,
    appDispatchers: AppDispatchers
) : BaseStateViewModel<CoinsState>(appDispatchers),
    CoinsAdapter.OwnerBannerListener {

    override fun initialState(): CoinsState = CoinsState(loading = false, refreshing = false, viewAction = null)

    init {
        safeLaunch {
            safeRepository.activeSafeFlow().collect { load() }
        }
    }

    fun load(refreshing: Boolean = false) {
        safeLaunch {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "load() - starting balance load, refreshing: $refreshing")
                Log.d(TAG, "load() - multichain enabled: ${multichainNavigationHelper.shouldUseMultichainAssets()}")
            }
            
            if (multichainNavigationHelper.shouldUseMultichainAssets()) {
                loadMultichainBalances(refreshing)
            } else {
                loadSingleChainBalances(refreshing)
            }
        }
    }

    private suspend fun loadMultichainBalances(refreshing: Boolean) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "loadMultichainBalances() - loading balances using multichain approach")
        }
        
        val multichainSafe = multichainSafeRepository.getActiveMultichainSafe()
        val userDefaultFiat = settingsHandler.userDefaultFiat
        
        if (multichainSafe != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "loadMultichainBalances() - found active multichain safe: ${multichainSafe.localName}")
                Log.d(TAG, "loadMultichainBalances() - chains: ${multichainSafe.chainNames}")
            }
            
            // Use first chain Safe for UI compatibility
            val firstChainSafe = multichainSafe.safesPerChain.values.first()
            updateState {
                CoinsState(
                    loading = !refreshing,
                    refreshing = refreshing,
                    viewAction = if (refreshing) null else ViewAction.UpdateActiveSafe(firstChainSafe)
                )
            }
            
            try {
                val balanceData = multichainBalanceService.loadAggregatedBalances(multichainSafe, userDefaultFiat)
                
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "loadMultichainBalances() - balance data loaded successfully")
                    Log.d(TAG, "loadMultichainBalances() - total fiat: ${balanceData.totalFiatValue}")
                    Log.d(TAG, "loadMultichainBalances() - successful chains: ${balanceData.successfulChains.size}")
                    Log.d(TAG, "loadMultichainBalances() - failed chains: ${balanceData.failedChains.size}")
                }
                
                val banner = when {
                    settingsHandler.showOwnerBanner && credentialsRepository.ownerCount() == 0 -> Banner.Type.ADD_OWNER_KEY
                    settingsHandler.showPasscodeBanner && credentialsRepository.ownerCount() > 0 -> Banner.Type.PASSCODE
                    else -> Banner.Type.NONE
                }
                
                val totalBalance = getMultichainTotalBalanceViewData(balanceData)
                val balances = getMultichainBalanceViewData(balanceData, banner)
                
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "loadMultichainBalances() - created ${balances.size} balance view items")
                    Log.d(TAG, "loadMultichainBalances() - total balance display: ${totalBalance.totalFiat}")
                }
                
                updateState { 
                    CoinsState(
                        loading = false, 
                        refreshing = false, 
                        viewAction = UpdateBalances(totalBalance, balances)
                    ) 
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "loadMultichainBalances() - error loading balances: ${e.message}", e)
                    Log.d(TAG, "loadMultichainBalances() - falling back to single-chain mode")
                }
                
                // Fallback to single-chain loading on error
                loadSingleChainBalances(refreshing)
            }
        } else {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "loadMultichainBalances() - no active multichain safe found, falling back to single-chain")
            }
            loadSingleChainBalances(refreshing)
        }
    }

    private suspend fun loadSingleChainBalances(refreshing: Boolean) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "loadSingleChainBalances() - loading balances using single-chain approach")
        }
        
        val safe = safeRepository.getActiveSafe()
        val userDefaultFiat = settingsHandler.userDefaultFiat
        if (safe != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "loadSingleChainBalances() - found active safe: ${safe.localName} on ${safe.chain.name}")
            }
            
            updateState {
                CoinsState(
                    loading = !refreshing,
                    refreshing = refreshing,
                    viewAction = if (refreshing) null else ViewAction.UpdateActiveSafe(safe)
                )
            }
            val balanceInfo = tokenRepository.loadBalanceOf(safe, userDefaultFiat)
            val banner = when {
                settingsHandler.showOwnerBanner && credentialsRepository.ownerCount() == 0 -> Banner.Type.ADD_OWNER_KEY
                settingsHandler.showPasscodeBanner && credentialsRepository.ownerCount() > 0 -> Banner.Type.PASSCODE
                else -> Banner.Type.NONE
            }
            val totalBalance = getTotalBalanceViewData(balanceInfo)
            val balances = getBalanceViewData(balanceInfo, banner)
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "loadSingleChainBalances() - total balance display: ${totalBalance.totalFiat}")
            }
            
            updateState { CoinsState(loading = false, refreshing = false, viewAction = UpdateBalances(totalBalance, balances)) }
        }
    }

    fun isLoading(): Boolean {
        return (state.value as CoinsState).loading
    }

    suspend fun getTotalBalanceViewData(coinBalanceData: CoinBalances): CoinsViewData.TotalBalance {
        val userCurrencyCode = settingsHandler.userDefaultFiat

        return CoinsViewData.TotalBalance(
            balanceFormatter.fiatBalanceWithCurrency(
                coinBalanceData.fiatTotal.setScale(2, RoundingMode.HALF_UP),
                userCurrencyCode
            )
        )
    }

    suspend fun getBalanceViewData(coinBalanceData: CoinBalances, banner: Banner.Type): List<CoinsViewData> {
        val userCurrencyCode = settingsHandler.userDefaultFiat
        val result = mutableListOf<CoinsViewData>()

        when (banner) {
            Banner.Type.ADD_OWNER_KEY -> {
                result.add(Banner(Banner.Type.ADD_OWNER_KEY))
            }
            Banner.Type.PASSCODE -> {
                result.add(Banner(Banner.Type.PASSCODE))
            }
            Banner.Type.NONE -> {
                // No banner to add for NONE type
            }
        }

        coinBalanceData.items.forEach {
            result.add(
                CoinsViewData.CoinBalance(
                    it.tokenInfo.address.asEthereumAddressChecksumString(),
                    it.tokenInfo.decimals,
                    it.tokenInfo.symbol,
                    it.tokenInfo.logoUri,
                    it.balance.convertAmount(it.tokenInfo.decimals),
                    balanceFormatter.shortAmount(it.balance.convertAmount(it.tokenInfo.decimals)),
                    balanceFormatter.fiatBalanceWithCurrency(
                        it.fiatBalance.setScale(2, RoundingMode.HALF_UP),
                        userCurrencyCode
                    )
                )
            )
        }

        return result
    }

    suspend fun getMultichainTotalBalanceViewData(balanceData: io.gnosis.safe.multichain.models.MultichainBalanceData): CoinsViewData.TotalBalance {
        val userCurrencyCode = settingsHandler.userDefaultFiat
        val totalBalanceFormatted = balanceFormatter.fiatBalanceWithCurrency(
            balanceData.totalFiatValue.setScale(2, RoundingMode.HALF_UP),
            userCurrencyCode
        )
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "getMultichainTotalBalanceViewData() - total balance: $totalBalanceFormatted")
            Log.d(TAG, "getMultichainTotalBalanceViewData() - chains with data: ${balanceData.successfulChains.size}")
            Log.d(TAG, "getMultichainTotalBalanceViewData() - has errors: ${balanceData.hasErrors}")
        }
        
        return CoinsViewData.TotalBalance(totalBalanceFormatted)
    }

    suspend fun getMultichainBalanceViewData(
        balanceData: io.gnosis.safe.multichain.models.MultichainBalanceData, 
        banner: Banner.Type
    ): List<CoinsViewData> {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "getMultichainBalanceViewData() - processing multichain balance data")
            Log.d(TAG, "getMultichainBalanceViewData() - chains to process: ${balanceData.balancesByChain.keys.map { it.name }}")
        }
        
        val userCurrencyCode = settingsHandler.userDefaultFiat
        val result = mutableListOf<CoinsViewData>()

        // Add banner if needed
        when (banner) {
            Banner.Type.ADD_OWNER_KEY -> {
                result.add(CoinsViewData.Banner(Banner.Type.ADD_OWNER_KEY))
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "getMultichainBalanceViewData() - added ADD_OWNER_KEY banner")
                }
            }
            Banner.Type.PASSCODE -> {
                result.add(CoinsViewData.Banner(Banner.Type.PASSCODE))
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "getMultichainBalanceViewData() - added PASSCODE banner")
                }
            }
            Banner.Type.NONE -> {
                // No banner to add
            }
        }

        // Group tokens by address across chains and sum balances
        val tokenGroups = mutableMapOf<String, MutableList<Pair<Chain, io.gnosis.data.models.assets.Balance>>>()
        
        balanceData.balancesByChain.forEach { (chain, coinBalances) ->
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "getMultichainBalanceViewData() - processing ${coinBalances.items.size} tokens from ${chain.name}")
            }
            
            coinBalances.items.forEach { balance ->
                val groupingKey = io.gnosis.safe.multichain.services.TokenGroupingStrategy.getTokenGroupingKey(balance)
                tokenGroups.getOrPut(groupingKey) { mutableListOf() }
                    .add(chain to balance)
                
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "getMultichainBalanceViewData() - token ${balance.tokenInfo.symbol} grouped as: $groupingKey")
                }
            }
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "getMultichainBalanceViewData() - grouped into ${tokenGroups.size} unique token groups")
        }

        // Create aggregated coin balance view data
        tokenGroups.forEach { (groupingKey, chainBalances) ->
            val firstBalance = chainBalances.first().second
            val totalTokenBalance = chainBalances.sumOf { it.second.balance }
            val totalFiatBalance = chainBalances.sumOf { it.second.fiatBalance }
            val chainsWithToken = chainBalances.map { it.first.name }.joinToString(", ")

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "getMultichainBalanceViewData() - token group $groupingKey:")
                Log.d(TAG, "  - symbol: ${firstBalance.tokenInfo.symbol}")
                Log.d(TAG, "  - present on ${chainBalances.size} chains: $chainsWithToken")
                Log.d(TAG, "  - total balance: $totalTokenBalance wei")
                Log.d(TAG, "  - total fiat: $totalFiatBalance $userCurrencyCode")
            }

            result.add(
                CoinsViewData.CoinBalance(
                    firstBalance.tokenInfo.address.asEthereumAddressChecksumString(),
                    firstBalance.tokenInfo.decimals,
                    firstBalance.tokenInfo.symbol,
                    firstBalance.tokenInfo.logoUri,
                    totalTokenBalance.convertAmount(firstBalance.tokenInfo.decimals),
                    balanceFormatter.shortAmount(totalTokenBalance.convertAmount(firstBalance.tokenInfo.decimals)),
                    balanceFormatter.fiatBalanceWithCurrency(
                        totalFiatBalance.setScale(2, RoundingMode.HALF_UP),
                        userCurrencyCode
                    )
                )
            )
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "getMultichainBalanceViewData() - returning ${result.size} view data items")
        }

        return result
    }

    override fun onBannerDismissed(type: Banner.Type) {
        when (type) {
            Banner.Type.ADD_OWNER_KEY -> {
                settingsHandler.showOwnerBanner = false
                tracker.logBannerOwnerSkip()
            }
            Banner.Type.PASSCODE -> {
                settingsHandler.showPasscodeBanner = false
                tracker.logBannerPasscodeSkip()
            }
            Banner.Type.NONE -> {
                // No action needed for NONE type
            }
        }
        safeLaunch {
            updateState { CoinsState(loading = false, refreshing = false, viewAction = DismissOwnerBanner) }
        }
    }

    override fun onBannerActionTriggered(type: Banner.Type) {
        when (type) {
            Banner.Type.ADD_OWNER_KEY -> {
                settingsHandler.showOwnerBanner = false
                tracker.logBannerOwnerImport()
            }
            Banner.Type.PASSCODE -> {
                settingsHandler.showPasscodeBanner = false
                tracker.logBannerPasscodeCreate()
            }
            Banner.Type.NONE -> {
                // No action needed for NONE type
            }
        }
        safeLaunch {
            updateState { CoinsState(loading = false, refreshing = false, viewAction = DismissOwnerBanner) }
        }
    }

    companion object {
        private const val TAG = "CoinsViewModel"
    }
}

data class CoinsState(
    val loading: Boolean,
    val refreshing: Boolean,
    override var viewAction: BaseStateViewModel.ViewAction?
) : BaseStateViewModel.State

data class UpdateBalances(
    val newTotalBalance: CoinsViewData.TotalBalance,
    val newBalances: List<CoinsViewData>
) : BaseStateViewModel.ViewAction

object DismissOwnerBanner: BaseStateViewModel.ViewAction
