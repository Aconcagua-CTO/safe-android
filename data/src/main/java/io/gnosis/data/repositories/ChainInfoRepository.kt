package io.gnosis.data.repositories

import io.gnosis.data.db.daos.ChainDao
import io.gnosis.data.models.Chain
import io.gnosis.data.models.ChainConfiguration
import io.gnosis.data.models.ChainInfo
import io.gnosis.data.models.Page
import io.gnosis.data.models.Safe

class ChainInfoRepository(
    private val chainDao: ChainDao
) {

    suspend fun getSupportedChains(): List<Chain> = ChainConfiguration.getSupportedChains()

    suspend fun getChainInfo(): Page<Chain> {
        val chains = ChainConfiguration.getSupportedChains()
        return Page(
            count = chains.size,
            next = null,
            previous = null,
            results = chains
        )
    }

    suspend fun loadChainInfoPage(pageLink: String?): Page<Chain> = getChainInfo()

    suspend fun updateChainInfo(chains: List<ChainInfo>, safes: List<Safe>) {
        val supportedChains = ChainConfiguration.getSupportedChains()
        val supportedChainIds = supportedChains.map { it.chainId }.toSet()
        
        safes.map { it.chainId }.toSet().forEach { chainId ->
            if (supportedChainIds.contains(chainId)) {
                val chain = supportedChains.find { it.chainId == chainId }
                chain?.let { save(it) }
            }
        }
    }

    suspend fun save(chain: Chain) {
        chainDao.save(chain)
        chainDao.saveCurrency(chain.currency)
    }

    suspend fun getChains(): List<Chain> = chainDao.loadAll()
    
    /**
     * Initialize the database with all supported chains for the current build variant
     */
    suspend fun initializeSupportedChains() {
        val supportedChains = ChainConfiguration.getSupportedChains()
        supportedChains.forEach { chain ->
            save(chain)
        }
    }
}

