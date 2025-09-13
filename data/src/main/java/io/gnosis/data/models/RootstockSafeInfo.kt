package io.gnosis.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import pm.gnosis.utils.asEthereumAddress
import java.math.BigInteger

/**
 * SafeInfo model for Rootstock API which has a different JSON structure.
 * The Rootstock API returns address as a string instead of an AddressInfo object.
 */
@JsonClass(generateAdapter = true)
data class RootstockSafeInfo(
    @Json(name = "address") val address: String, // String instead of AddressInfo
    @Json(name = "nonce") val nonce: BigInteger,
    @Json(name = "threshold") val threshold: Int,
    @Json(name = "owners") val owners: List<String>, // List of strings instead of AddressInfo
    @Json(name = "masterCopy") val masterCopy: String, // String instead of AddressInfo
    @Json(name = "modules") val modules: List<String>?, // List of strings instead of AddressInfo
    @Json(name = "fallbackHandler") val fallbackHandler: String?, // String instead of AddressInfo
    @Json(name = "guard") val guard: String?, // String instead of AddressInfo
    @Json(name = "version") val version: String
    // Note: Rootstock API doesn't have implementationVersionState field
)

/**
 * Extension function to convert RootstockSafeInfo to standard SafeInfo
 */
fun RootstockSafeInfo.toSafeInfo(): SafeInfo {
    return SafeInfo(
        address = AddressInfo(
            value = address.asEthereumAddress()!!,
            name = null,
            logoUri = null
        ),
        nonce = nonce,
        threshold = threshold,
        owners = owners.map { ownerAddress ->
            AddressInfo(
                value = ownerAddress.asEthereumAddress()!!,
                name = null,
                logoUri = null
            )
        },
        implementation = AddressInfo(
            value = masterCopy.asEthereumAddress()!!,
            name = null,
            logoUri = null
        ),
        modules = modules?.map { moduleAddress ->
            AddressInfo(
                value = moduleAddress.asEthereumAddress()!!,
                name = null,
                logoUri = null
            )
        },
        fallbackHandler = fallbackHandler?.let { handlerAddress ->
            AddressInfo(
                value = handlerAddress.asEthereumAddress()!!,
                name = null,
                logoUri = null
            )
        },
        guard = guard?.let { guardAddress ->
            AddressInfo(
                value = guardAddress.asEthereumAddress()!!,
                name = null,
                logoUri = null
            )
        },
        version = version,
        versionState = VersionState.UNKNOWN // Default since Rootstock API doesn't provide this
    )
}
