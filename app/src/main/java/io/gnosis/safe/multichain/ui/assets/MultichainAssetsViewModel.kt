package io.gnosis.safe.multichain.ui.assets

import android.util.Log
import io.gnosis.safe.BuildConfig
import io.gnosis.safe.multichain.models.MultichainSafe
import io.gnosis.safe.multichain.repositories.MultichainSafeRepository
import io.gnosis.safe.ui.base.AppDispatchers
import io.gnosis.safe.ui.base.BaseStateViewModel
import pm.gnosis.utils.asEthereumAddressString
import javax.inject.Inject

/**
 * ViewModel for managing multichain assets and balance display
 * Observes active multichain Safe changes and coordinates balance loading
 */
class MultichainAssetsViewModel @Inject constructor(
    private val multichainSafeRepository: MultichainSafeRepository,
    appDispatchers: AppDispatchers
) : BaseStateViewModel<MultichainAssetsState>(appDispatchers) {

    var activeMultichainSafe: MultichainSafe? = null
        private set

    override fun initialState(): MultichainAssetsState = 
        MultichainAssetsState.SafeLoading(null)

    init {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "init() - initializing MultichainAssetsViewModel")
        }
        
        safeLaunch {
            multichainSafeRepository.activeSafeFlow().collect { multichainSafe ->
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "activeSafeFlow() - active safe changed: ${multichainSafe?.localName ?: "none"}")
                    multichainSafe?.let { safe ->
                        Log.d(TAG, "activeSafeFlow() - safe address: ${safe.address.asEthereumAddressString()}")
                        Log.d(TAG, "activeSafeFlow() - deployed chains: ${safe.chainNames}")
                        Log.d(TAG, "activeSafeFlow() - chain count: ${safe.chainCount}")
                    }
                }
                
                updateState {
                    activeMultichainSafe = multichainSafe
                    MultichainAssetsState.ActiveMultichainSafe(multichainSafe, null)
                }
            }
        }
    }

    fun updateTotalBalance(balance: String) {
        safeLaunch {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "updateTotalBalance() - updating total balance to: $balance")
            }
            
            updateState {
                MultichainAssetsState.TotalBalance(balance, null)
            }
            updateState {
                MultichainAssetsState.ActiveMultichainSafe(activeMultichainSafe, null)
            }
        }
    }

    fun getActiveMultichainSafeName(): String? {
        return activeMultichainSafe?.localName
    }

    fun getActiveMultichainSafeChainCount(): Int {
        return activeMultichainSafe?.chainCount ?: 0
    }

    companion object {
        private const val TAG = "MultichainAssetsViewModel"
    }
}

/**
 * States for multichain assets display
 */
sealed class MultichainAssetsState : BaseStateViewModel.State {

    data class SafeLoading(
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : MultichainAssetsState()

    data class ActiveMultichainSafe(
        val multichainSafe: MultichainSafe?,
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : MultichainAssetsState()

    data class LoadingBalances(
        val multichainSafe: MultichainSafe,
        val refreshing: Boolean,
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : MultichainAssetsState()

    data class BalancesLoaded(
        val multichainSafe: MultichainSafe,
        val balanceData: io.gnosis.safe.multichain.models.MultichainBalanceData,
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : MultichainAssetsState()

    data class BalanceError(
        val multichainSafe: MultichainSafe,
        val error: Exception,
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : MultichainAssetsState()

    data class TotalBalance(
        val totalBalance: String,
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : MultichainAssetsState()
}
