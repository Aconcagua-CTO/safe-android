package io.gnosis.safe.multichain.repositories

import android.content.SharedPreferences
import android.util.Log
import io.gnosis.data.models.Chain
import io.gnosis.data.repositories.ChainInfoRepository
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.safe.BuildConfig
import io.gnosis.safe.multichain.models.MultichainSafe
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.PreferencesManager
import pm.gnosis.svalinn.common.utils.edit
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing multichain Safe operations
 * Aggregates individual Safe instances across different chains by their shared address
 */
@Singleton
class MultichainSafeRepository @Inject constructor(
    private val safeRepository: SafeRepository,
    private val chainInfoRepository: ChainInfoRepository,
    private val preferencesManager: PreferencesManager
) {
    
    /**
     * Get all multichain Safes by grouping individual Safes by address
     */
    suspend fun getMultichainSafes(): List<MultichainSafe> {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "getMultichainSafes() - fetching all multichain safes")
        }
        
        val allSafes = safeRepository.getSafes()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "getMultichainSafes() - found ${allSafes.size} individual safes")
        }
        
        val multichainSafes = allSafes
            .groupBy { it.address }
            .map { (address, safes) ->
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "getMultichainSafes() - grouping ${safes.size} safes for address ${address.asEthereumAddressString()}")
                    safes.forEach { safe ->
                        Log.d(TAG, "  - Safe on chain ${safe.chainId} (${safe.chain.name})")
                    }
                }
                MultichainSafe(
                    address = address,
                    localName = safes.first().localName, // Assume same name across chains
                    safesPerChain = safes.associateBy { it.chainId }
                )
            }
            .sortedBy { it.localName }
            
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "getMultichainSafes() - returning ${multichainSafes.size} multichain safes")
            multichainSafes.forEach { multichainSafe ->
                Log.d(TAG, "  - ${multichainSafe.localName}: ${multichainSafe.chainNames}")
            }
        }
        
        return multichainSafes
    }
    
    /**
     * Get a multichain Safe by its address
     */
    suspend fun getMultichainSafeByAddress(address: Solidity.Address): MultichainSafe? {
        val safes = safeRepository.getSafes().filter { it.address == address }
        if (safes.isEmpty()) return null
        
        return MultichainSafe(
            address = address,
            localName = safes.first().localName,
            safesPerChain = safes.associateBy { it.chainId }
        )
    }
    
    /**
     * Get the currently active multichain Safe
     */
    fun getActiveMultichainSafe(): MultichainSafe? {
        val activeAddress = preferencesManager.prefs.getString(ACTIVE_MULTICHAIN_SAFE, null)
            ?.asEthereumAddress()
        return activeAddress?.let { 
            runBlocking { getMultichainSafeByAddress(it) }
        }
    }
    
    /**
     * Set the active multichain Safe
     */
    fun setActiveMultichainSafe(multichainSafe: MultichainSafe) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "setActiveMultichainSafe() - setting active safe: ${multichainSafe.localName} (${multichainSafe.address.asEthereumAddressString()}) on ${multichainSafe.chainCount} chains")
        }
        preferencesManager.prefs.edit {
            putString(ACTIVE_MULTICHAIN_SAFE, multichainSafe.address.asEthereumAddressString())
        }
    }
    
    /**
     * Clear the active multichain Safe
     */
    fun clearActiveMultichainSafe() {
        preferencesManager.prefs.edit {
            remove(ACTIVE_MULTICHAIN_SAFE)
        }
    }
    
    /**
     * Flow that emits the active multichain Safe whenever it changes
     */
    fun activeSafeFlow(): Flow<MultichainSafe?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> 
            if (key == ACTIVE_MULTICHAIN_SAFE) trySend(key) 
        }
        preferencesManager.prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferencesManager.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
        .onStart { emit(ACTIVE_MULTICHAIN_SAFE) }
        .map { getActiveMultichainSafe() }
        .conflate()
    
    /**
     * Check if a multichain Safe exists for the given address
     */
    suspend fun hasMultichainSafe(address: Solidity.Address): Boolean {
        return getMultichainSafeByAddress(address) != null
    }
    
    /**
     * Get the count of multichain Safes
     */
    suspend fun getMultichainSafeCount(): Int {
        return getMultichainSafes().size
    }
    
    /**
     * Get multichain Safes deployed on a specific chain
     */
    suspend fun getMultichainSafesForChain(chainId: java.math.BigInteger): List<MultichainSafe> {
        return getMultichainSafes().filter { it.isDeployedOnChain(chainId) }
    }
    
    /**
     * Get all chains where any Safe is deployed
     */
    suspend fun getAllDeployedChains(): List<Chain> {
        return getMultichainSafes()
            .flatMap { it.deployedChains }
            .distinctBy { it.chainId }
            .sortedBy { it.chainId }
    }
    
    companion object {
        private const val TAG = "MultichainSafeRepository"
        private const val ACTIVE_MULTICHAIN_SAFE = "prefs.string.active_multichain_safe"
    }
}
