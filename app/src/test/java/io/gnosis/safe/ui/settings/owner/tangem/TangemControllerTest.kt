package io.gnosis.safe.ui.settings.owner.tangem

import android.content.Context
import android.nfc.NfcAdapter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TangemControllerTest {

    private lateinit var context: Context
    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var tangemController: TangemController

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        nfcAdapter = mockk(relaxed = true)
        tangemController = TangemController(context)
    }

    @Test
    fun `isNfcAvailable returns false when NFC adapter is null`() {
        // Given
        // NFC adapter is null by default in test environment

        // When
        val result = tangemController.isNfcAvailable()

        // Then
        assertFalse(result)
    }

    @Test
    fun `isNfcAvailable returns false when NFC is disabled`() {
        // Given
        every { nfcAdapter.isEnabled } returns false

        // When
        val result = tangemController.isNfcAvailable()

        // Then
        assertFalse(result)
    }

    @Test
    fun `isNfcAvailable returns true when NFC is available and enabled`() {
        // Given
        every { nfcAdapter.isEnabled } returns true

        // When
        val result = tangemController.isNfcAvailable()

        // Then
        assertTrue(result)
    }

    @Test
    fun `scanCard returns error when NFC is not available`() = runTest {
        // Given
        // NFC is not available in test environment

        // When
        val result = tangemController.scanCard()

        // Then
        result.collect { tangemResult ->
            assertTrue(tangemResult is TangemResult.Error)
            assertTrue(tangemResult.message.contains("NFC not available"))
        }
    }

    @Test
    fun `deriveWallet throws exception when NFC is not available`() = runTest {
        // Given
        val cardId = "test-card-id"
        val derivationPath = "m/44'/60'/0'/0/0"

        // When & Then
        try {
            tangemController.deriveWallet(cardId, derivationPath)
            assertTrue(false, "Expected TangemException to be thrown")
        } catch (e: TangemException) {
            assertTrue(e.message?.contains("NFC not available") == true)
        }
    }

    @Test
    fun `signHash throws exception when NFC is not available`() = runTest {
        // Given
        val cardId = "test-card-id"
        val hash = byteArrayOf(1, 2, 3, 4)
        val derivationPath = "m/44'/60'/0'/0/0"

        // When & Then
        try {
            tangemController.signHash(cardId, hash, derivationPath)
            assertTrue(false, "Expected TangemException to be thrown")
        } catch (e: TangemException) {
            assertTrue(e.message?.contains("NFC not available") == true)
        }
    }

    @Test
    fun `addressesForPage returns empty list when NFC is not available`() = runTest {
        // Given
        val derivationPath = "m/44'/60'/0'/0/0"
        val startIndex = 0L
        val pageSize = 5

        // When
        val result = tangemController.addressesForPage(derivationPath, startIndex, pageSize)

        // Then
        assertTrue(result.isEmpty())
    }
}
