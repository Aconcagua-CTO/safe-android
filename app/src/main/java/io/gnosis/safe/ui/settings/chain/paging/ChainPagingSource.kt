package io.gnosis.safe.ui.settings.chain.paging

import androidx.paging.PagingSource
import io.gnosis.data.models.Chain
import io.gnosis.data.repositories.ChainInfoRepository
import timber.log.Timber

class ChainPagingSource(
    private val chainInfoRepository: ChainInfoRepository
) : PagingSource<Int, Chain>() {
    
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Chain> {
        return try {
            val page = params.key ?: 0
            val pageSize = params.loadSize
            val allChains = chainInfoRepository.getSupportedChains()
            
            val startIndex = page * pageSize
            val endIndex = minOf(startIndex + pageSize, allChains.size)
            
            if (startIndex >= allChains.size) {
                LoadResult.Page(
                    data = emptyList(),
                    prevKey = if (page > 0) page - 1 else null,
                    nextKey = null
                )
            } else {
                LoadResult.Page(
                    data = allChains.subList(startIndex, endIndex),
                    prevKey = if (page > 0) page - 1 else null,
                    nextKey = if (endIndex < allChains.size) page + 1 else null
                )
            }
        } catch (e: Exception) {
            Timber.e(e)
            LoadResult.Error(e)
        }
    }
}
