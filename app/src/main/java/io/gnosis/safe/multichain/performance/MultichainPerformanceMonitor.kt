package io.gnosis.safe.multichain.performance

import android.util.Log
import io.gnosis.data.models.Chain
import io.gnosis.safe.BuildConfig
import io.gnosis.safe.multichain.models.MultichainSafe
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performance monitoring for multichain operations
 * Tracks timing, success rates, and performance metrics
 */
@Singleton
class MultichainPerformanceMonitor @Inject constructor() {
    
    private val operationMetrics = mutableMapOf<String, OperationMetrics>()
    
    /**
     * Start timing an operation
     */
    fun startOperation(operationId: String, operationType: String, details: Map<String, Any> = emptyMap()): Long {
        val startTime = System.currentTimeMillis()
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "startOperation() - starting $operationType (id: $operationId)")
            details.forEach { (key, value) ->
                Log.d(TAG, "startOperation() - $key: $value")
            }
        }
        
        return startTime
    }
    
    /**
     * End timing an operation and record metrics
     */
    fun endOperation(
        operationId: String,
        operationType: String,
        startTime: Long,
        success: Boolean,
        details: Map<String, Any> = emptyMap()
    ) {
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "endOperation() - completed $operationType (id: $operationId)")
            Log.d(TAG, "endOperation() - duration: ${duration}ms")
            Log.d(TAG, "endOperation() - success: $success")
            details.forEach { (key, value) ->
                Log.d(TAG, "endOperation() - $key: $value")
            }
        }
        
        // Record metrics
        val metrics = operationMetrics.getOrPut(operationType) { OperationMetrics(operationType) }
        metrics.recordOperation(duration, success)
        
        // Log performance warnings for slow operations
        when (operationType) {
            "balance_aggregation" -> {
                if (duration > 5000) { // > 5 seconds
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "endOperation() - SLOW balance aggregation: ${duration}ms")
                    }
                }
            }
            "safe_selection" -> {
                if (duration > 1000) { // > 1 second
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "endOperation() - SLOW safe selection: ${duration}ms")
                    }
                }
            }
        }
    }
    
    /**
     * Monitor balance aggregation performance
     */
    fun monitorBalanceAggregation(
        multichainSafe: MultichainSafe,
        chainCount: Int,
        fiatCode: String
    ): String {
        val operationId = "balance_${multichainSafe.address.toString().takeLast(8)}_${System.currentTimeMillis()}"
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "monitorBalanceAggregation() - starting balance aggregation monitoring")
            Log.d(TAG, "monitorBalanceAggregation() - safe: ${multichainSafe.localName}")
            Log.d(TAG, "monitorBalanceAggregation() - chains: $chainCount")
            Log.d(TAG, "monitorBalanceAggregation() - fiat: $fiatCode")
            Log.d(TAG, "monitorBalanceAggregation() - operation id: $operationId")
        }
        
        return operationId
    }
    
    /**
     * Monitor individual chain performance
     */
    fun monitorChainOperation(chain: Chain, operation: String): String {
        val operationId = "chain_${chain.chainId}_${operation}_${System.currentTimeMillis()}"
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "monitorChainOperation() - monitoring ${operation} for ${chain.name}")
            Log.d(TAG, "monitorChainOperation() - chain id: ${chain.chainId}")
            Log.d(TAG, "monitorChainOperation() - operation id: $operationId")
        }
        
        return operationId
    }
    
    /**
     * Record chain-specific performance
     */
    fun recordChainPerformance(
        operationId: String,
        chain: Chain,
        startTime: Long,
        success: Boolean,
        errorMessage: String? = null
    ) {
        val duration = System.currentTimeMillis() - startTime
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "recordChainPerformance() - chain: ${chain.name}")
            Log.d(TAG, "recordChainPerformance() - duration: ${duration}ms")
            Log.d(TAG, "recordChainPerformance() - success: $success")
            
            if (!success && errorMessage != null) {
                Log.w(TAG, "recordChainPerformance() - error: $errorMessage")
            }
        }
        
        // Track slow chains
        if (duration > 3000) { // > 3 seconds
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "recordChainPerformance() - SLOW CHAIN: ${chain.name} took ${duration}ms")
            }
        }
    }
    
    /**
     * Get performance summary for debugging
     */
    fun getPerformanceSummary(): PerformanceSummary {
        val summary = PerformanceSummary(
            totalOperations = operationMetrics.values.sumOf { it.totalOperations },
            successfulOperations = operationMetrics.values.sumOf { it.successfulOperations },
            averageDuration = operationMetrics.values.map { it.averageDuration }.average().takeIf { !it.isNaN() } ?: 0.0,
            operationBreakdown = operationMetrics.toMap()
        )
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "getPerformanceSummary() - performance summary:")
            Log.d(TAG, "  - total operations: ${summary.totalOperations}")
            Log.d(TAG, "  - successful operations: ${summary.successfulOperations}")
            Log.d(TAG, "  - success rate: ${summary.successRate}%")
            Log.d(TAG, "  - average duration: ${summary.averageDuration}ms")
        }
        
        return summary
    }
    
    /**
     * Reset performance metrics
     */
    fun resetMetrics() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "resetMetrics() - clearing all performance metrics")
        }
        operationMetrics.clear()
    }
    
    companion object {
        private const val TAG = "MultichainPerformanceMonitor"
    }
}

/**
 * Metrics for a specific operation type
 */
data class OperationMetrics(
    val operationType: String,
    var totalOperations: Int = 0,
    var successfulOperations: Int = 0,
    var totalDuration: Long = 0
) {
    val averageDuration: Double get() = if (totalOperations > 0) totalDuration.toDouble() / totalOperations else 0.0
    val successRate: Double get() = if (totalOperations > 0) (successfulOperations.toDouble() / totalOperations) * 100 else 0.0
    
    fun recordOperation(duration: Long, success: Boolean) {
        totalOperations++
        totalDuration += duration
        if (success) successfulOperations++
    }
}

/**
 * Overall performance summary
 */
data class PerformanceSummary(
    val totalOperations: Int,
    val successfulOperations: Int,
    val averageDuration: Double,
    val operationBreakdown: Map<String, OperationMetrics>
) {
    val successRate: Double get() = if (totalOperations > 0) (successfulOperations.toDouble() / totalOperations) * 100 else 0.0
}
