package io.gnosis.safe.ui.assets

import android.util.Log
import io.gnosis.data.models.Safe
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.safe.BuildConfig
import io.gnosis.safe.multichain.navigation.MultichainNavigationHelper
import io.gnosis.safe.multichain.repositories.MultichainSafeRepository
import io.gnosis.safe.ui.base.AppDispatchers
import io.gnosis.safe.ui.base.BaseStateViewModel
import pm.gnosis.utils.asEthereumAddressString
import javax.inject.Inject

class AssetsViewModel @Inject constructor(
    private val safeRepository: SafeRepository,
    private val multichainNavigationHelper: MultichainNavigationHelper,
    private val multichainSafeRepository: MultichainSafeRepository,
    appDispatchers: AppDispatchers
) : BaseStateViewModel<SafeBalancesState>(appDispatchers) {

    var activeSafe: Safe? = null
        private set

    override fun initialState(): SafeBalancesState = SafeBalancesState.SafeLoading(null)

    init {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "init() - initializing AssetsViewModel")
        }
        
        safeLaunch {
            if (multichainNavigationHelper.shouldUseMultichainAssets()) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "init() - using multichain safe flow")
                }
                
                // Observe multichain safe changes
                multichainSafeRepository.activeSafeFlow().collect { multichainSafe ->
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "init() - multichain safe changed: ${multichainSafe?.localName ?: "none"}")
                        multichainSafe?.let { safe ->
                            Log.d(TAG, "init() - multichain safe chains: ${safe.chainNames}")
                        }
                    }
                    
                    // Convert multichain safe to regular safe for existing UI compatibility
                    val safe = multichainSafe?.safesPerChain?.values?.firstOrNull()
                    updateState {
                        activeSafe = safe
                        SafeBalancesState.ActiveSafe(safe, null)
                    }
                }
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "init() - using single-chain safe flow")
                }
                
                // Use existing single-chain flow
                safeRepository.activeSafeFlow().collect { safe ->
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "init() - single-chain safe changed: ${safe?.localName ?: "none"}")
                        safe?.let { s ->
                            Log.d(TAG, "init() - safe chain: ${s.chain.name}")
                        }
                    }
                    
                    updateState {
                        activeSafe = safe
                        SafeBalancesState.ActiveSafe(safe, null)
                    }
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
                SafeBalancesState.TotalBalance(balance, null)
            }
            updateState {
                SafeBalancesState.ActiveSafe(activeSafe, null)
            }
        }
    }
    
    companion object {
        private const val TAG = "AssetsViewModel"
    }
}

sealed class SafeBalancesState : BaseStateViewModel.State {

    data class SafeLoading(
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : SafeBalancesState()

    data class ActiveSafe(
        val safe: Safe?,
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : SafeBalancesState()

    data class TotalBalance(
        val totalBalance: String,
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : SafeBalancesState()
}
