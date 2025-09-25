package io.gnosis.safe.multichain.migration

import android.util.Log
import io.gnosis.data.models.Safe
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.safe.BuildConfig
import io.gnosis.safe.multichain.MultichainFeatureFlag
import io.gnosis.safe.multichain.models.MultichainSafe
import io.gnosis.safe.multichain.repositories.MultichainSafeRepository
import pm.gnosis.utils.asEthereumAddressString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper for migrating between single-chain and multichain Safe management
 * Handles active Safe synchronization and fallback scenarios
 */
@Singleton
class MultichainMigrationHelper @Inject constructor(
    private val multichainFeatureFlag: MultichainFeatureFlag,
    private val safeRepository: SafeRepository,
    private val multichainSafeRepository: MultichainSafeRepository
) {
    
    /**
     * Synchronize active Safe between single-chain and multichain systems
     * This ensures smooth transition when feature flag is toggled
     */
    suspend fun synchronizeActiveSafe() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "synchronizeActiveSafe() - starting active safe synchronization")
        }
        
        val multichainEnabled = multichainFeatureFlag.shouldShowMultichainFeatures()
        
        if (multichainEnabled) {
            migrateToMultichainActive()
        } else {
            migrateToSingleChainActive()
        }
    }
    
    /**
     * Migrate from single-chain active Safe to multichain active Safe
     */
    private suspend fun migrateToMultichainActive() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "migrateToMultichainActive() - migrating to multichain active safe")
        }
        
        val currentSingleChainSafe = safeRepository.getActiveSafe()
        val currentMultichainSafe = multichainSafeRepository.getActiveMultichainSafe()
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "migrateToMultichainActive() - current single-chain safe: ${currentSingleChainSafe?.localName ?: "none"}")
            Log.d(TAG, "migrateToMultichainActive() - current multichain safe: ${currentMultichainSafe?.localName ?: "none"}")
        }
        
        when {
            // Case 1: No multichain safe set, but single-chain safe exists
            currentMultichainSafe == null && currentSingleChainSafe != null -> {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "migrateToMultichainActive() - migrating single-chain safe to multichain")
                }
                
                val multichainSafe = multichainSafeRepository.getMultichainSafeByAddress(currentSingleChainSafe.address)
                if (multichainSafe != null) {
                    multichainSafeRepository.setActiveMultichainSafe(multichainSafe)
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "migrateToMultichainActive() - successfully migrated to multichain safe: ${multichainSafe.localName}")
                    }
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "migrateToMultichainActive() - no multichain safe found for address ${currentSingleChainSafe.address.asEthereumAddressString()}")
                    }
                }
            }
            
            // Case 2: Both exist, verify they match
            currentMultichainSafe != null && currentSingleChainSafe != null -> {
                if (currentMultichainSafe.address != currentSingleChainSafe.address) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "migrateToMultichainActive() - active safes don't match, updating multichain to match single-chain")
                    }
                    
                    val multichainSafe = multichainSafeRepository.getMultichainSafeByAddress(currentSingleChainSafe.address)
                    if (multichainSafe != null) {
                        multichainSafeRepository.setActiveMultichainSafe(multichainSafe)
                    }
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "migrateToMultichainActive() - active safes already synchronized")
                    }
                }
            }
            
            // Case 3: No active safes
            else -> {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "migrateToMultichainActive() - no active safes to migrate")
                }
            }
        }
    }
    
    /**
     * Migrate from multichain active Safe to single-chain active Safe
     */
    private suspend fun migrateToSingleChainActive() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "migrateToSingleChainActive() - migrating to single-chain active safe")
        }
        
        val currentMultichainSafe = multichainSafeRepository.getActiveMultichainSafe()
        val currentSingleChainSafe = safeRepository.getActiveSafe()
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "migrateToSingleChainActive() - current multichain safe: ${currentMultichainSafe?.localName ?: "none"}")
            Log.d(TAG, "migrateToSingleChainActive() - current single-chain safe: ${currentSingleChainSafe?.localName ?: "none"}")
        }
        
        when {
            // Case 1: Multichain safe exists, but no single-chain safe
            currentMultichainSafe != null && currentSingleChainSafe == null -> {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "migrateToSingleChainActive() - selecting default chain for multichain safe")
                }
                
                // Choose the first available chain (could be improved with user preference)
                val defaultSafe = currentMultichainSafe.safesPerChain.values.firstOrNull()
                if (defaultSafe != null) {
                    safeRepository.setActiveSafe(defaultSafe)
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "migrateToSingleChainActive() - set active safe to ${defaultSafe.localName} on ${defaultSafe.chain.name}")
                    }
                }
            }
            
            // Case 2: Both exist, verify multichain contains the single-chain safe
            currentMultichainSafe != null && currentSingleChainSafe != null -> {
                if (currentMultichainSafe.address == currentSingleChainSafe.address) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "migrateToSingleChainActive() - active safes already synchronized")
                    }
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "migrateToSingleChainActive() - active safes don't match, keeping single-chain safe")
                    }
                }
            }
            
            // Case 3: No multichain safe
            else -> {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "migrateToSingleChainActive() - no multichain safe to migrate from")
                }
            }
        }
    }
    
    /**
     * Get migration status information for debugging
     */
    suspend fun getMigrationStatus(): MigrationStatus {
        val singleChainSafe = safeRepository.getActiveSafe()
        val multichainSafe = multichainSafeRepository.getActiveMultichainSafe()
        val multichainEnabled = multichainFeatureFlag.shouldShowMultichainFeatures()
        
        val status = MigrationStatus(
            multichainEnabled = multichainEnabled,
            hasSingleChainActive = singleChainSafe != null,
            hasMultichainActive = multichainSafe != null,
            safesAreSynchronized = singleChainSafe?.address == multichainSafe?.address,
            singleChainSafeName = singleChainSafe?.localName,
            multichainSafeName = multichainSafe?.localName,
            multichainChainCount = multichainSafe?.chainCount ?: 0
        )
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "getMigrationStatus() - migration status:")
            Log.d(TAG, "  - multichain enabled: ${status.multichainEnabled}")
            Log.d(TAG, "  - has single-chain active: ${status.hasSingleChainActive}")
            Log.d(TAG, "  - has multichain active: ${status.hasMultichainActive}")
            Log.d(TAG, "  - safes synchronized: ${status.safesAreSynchronized}")
            Log.d(TAG, "  - single-chain safe: ${status.singleChainSafeName ?: "none"}")
            Log.d(TAG, "  - multichain safe: ${status.multichainSafeName ?: "none"} (${status.multichainChainCount} chains)")
        }
        
        return status
    }
    
    /**
     * Handle feature flag toggle with proper migration
     */
    suspend fun handleFeatureFlagToggle(enabled: Boolean) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "handleFeatureFlagToggle() - toggling multichain to: $enabled")
        }
        
        if (enabled) {
            multichainFeatureFlag.enableMultichainMode()
        } else {
            multichainFeatureFlag.disableMultichainMode()
        }
        
        // Synchronize active safes after toggle
        synchronizeActiveSafe()
        
        if (BuildConfig.DEBUG) {
            val status = getMigrationStatus()
            Log.d(TAG, "handleFeatureFlagToggle() - migration completed, synchronized: ${status.safesAreSynchronized}")
        }
    }
    
    /**
     * Synchronize multichain Safe selection with single-chain system
     * Called when a multichain Safe is selected to update both systems
     */
    suspend fun syncMultichainSafeSelection(multichainSafe: MultichainSafe) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "syncMultichainSafeSelection() - syncing selection: ${multichainSafe.localName}")
            Log.d(TAG, "syncMultichainSafeSelection() - available chains: ${multichainSafe.chainNames}")
        }
        
        // Update multichain active Safe
        multichainSafeRepository.setActiveMultichainSafe(multichainSafe)
        
        // Update single-chain active Safe using Option 1: First Chain
        val firstChainSafe = multichainSafe.safesPerChain.values.first()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "syncMultichainSafeSelection() - setting single-chain active to first chain: ${firstChainSafe.chain.name}")
            Log.d(TAG, "syncMultichainSafeSelection() - first chain safe: ${firstChainSafe.localName} on ${firstChainSafe.chain.name}")
        }
        
        safeRepository.setActiveSafe(firstChainSafe)
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "syncMultichainSafeSelection() - synchronization completed")
        }
    }
    
    /**
     * Synchronize single-chain Safe selection with multichain system
     * Called when a single-chain Safe is selected to update multichain system
     */
    suspend fun syncSingleChainSafeSelection(safe: io.gnosis.data.models.Safe) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "syncSingleChainSafeSelection() - syncing single-chain selection: ${safe.localName}")
            Log.d(TAG, "syncSingleChainSafeSelection() - chain: ${safe.chain.name}")
        }
        
        // Update single-chain active Safe
        safeRepository.setActiveSafe(safe)
        
        // Update multichain active Safe if multichain mode is enabled
        if (multichainFeatureFlag.shouldShowMultichainFeatures()) {
            val multichainSafe = multichainSafeRepository.getMultichainSafeByAddress(safe.address)
            if (multichainSafe != null) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "syncSingleChainSafeSelection() - updating multichain active safe")
                }
                multichainSafeRepository.setActiveMultichainSafe(multichainSafe)
            } else {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "syncSingleChainSafeSelection() - no multichain safe found for address")
                }
            }
        }
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "syncSingleChainSafeSelection() - synchronization completed")
        }
    }
    
    companion object {
        private const val TAG = "MultichainMigrationHelper"
    }
}

/**
 * Status information for multichain migration
 */
data class MigrationStatus(
    val multichainEnabled: Boolean,
    val hasSingleChainActive: Boolean,
    val hasMultichainActive: Boolean,
    val safesAreSynchronized: Boolean,
    val singleChainSafeName: String?,
    val multichainSafeName: String?,
    val multichainChainCount: Int
)
