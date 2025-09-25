package io.gnosis.safe.multichain.ui.selection

import android.util.Log
import io.gnosis.safe.BuildConfig
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.safe.multichain.migration.MultichainMigrationHelper
import io.gnosis.safe.multichain.models.MultichainSafe
import io.gnosis.safe.multichain.models.MultichainSafeSelectionViewData
import io.gnosis.safe.multichain.repositories.MultichainSafeRepository
import io.gnosis.safe.ui.base.AppDispatchers
import io.gnosis.safe.ui.base.BaseStateViewModel
import io.gnosis.safe.ui.settings.app.SettingsHandler
import io.gnosis.safe.ui.safe.selection.SafeSelectionDialogDirections
import io.gnosis.safe.ui.settings.chain.ChainSelectionMode
import pm.gnosis.utils.asEthereumAddressString
import javax.inject.Inject

/**
 * ViewModel for multichain Safe selection dialog
 * Manages the display and selection of multichain Safes
 */
class MultichainSafeSelectionViewModel @Inject constructor(
    private val multichainSafeRepository: MultichainSafeRepository,
    private val safeRepository: SafeRepository,
    private val multichainMigrationHelper: MultichainMigrationHelper,
    private val settingsHandler: SettingsHandler,
    appDispatchers: AppDispatchers
) : BaseStateViewModel<MultichainSafeSelectionState>(appDispatchers),
    MultichainSafeSelectionAdapter.OnMultichainSafeSelectionItemClickedListener {

    private val items: MutableList<MultichainSafeSelectionViewData> = mutableListOf()
    private var activeMultichainSafe: MultichainSafe? = null

    override fun initialState(): MultichainSafeSelectionState =
        MultichainSafeSelectionState.SafeListState(
            listOf(MultichainSafeSelectionViewData.AddSafeHeader), null, null
        )

    fun loadMultichainSafes() {
        safeLaunch {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "loadMultichainSafes() - starting to load multichain safes")
            }
            
            activeMultichainSafe = multichainSafeRepository.getActiveMultichainSafe()
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "loadMultichainSafes() - active safe: ${activeMultichainSafe?.localName ?: "none"}")
            }

            with(items) {
                clear()
                add(MultichainSafeSelectionViewData.AddSafeHeader)
                
                val multichainSafes = multichainSafeRepository.getMultichainSafes()
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "loadMultichainSafes() - loaded ${multichainSafes.size} multichain safes")
                }
                
                // Add active safe first if it exists
                activeMultichainSafe?.let { active ->
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "loadMultichainSafes() - adding active safe first: ${active.localName}")
                    }
                    add(MultichainSafeSelectionViewData.MultichainSafeItem(active))
                    // Add other safes
                    val otherSafes = multichainSafes.filter { it.address != active.address }
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "loadMultichainSafes() - adding ${otherSafes.size} other safes")
                    }
                    otherSafes.forEach { add(MultichainSafeSelectionViewData.MultichainSafeItem(it)) }
                } ?: run {
                    // No active safe, add all safes
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "loadMultichainSafes() - no active safe, adding all ${multichainSafes.size} safes")
                    }
                    multichainSafes.forEach { 
                        add(MultichainSafeSelectionViewData.MultichainSafeItem(it)) 
                    }
                }
            }

            updateState { 
                MultichainSafeSelectionState.SafeListState(items, activeMultichainSafe, null) 
            }
        }
    }

    private fun selectMultichainSafe(multichainSafe: MultichainSafe) {
        safeLaunch {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "selectMultichainSafe() - selecting multichain safe: ${multichainSafe.localName}")
                Log.d(TAG, "selectMultichainSafe() - address: ${multichainSafe.address.asEthereumAddressString()}")
                Log.d(TAG, "selectMultichainSafe() - chains: ${multichainSafe.chainNames}")
            }
            
            val currentActive = multichainSafeRepository.getActiveMultichainSafe()
            if (currentActive?.address != multichainSafe.address) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "selectMultichainSafe() - setting new active multichain safe")
                }
                
                // Use migration helper for proper bidirectional synchronization
                multichainMigrationHelper.syncMultichainSafeSelection(multichainSafe)
                
                updateState { 
                    MultichainSafeSelectionState.SafeListState(
                        items, 
                        multichainSafe, 
                        ViewAction.CloseScreen
                    ) 
                }
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "selectMultichainSafe() - same safe already selected, just closing dialog")
                }
                
                // Same safe selected, just close
                updateState { 
                    MultichainSafeSelectionState.SafeListState(
                        items, 
                        multichainSafe, 
                        ViewAction.CloseScreen
                    ) 
                }
            }
        }
    }

    override fun onMultichainSafeClicked(multichainSafe: MultichainSafe) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onMultichainSafeClicked() - safe: ${multichainSafe.localName} (${multichainSafe.address.asEthereumAddressString()})")
        }
        selectMultichainSafe(multichainSafe)
    }

    override fun onAddSafeClicked() {
        safeLaunch {
            updateState {
                MultichainSafeSelectionState.AddSafeState(
                    ViewAction.NavigateTo(
                        SafeSelectionDialogDirections.actionSafeSelectionDialogToAddSafe(ChainSelectionMode.ADD_SAFE)
                    )
                )
            }
        }
    }

    fun getMultichainSafeCount(): Int = items.count { it is MultichainSafeSelectionViewData.MultichainSafeItem }
    
    companion object {
        private const val TAG = "MultichainSafeSelectionViewModel"
    }
}

/**
 * States for multichain Safe selection
 */
sealed class MultichainSafeSelectionState : BaseStateViewModel.State {
    data class SafeListState(
        val listItems: List<MultichainSafeSelectionViewData>,
        val activeMultichainSafe: MultichainSafe?,
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : MultichainSafeSelectionState()

    data class AddSafeState(
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : MultichainSafeSelectionState()
}
