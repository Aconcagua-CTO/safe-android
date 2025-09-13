package io.gnosis.safe.ui.settings.owner.tangem

import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.nullOnThrow
import pm.gnosis.utils.toHexString
import timber.log.Timber

object TangemWrapper {

    private const val TAG = "TangemWrapper"

    /**
     * Parse derivation response to get Ethereum address
     * TODO: Implement actual Tangem SDK integration
     */
    fun parseDeriveWallet(deriveResponse: TangemDeriveResponse): Solidity.Address? {
        Timber.d("$TAG: Parsing derive wallet response")
        
        return nullOnThrow {
            val publicKey = deriveResponse.walletPublicKey
            Timber.d("$TAG: Tangem public key: ${publicKey.toHexString()}")
            
            // For Ethereum, we need to derive the address from the public key
            // This is a simplified implementation - in reality you'd need proper ECDSA key handling
            val publicKeyBytes = publicKey
            
            // Take the last 20 bytes as the address (simplified)
            if (publicKeyBytes.size >= 20) {
                val addressBytes = publicKeyBytes.takeLast(20).toByteArray()
                val addressHex = "0x" + addressBytes.toHexString()
                val address = addressHex.asEthereumAddress()
                Timber.d("$TAG: Derived Ethereum address: ${address?.asEthereumAddressChecksumString()}")
                address
            } else {
                Timber.w("$TAG: Public key too short for address derivation: ${publicKeyBytes.size} bytes")
                null
            }
        }
    }

    /**
     * Parse signature response to get ECDSA signature
     * TODO: Implement actual Tangem SDK integration
     */
    fun parseSignHash(signResponse: TangemSignResponse): String? {
        Timber.d("$TAG: Parsing sign hash response")
        
        return nullOnThrow {
            val signature = signResponse.signature
            Timber.d("$TAG: Tangem signature: ${signature.toHexString()}")
            
            // Convert Tangem signature to ECDSA format
            // Tangem typically returns signatures in a specific format
            // This is a simplified implementation
            val result = signature.toHexString()
            Timber.d("$TAG: Converted signature to hex: $result")
            result
        }
    }

    /**
     * Validate derivation path format
     */
    fun isValidDerivationPath(path: String): Boolean {
        Timber.d("$TAG: Validating derivation path: $path")
        
        return try {
            // Basic validation for BIP44 paths like "m/44'/60'/0'/0/0"
            val parts = path.split("/")
            if (parts.size < 2 || parts[0] != "m") {
                Timber.w("$TAG: Invalid derivation path format - must start with 'm' and have at least 2 parts")
                return false
            }
            
            parts.drop(1).forEach { part ->
                val cleanPart = part.replace("'", "")
                cleanPart.toInt()
            }
            
            Timber.d("$TAG: Derivation path is valid")
            true
        } catch (e: Exception) {
            Timber.w("$TAG: Invalid derivation path - exception: ${e.message}")
            false
        }
    }

    /**
     * Get default Ethereum derivation path
     */
    fun getDefaultEthereumPath(accountIndex: Int = 0, addressIndex: Int = 0): String {
        val path = "m/44'/60'/$accountIndex'/0/$addressIndex"
        Timber.d("$TAG: Generated default Ethereum path: $path")
        return path
    }

    /**
     * Extract card ID from card info
     * TODO: Implement actual Tangem SDK integration
     */
    fun getCardId(cardInfo: TangemCardInfo): String {
        Timber.d("$TAG: Extracting card ID: ${cardInfo.cardId}")
        return cardInfo.cardId
    }

    /**
     * Check if card supports Ethereum
     * TODO: Implement actual Tangem SDK integration
     */
    fun supportsEthereum(cardInfo: TangemCardInfo): Boolean {
        // Check if the card supports the required curves and features for Ethereum
        val supports = cardInfo.supportedCurves.contains("secp256k1")
        Timber.d("$TAG: Card supports Ethereum: $supports, supported curves: ${cardInfo.supportedCurves}")
        return supports
    }

    /**
     * Get wallet public key from card info
     * TODO: Implement actual Tangem SDK integration
     */
    fun getWalletPublicKey(cardInfo: TangemCardInfo): ByteArray? {
        val publicKey = cardInfo.wallet?.publicKey
        if (publicKey != null) {
            Timber.d("$TAG: Retrieved wallet public key: ${publicKey.toHexString()}")
        } else {
            Timber.w("$TAG: No wallet public key found in card info")
        }
        return publicKey
    }
}