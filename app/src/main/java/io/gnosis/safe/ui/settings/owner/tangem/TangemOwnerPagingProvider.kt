package io.gnosis.safe.ui.settings.owner.tangem

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import pm.gnosis.model.Solidity

class TangemOwnerPagingProvider(
    private val tangemController: TangemController
) {

    fun getOwnersStream(
        cardId: String,
        walletPublicKey: ByteArray,
        derivationPath: String
    ): Flow<PagingData<Solidity.Address>> {
        return Pager(
            initialKey = 0,
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = 1,
                enablePlaceholders = false,
                initialLoadSize = PAGE_SIZE,
                maxSize = PAGE_SIZE * MAX_PAGES
            ),
            pagingSourceFactory = {
                TangemOwnerPagingSource(
                    tangemController = tangemController,
                    cardId = cardId,
                    walletPublicKey = walletPublicKey,
                    derivationPath = derivationPath,
                    maxPages = MAX_PAGES
                )
            }
        ).flow
    }

    companion object {
        const val PAGE_SIZE = 5
        const val MAX_PAGES = 20
    }
}
