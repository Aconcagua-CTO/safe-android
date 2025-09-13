package io.gnosis.safe.ui.settings.owner.tangem

import timber.log.Timber

/**
 * Comprehensive logging utility for Tangem operations
 * Provides structured logging for debugging Tangem integration
 */
object TangemLogger {

    private const val TAG_PREFIX = "Tangem"
    private const val SEPARATOR = " | "

    /**
     * Log NFC availability check
     */
    fun logNfcAvailability(available: Boolean, enabled: Boolean) {
        Timber.d("$TAG_PREFIX$SEPARATOR NFC Check$SEPARATOR Available: $available, Enabled: $enabled")
    }

    /**
     * Log card scan operation
     */
    fun logCardScanStarted() {
        Timber.i("$TAG_PREFIX$SEPARATOR Card Scan$SEPARATOR Started")
    }

    fun logCardScanResult(success: Boolean, cardId: String? = null, error: String? = null) {
        if (success) {
            Timber.i("$TAG_PREFIX$SEPARATOR Card Scan$SEPARATOR Success$SEPARATOR CardId: $cardId")
        } else {
            Timber.e("$TAG_PREFIX$SEPARATOR Card Scan$SEPARATOR Failed$SEPARATOR Error: $error")
        }
    }

    /**
     * Log wallet derivation operation
     */
    fun logWalletDerivationStarted(cardId: String, derivationPath: String) {
        Timber.i("$TAG_PREFIX$SEPARATOR Wallet Derivation$SEPARATOR Started$SEPARATOR CardId: $cardId, Path: $derivationPath")
    }

    fun logWalletDerivationResult(success: Boolean, address: String? = null, error: String? = null) {
        if (success) {
            Timber.i("$TAG_PREFIX$SEPARATOR Wallet Derivation$SEPARATOR Success$SEPARATOR Address: $address")
        } else {
            Timber.e("$TAG_PREFIX$SEPARATOR Wallet Derivation$SEPARATOR Failed$SEPARATOR Error: $error")
        }
    }

    /**
     * Log hash signing operation
     */
    fun logHashSigningStarted(cardId: String, hash: String, derivationPath: String) {
        Timber.i("$TAG_PREFIX$SEPARATOR Hash Signing$SEPARATOR Started$SEPARATOR CardId: $cardId, Hash: $hash, Path: $derivationPath")
    }

    fun logHashSigningResult(success: Boolean, signature: String? = null, error: String? = null) {
        if (success) {
            Timber.i("$TAG_PREFIX$SEPARATOR Hash Signing$SEPARATOR Success$SEPARATOR Signature: $signature")
        } else {
            Timber.e("$TAG_PREFIX$SEPARATOR Hash Signing$SEPARATOR Failed$SEPARATOR Error: $error")
        }
    }

    /**
     * Log address derivation for paging
     */
    fun logAddressDerivationStarted(derivationPath: String, startIndex: Long, pageSize: Int) {
        Timber.d("$TAG_PREFIX$SEPARATOR Address Derivation$SEPARATOR Started$SEPARATOR Path: $derivationPath, StartIndex: $startIndex, PageSize: $pageSize")
    }

    fun logAddressDerivationResult(addressCount: Int, error: String? = null) {
        if (error == null) {
            Timber.d("$TAG_PREFIX$SEPARATOR Address Derivation$SEPARATOR Success$SEPARATOR AddressCount: $addressCount")
        } else {
            Timber.e("$TAG_PREFIX$SEPARATOR Address Derivation$SEPARATOR Failed$SEPARATOR Error: $error")
        }
    }

    /**
     * Log UI interactions
     */
    fun logUiInteraction(screen: String, action: String, details: String? = null) {
        val detailsStr = if (details != null) "$SEPARATOR Details: $details" else ""
        Timber.d("$TAG_PREFIX$SEPARATOR UI$SEPARATOR Screen: $screen$SEPARATOR Action: $action$detailsStr")
    }

    /**
     * Log ViewModel operations
     */
    fun logViewModelOperation(operation: String, details: String? = null) {
        val detailsStr = if (details != null) "$SEPARATOR Details: $details" else ""
        Timber.d("$TAG_PREFIX$SEPARATOR ViewModel$SEPARATOR Operation: $operation$detailsStr")
    }

    /**
     * Log state changes
     */
    fun logStateChange(from: String, to: String, reason: String? = null) {
        val reasonStr = if (reason != null) "$SEPARATOR Reason: $reason" else ""
        Timber.d("$TAG_PREFIX$SEPARATOR State$SEPARATOR From: $from$SEPARATOR To: $to$reasonStr")
    }

    /**
     * Log performance metrics
     */
    fun logPerformance(operation: String, durationMs: Long, success: Boolean) {
        val status = if (success) "Success" else "Failed"
        Timber.d("$TAG_PREFIX$SEPARATOR Performance$SEPARATOR Operation: $operation$SEPARATOR Duration: ${durationMs}ms$SEPARATOR Status: $status")
    }

    /**
     * Log error with context
     */
    fun logError(operation: String, error: Throwable, context: String? = null) {
        val contextStr = if (context != null) "$SEPARATOR Context: $context" else ""
        Timber.e(error, "$TAG_PREFIX$SEPARATOR Error$SEPARATOR Operation: $operation$contextStr")
    }

    /**
     * Log security-related events
     */
    fun logSecurityEvent(event: String, details: String? = null) {
        val detailsStr = if (details != null) "$SEPARATOR Details: $details" else ""
        Timber.w("$TAG_PREFIX$SEPARATOR Security$SEPARATOR Event: $event$detailsStr")
    }

    /**
     * Log data validation
     */
    fun logDataValidation(dataType: String, valid: Boolean, details: String? = null) {
        val detailsStr = if (details != null) "$SEPARATOR Details: $details" else ""
        val status = if (valid) "Valid" else "Invalid"
        Timber.d("$TAG_PREFIX$SEPARATOR Validation$SEPARATOR Type: $dataType$SEPARATOR Status: $status$detailsStr")
    }

    /**
     * Log SDK integration events
     */
    fun logSdkEvent(event: String, details: String? = null) {
        val detailsStr = if (details != null) "$SEPARATOR Details: $details" else ""
        Timber.d("$TAG_PREFIX$SEPARATOR SDK$SEPARATOR Event: $event$detailsStr")
    }

    /**
     * Log user actions
     */
    fun logUserAction(action: String, screen: String, details: String? = null) {
        val detailsStr = if (details != null) "$SEPARATOR Details: $details" else ""
        Timber.i("$TAG_PREFIX$SEPARATOR User Action$SEPARATOR Action: $action$SEPARATOR Screen: $screen$detailsStr")
    }
}
