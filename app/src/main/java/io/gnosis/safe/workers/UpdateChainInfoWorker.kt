package io.gnosis.safe.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.gnosis.data.repositories.ChainInfoRepository
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.safe.Tracker

class UpdateChainInfoWorker(
    private val safeRepository: SafeRepository,
    private val chainInfoRepository: ChainInfoRepository,
    private val tracker: Tracker,
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Initialize with build variant specific chains
            chainInfoRepository.initializeSupportedChains()
            
            // Update chain info for existing safes
            val safes = safeRepository.getSafes()
            chainInfoRepository.updateChainInfo(emptyList(), safes)
            
            Result.success()
        } catch (e: Exception) {
            tracker.logException(e)
            Result.failure()
        }
    }
}
