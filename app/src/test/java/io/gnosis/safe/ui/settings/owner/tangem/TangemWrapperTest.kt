package io.gnosis.safe.ui.settings.owner.tangem

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TangemWrapperTest {

    @Test
    fun `isValidDerivationPath returns true for valid BIP44 path`() {
        // Given
        val validPath = "m/44'/60'/0'/0/0"

        // When
        val result = TangemWrapper.isValidDerivationPath(validPath)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isValidDerivationPath returns false for invalid path format`() {
        // Given
        val invalidPaths = listOf(
            "44'/60'/0'/0/0", // Missing 'm' prefix
            "m/44'/60'/0'/0", // Too few parts
            "m/44'/60'/0'/0/0/0", // Too many parts
            "m/44'/60'/invalid'/0/0", // Non-numeric part
            "", // Empty string
            "invalid" // Completely invalid
        )

        // When & Then
        invalidPaths.forEach { path ->
            assertFalse(TangemWrapper.isValidDerivationPath(path), "Path '$path' should be invalid")
        }
    }

    @Test
    fun `getDefaultEthereumPath returns correct path format`() {
        // Given
        val accountIndex = 1
        val addressIndex = 5

        // When
        val result = TangemWrapper.getDefaultEthereumPath(accountIndex, addressIndex)

        // Then
        assertEquals("m/44'/60'/$accountIndex'/0/$addressIndex", result)
    }

    @Test
    fun `getDefaultEthereumPath uses default values when not specified`() {
        // When
        val result = TangemWrapper.getDefaultEthereumPath()

        // Then
        assertEquals("m/44'/60'/0'/0/0", result)
    }

    @Test
    fun `getCardId returns card ID from card info`() {
        // Given
        val cardId = "test-card-123"
        val cardInfo = TangemCardInfo(
            cardId = cardId,
            supportedCurves = listOf("secp256k1"),
            wallet = null
        )

        // When
        val result = TangemWrapper.getCardId(cardInfo)

        // Then
        assertEquals(cardId, result)
    }

    @Test
    fun `supportsEthereum returns true when card supports secp256k1`() {
        // Given
        val cardInfo = TangemCardInfo(
            cardId = "test-card",
            supportedCurves = listOf("secp256k1", "ed25519"),
            wallet = null
        )

        // When
        val result = TangemWrapper.supportsEthereum(cardInfo)

        // Then
        assertTrue(result)
    }

    @Test
    fun `supportsEthereum returns false when card does not support secp256k1`() {
        // Given
        val cardInfo = TangemCardInfo(
            cardId = "test-card",
            supportedCurves = listOf("ed25519"),
            wallet = null
        )

        // When
        val result = TangemWrapper.supportsEthereum(cardInfo)

        // Then
        assertFalse(result)
    }

    @Test
    fun `getWalletPublicKey returns public key when wallet exists`() {
        // Given
        val publicKey = byteArrayOf(1, 2, 3, 4, 5)
        val wallet = TangemWallet(publicKey)
        val cardInfo = TangemCardInfo(
            cardId = "test-card",
            supportedCurves = listOf("secp256k1"),
            wallet = wallet
        )

        // When
        val result = TangemWrapper.getWalletPublicKey(cardInfo)

        // Then
        assertEquals(publicKey, result)
    }

    @Test
    fun `getWalletPublicKey returns null when wallet is null`() {
        // Given
        val cardInfo = TangemCardInfo(
            cardId = "test-card",
            supportedCurves = listOf("secp256k1"),
            wallet = null
        )

        // When
        val result = TangemWrapper.getWalletPublicKey(cardInfo)

        // Then
        assertNull(result)
    }

    @Test
    fun `parseDeriveWallet returns null for short public key`() {
        // Given
        val shortPublicKey = byteArrayOf(1, 2, 3) // Less than 20 bytes
        val deriveResponse = TangemDeriveResponse(shortPublicKey)

        // When
        val result = TangemWrapper.parseDeriveWallet(deriveResponse)

        // Then
        assertNull(result)
    }

    @Test
    fun `parseSignHash returns hex string for valid signature`() {
        // Given
        val signature = byteArrayOf(1, 2, 3, 4, 5)
        val signResponse = TangemSignResponse(signature)

        // When
        val result = TangemWrapper.parseSignHash(signResponse)

        // Then
        assertEquals("0102030405", result)
    }
}
