package io.gnosis.safe.ui.settings.owner.tangem

import androidx.paging.PagingSource
import pm.gnosis.model.Solidity
import timber.log.Timber

class TangemOwnerPagingSource(
    private val tangemController: TangemController,
    private val cardId: String,
    private val walletPublicKey: ByteArray,
    private val derivationPath: String,
    private val maxPages: Int
) : PagingSource<Long, Solidity.Address>() {

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, Solidity.Address> {
        val pageLink = params.key
        val pageSize = params.loadSize

        kotlin.runCatching {
            pageLink?.let { 
                tangemController.addressesForPage(
                    cardId = cardId,
                    walletPublicKey = walletPublicKey,
                    derivationPath = derivationPath,
                    startIndex = pageLink,
                    pageSize = pageSize
                )
            } ?: tangemController.addressesForPage(
                cardId = cardId,
                walletPublicKey = walletPublicKey,
                derivationPath = derivationPath,
                startIndex = 0,
                pageSize = pageSize
            )
        }.onSuccess {
            return LoadResult.Page(
                data = it,
                prevKey = if (pageLink == null || pageLink == 0L) null else pageLink - pageSize,
                nextKey = if ((pageLink ?: 0) < (maxPages - 1) * pageSize) (pageLink ?: 0) + pageSize else null
            )
        }.onFailure {
            Timber.e(it)
            return LoadResult.Error(it)
        }

        throw IllegalStateException(javaClass.name)
    }
}
