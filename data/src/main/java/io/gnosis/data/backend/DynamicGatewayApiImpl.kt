package io.gnosis.data.backend

import io.gnosis.data.models.Chain
import io.gnosis.data.models.ChainInfo
import io.gnosis.data.models.Page
import io.gnosis.data.models.SafeInfo
import io.gnosis.data.models.RootstockSafeInfo
import io.gnosis.data.models.toSafeInfo
import io.gnosis.data.models.SafeNonces
import io.gnosis.data.models.assets.CoinBalances
import io.gnosis.data.models.assets.Collectible
import io.gnosis.data.models.transaction.*
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType
import java.math.BigInteger
import java.util.*
import retrofit2.HttpException
import okio.Buffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.gnosis.data.models.RootstockBalancesArray
import io.gnosis.data.models.toCoinBalances

/**
 * Implementation of DynamicGatewayApi that routes requests to the appropriate gateway
 * based on the chain ID.
 */
class DynamicGatewayApiImpl(
    private val moshi: Moshi,
    private val client: OkHttpClient
) : DynamicGatewayApi {
    
    // Cache for Retrofit instances to avoid recreating them
    private val retrofitCache = mutableMapOf<String, Retrofit>()
    
    /**
     * Gets the appropriate Retrofit instance for the given chain ID.
     */
    private fun getRetrofitForChain(chainId: BigInteger): Retrofit {
        val gatewayUrl = ChainGatewayMapping.getGatewayUrl(chainId)
        
        return retrofitCache.getOrPut(gatewayUrl) {
            Retrofit.Builder()
                .client(client)
                .baseUrl(gatewayUrl)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
        }
    }
    
    /**
     * Gets the appropriate GatewayApi instance for the given chain ID.
     */
    private fun getGatewayApiForChain(chainId: BigInteger): GatewayApi {
        return getRetrofitForChain(chainId).create(GatewayApi::class.java)
    }
    
    override suspend fun loadSupportedCurrencies(): List<String> {
        // Use default gateway for this call since it's not chain-specific
        val defaultRetrofit = Retrofit.Builder()
            .client(client)
            .baseUrl(ChainGatewayMapping.getGatewayUrl(BigInteger.ZERO))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return defaultRetrofit.create(GatewayApi::class.java).loadSupportedCurrencies()
    }

    override suspend fun getSafeInfo(chainId: BigInteger, address: String): SafeInfo {
        android.util.Log.d("DynamicGatewayApi", "getSafeInfo called for chainId: $chainId, address: $address")
        if (ChainGatewayMapping.useCustomPath(chainId)) {
            val gatewayUrl = ChainGatewayMapping.getGatewayUrl(chainId)
            val customUrl = "${gatewayUrl}api/v1/safes/$address/"
            android.util.Log.d("DynamicGatewayApi", "Using custom path URL: $customUrl")
            val request = Request.Builder().url(customUrl).build()
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            if (!response.isSuccessful) throw HttpException(retrofit2.Response.error<SafeInfo>(response.code(), response.body()!!))
            
            // Check if this is a Rootstock chain (needs special parsing)
            if (chainId == Chain.ID_ROOTSTOCK || chainId == Chain.ID_ROOTSTOCK_TESTNET) {
                android.util.Log.d("DynamicGatewayApi", "Parsing as RootstockSafeInfo for chain: $chainId")
                val rootstockSafeInfo = moshi.adapter(RootstockSafeInfo::class.java).fromJson(response.body()!!.source())!!
                return rootstockSafeInfo.toSafeInfo()
            } else {
                android.util.Log.d("DynamicGatewayApi", "Parsing as standard SafeInfo for chain: $chainId")
                // For other transaction services, parse as standard SafeInfo
                val safeInfo = moshi.adapter(SafeInfo::class.java).fromJson(response.body()!!.source())!!
                return safeInfo
            }
        } else {
            android.util.Log.d("DynamicGatewayApi", "Using standard path for chain: $chainId")
            return getGatewayApiForChain(chainId).getSafeInfo(chainId, address)
        }
    }

    override suspend fun loadBalances(chainId: BigInteger, address: String, fiat: String): CoinBalances {
        android.util.Log.d("DynamicGatewayApi", "loadBalances called for chainId: $chainId, address: $address, fiat: $fiat")
        if (ChainGatewayMapping.useCustomPath(chainId)) {
            val gatewayUrl = ChainGatewayMapping.getGatewayUrl(chainId)
            val customUrl = "${gatewayUrl}api/v1/safes/$address/balances/?currency=$fiat"
            android.util.Log.d("DynamicGatewayApi", "Using custom path balances URL: $customUrl")
            val request = Request.Builder().url(customUrl).build()
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            if (!response.isSuccessful) {
                android.util.Log.w("DynamicGatewayApi", "Balance request failed: ${response.code()}")
                throw HttpException(retrofit2.Response.error<CoinBalances>(response.code(), response.body()!!))
            }
            
            // Check if this is a Rootstock chain (needs special parsing)
            if (chainId == Chain.ID_ROOTSTOCK || chainId == Chain.ID_ROOTSTOCK_TESTNET) {
                android.util.Log.d("DynamicGatewayApi", "Parsing as Rootstock balances for chain: $chainId")
                val rootstockBalances = moshi.adapter<List<io.gnosis.data.models.RootstockBalance>>(Types.newParameterizedType(List::class.java, io.gnosis.data.models.RootstockBalance::class.java)).fromJson(response.body()!!.source())!!
                return rootstockBalances.toCoinBalances(chainId)
            } else {
                android.util.Log.d("DynamicGatewayApi", "Parsing as standard CoinBalances for chain: $chainId")
                // For Base and other transaction services, parse as standard CoinBalances
                val coinBalances = moshi.adapter(CoinBalances::class.java).fromJson(response.body()!!.source())!!
                return coinBalances
            }
        } else {
            android.util.Log.d("DynamicGatewayApi", "Using standard path for balances: $chainId")
            return getGatewayApiForChain(chainId).loadBalances(chainId, address, fiat)
        }
    }

    override suspend fun loadTransactionDetails(chainId: BigInteger, transactionId: String): TransactionDetails {
        return getGatewayApiForChain(chainId).loadTransactionDetails(chainId, transactionId)
    }

    override suspend fun submitConfirmation(
        chainId: BigInteger,
        safeTxHash: String,
        txConfirmationRequest: TransactionConfirmationRequest
    ): TransactionDetails {
        android.util.Log.d("DynamicGatewayApi", "submitConfirmation called for chainId: $chainId, safeTxHash: $safeTxHash")
        if (ChainGatewayMapping.useCustomPath(chainId)) {
            android.util.Log.d("DynamicGatewayApi", "Using custom path for Rootstock confirmation submission")
            val gatewayUrl = ChainGatewayMapping.getGatewayUrl(chainId)
            val customUrl = "${gatewayUrl}api/v1/multisig-transactions/$safeTxHash/confirmations/"
            android.util.Log.d("DynamicGatewayApi", "Custom confirmation URL: $customUrl")
            
            // Convert the request to JSON
            val requestJson = moshi.adapter(TransactionConfirmationRequest::class.java).toJson(txConfirmationRequest)
            val requestBody = RequestBody.create(MediaType.parse("application/json"), requestJson)
            val request = Request.Builder()
                .url(customUrl)
                .post(requestBody)
                .build()
            
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            if (!response.isSuccessful) {
                android.util.Log.w("DynamicGatewayApi", "Confirmation submission request failed: ${response.code()}")
                throw HttpException(retrofit2.Response.error<TransactionDetails>(response.code(), response.body()!!))
            }
            
            // Parse the response as TransactionDetails
            val transactionDetails = moshi.adapter(TransactionDetails::class.java).fromJson(response.body()!!.source())!!
            android.util.Log.d("DynamicGatewayApi", "Successfully parsed Rootstock confirmation submission: $transactionDetails")
            return transactionDetails
        } else {
            android.util.Log.d("DynamicGatewayApi", "Using standard path for confirmation submission: $chainId")
            return getGatewayApiForChain(chainId).submitConfirmation(chainId, safeTxHash, txConfirmationRequest)
        }
    }

    override suspend fun proposeTransaction(
        chainId: BigInteger,
        safeAddress: String,
        multisigTransactionRequest: MultisigTransactionRequest
    ) {
        android.util.Log.d("DynamicGatewayApi", "proposeTransaction called for chainId: $chainId, safeAddress: $safeAddress")
        if (ChainGatewayMapping.useCustomPath(chainId)) {
            android.util.Log.d("DynamicGatewayApi", "Using custom path for Rootstock transaction proposal")
            
            // For Rootstock, we create the transaction and immediately submit confirmation
            // since it's a single-owner safe (threshold=1)
            val safeTxHash = multisigTransactionRequest.safeTxHash
            android.util.Log.d("DynamicGatewayApi", "Rootstock: Submitting confirmation directly for safeTxHash: $safeTxHash")
            
            // Submit the confirmation using the correct endpoint pattern
            val confirmationRequest = TransactionConfirmationRequest(multisigTransactionRequest.signature)
            submitConfirmation(chainId, safeTxHash, confirmationRequest)
            
            android.util.Log.d("DynamicGatewayApi", "✅ Rootstock transaction confirmed and ready for execution")
        } else {
            android.util.Log.d("DynamicGatewayApi", "Using standard path for transaction proposal: $chainId")
            return getGatewayApiForChain(chainId).proposeTransaction(chainId, safeAddress, multisigTransactionRequest)
        }
    }

    override suspend fun loadCollectibles(chainId: BigInteger, safeAddress: String): Page<Collectible> {
        android.util.Log.d("DynamicGatewayApi", "loadCollectibles called for chainId: $chainId, address: $safeAddress")
        if (ChainGatewayMapping.useCustomPath(chainId)) {
            android.util.Log.d("DynamicGatewayApi", "Using custom path for Rootstock collectibles")
            val gatewayUrl = ChainGatewayMapping.getGatewayUrl(chainId)
            val customUrl = "${gatewayUrl}api/v1/safes/$safeAddress/collectibles/"
            android.util.Log.d("DynamicGatewayApi", "Custom collectibles URL: $customUrl")
            val request = Request.Builder().url(customUrl).build()
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            if (!response.isSuccessful) {
                android.util.Log.w("DynamicGatewayApi", "Collectibles request failed: ${response.code()}")
                throw HttpException(retrofit2.Response.error<Page<Collectible>>(response.code(), response.body()!!))
            }
            
            // Parse the response as Page<Collectible>
            val collectiblesPage = moshi.adapter(Page::class.java).fromJson(response.body()!!.source())!!
            android.util.Log.d("DynamicGatewayApi", "Successfully parsed Rootstock collectibles")
            return collectiblesPage as Page<Collectible>
        } else {
            android.util.Log.d("DynamicGatewayApi", "Using standard path for collectibles: $chainId")
            return getGatewayApiForChain(chainId).loadCollectibles(chainId, safeAddress)
        }
    }

    override suspend fun loadCollectiblesPage(pageLink: String): Page<Collectible> {
        // For page links, we need to determine the chain from the URL
        // This is a simplified approach - in practice, you might need to parse the URL
        val defaultRetrofit = Retrofit.Builder()
            .client(client)
            .baseUrl(ChainGatewayMapping.getGatewayUrl(BigInteger.ZERO))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return defaultRetrofit.create(GatewayApi::class.java).loadCollectiblesPage(pageLink)
    }

    override suspend fun loadTransactionsHistory(
        chainId: BigInteger,
        address: String,
        timezoneOffset: Int
    ): Page<TxListEntry> {
        android.util.Log.d("DynamicGatewayApi", "loadTransactionsHistory called for chainId: $chainId, address: $address")
        if (ChainGatewayMapping.useCustomPath(chainId)) {
            android.util.Log.d("DynamicGatewayApi", "Using custom path for Rootstock transaction history")
            val gatewayUrl = ChainGatewayMapping.getGatewayUrl(chainId)
            val customUrl = "${gatewayUrl}api/v1/safes/$address/transactions/history/?timezone_offset=$timezoneOffset"
            android.util.Log.d("DynamicGatewayApi", "Custom transaction history URL: $customUrl")
            val request = Request.Builder().url(customUrl).build()
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            if (!response.isSuccessful) {
                android.util.Log.w("DynamicGatewayApi", "Transaction history request failed: ${response.code()}")
                throw HttpException(retrofit2.Response.error<Page<TxListEntry>>(response.code(), response.body()!!))
            }
            
            // Parse the response as Page<TxListEntry>
            val transactionsPage = moshi.adapter(Page::class.java).fromJson(response.body()!!.source())!!
            android.util.Log.d("DynamicGatewayApi", "Successfully parsed Rootstock transaction history")
            return transactionsPage as Page<TxListEntry>
        } else {
            android.util.Log.d("DynamicGatewayApi", "Using standard path for transaction history: $chainId")
            return getGatewayApiForChain(chainId).loadTransactionsHistory(chainId, address, timezoneOffset)
        }
    }

    override suspend fun loadTransactionsQueue(
        chainId: BigInteger,
        address: String,
        timezoneOffset: Int
    ): Page<TxListEntry> {
        android.util.Log.d("DynamicGatewayApi", "loadTransactionsQueue called for chainId: $chainId, address: $address")
        if (ChainGatewayMapping.useCustomPath(chainId)) {
            android.util.Log.d("DynamicGatewayApi", "Using custom path for Rootstock transaction queue")
            val gatewayUrl = ChainGatewayMapping.getGatewayUrl(chainId)
            val customUrl = "${gatewayUrl}api/v1/safes/$address/transactions/queued/?timezone_offset=$timezoneOffset"
            android.util.Log.d("DynamicGatewayApi", "Custom transaction queue URL: $customUrl")
            val request = Request.Builder().url(customUrl).build()
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            if (!response.isSuccessful) {
                android.util.Log.w("DynamicGatewayApi", "Transaction queue request failed: ${response.code()}")
                throw HttpException(retrofit2.Response.error<Page<TxListEntry>>(response.code(), response.body()!!))
            }
            
            // Parse the response as Page<TxListEntry>
            val transactionsPage = moshi.adapter(Page::class.java).fromJson(response.body()!!.source())!!
            android.util.Log.d("DynamicGatewayApi", "Successfully parsed Rootstock transaction queue")
            return transactionsPage as Page<TxListEntry>
        } else {
            android.util.Log.d("DynamicGatewayApi", "Using standard path for transaction queue: $chainId")
            return getGatewayApiForChain(chainId).loadTransactionsQueue(chainId, address, timezoneOffset)
        }
    }

    override suspend fun loadTransactionsPage(pageLink: String): Page<TxListEntry> {
        // For page links, we need to determine the chain from the URL
        // This is a simplified approach - in practice, you might need to parse the URL
        val defaultRetrofit = Retrofit.Builder()
            .client(client)
            .baseUrl(ChainGatewayMapping.getGatewayUrl(BigInteger.ZERO))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return defaultRetrofit.create(GatewayApi::class.java).loadTransactionsPage(pageLink)
    }

    override suspend fun loadChainInfo(): Page<ChainInfo> {
        // Use default gateway for this call since it's not chain-specific
        val defaultRetrofit = Retrofit.Builder()
            .client(client)
            .baseUrl(ChainGatewayMapping.getGatewayUrl(BigInteger.ZERO))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return defaultRetrofit.create(GatewayApi::class.java).loadChainInfo()
    }

    override suspend fun loadChainInfo(chainId: BigInteger): ChainInfo {
        return getGatewayApiForChain(chainId).loadChainInfo(chainId)
    }

    override suspend fun loadChainInfoPage(pageLink: String): Page<ChainInfo> {
        // For page links, we need to determine the chain from the URL
        // This is a simplified approach - in practice, you might need to parse the URL
        val defaultRetrofit = Retrofit.Builder()
            .client(client)
            .baseUrl(ChainGatewayMapping.getGatewayUrl(BigInteger.ZERO))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return defaultRetrofit.create(GatewayApi::class.java).loadChainInfoPage(pageLink)
    }

    override suspend fun estimateTransaction(
        chainId: BigInteger,
        address: String,
        transactionEstimationRequest: TransactionEstimationRequest
    ): TransactionEstimation {
        android.util.Log.d("DynamicGatewayApi", "estimateTransaction called for chainId: $chainId, address: $address")
        if (ChainGatewayMapping.useCustomPath(chainId)) {
            android.util.Log.d("DynamicGatewayApi", "Using custom path for Rootstock transaction estimation")
            val gatewayUrl = ChainGatewayMapping.getGatewayUrl(chainId)
            val customUrl = "${gatewayUrl}api/v1/safes/$address/multisig-transactions/estimations/"
            android.util.Log.d("DynamicGatewayApi", "Custom estimation URL: $customUrl")
            
            // Convert the request to JSON
            val requestJson = moshi.adapter(TransactionEstimationRequest::class.java).toJson(transactionEstimationRequest)
            val requestBody = okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), requestJson)
            val request = Request.Builder()
                .url(customUrl)
                .post(requestBody)
                .build()
            
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            if (!response.isSuccessful) {
                android.util.Log.w("DynamicGatewayApi", "Transaction estimation request failed: ${response.code()}")
                throw HttpException(retrofit2.Response.error<TransactionEstimation>(response.code(), response.body()!!))
            }
            
            // Parse the response as TransactionEstimation
            val transactionEstimation = moshi.adapter(TransactionEstimation::class.java).fromJson(response.body()!!.source())!!
            android.util.Log.d("DynamicGatewayApi", "Successfully parsed Rootstock transaction estimation: $transactionEstimation")
            return transactionEstimation
        } else {
            android.util.Log.d("DynamicGatewayApi", "Using standard path for transaction estimation: $chainId")
            return getGatewayApiForChain(chainId).estimateTransaction(chainId, address, transactionEstimationRequest)
        }
    }

    override suspend fun loadSafeNonces(chainId: BigInteger, address: String): SafeNonces {
        android.util.Log.d("DynamicGatewayApi", "loadSafeNonces called for chainId: $chainId, address: $address")
        if (ChainGatewayMapping.useCustomPath(chainId)) {
            android.util.Log.d("DynamicGatewayApi", "Using SafeInfo to derive nonces for Rootstock")
            // For Rootstock, we get the nonce from SafeInfo since the dedicated nonces endpoint doesn't exist
            val safeInfo = getSafeInfo(chainId, address)
            val currentNonce = safeInfo.nonce
            val recommendedNonce = currentNonce // In most cases, recommended nonce equals current nonce
            val safeNonces = SafeNonces(currentNonce = currentNonce, recommendedNonce = recommendedNonce)
            android.util.Log.d("DynamicGatewayApi", "Successfully derived Rootstock nonces from SafeInfo: $safeNonces")
            return safeNonces
        } else {
            android.util.Log.d("DynamicGatewayApi", "Using standard path for nonces: $chainId")
            return getGatewayApiForChain(chainId).loadSafeNonces(chainId, address)
        }
    }
}
