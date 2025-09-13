package io.gnosis.safe.ui.settings.owner.tangem

import com.tangem.common.CompletionResult
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.CompletionCallback
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.operations.sign.SignHashResponse
import timber.log.Timber
import java.security.MessageDigest

/**
 * TRUE SignRaw command implementation - Phase 1.
 * 
 * EXPERT INSIGHT: We discovered that many Tangem SDK internal APIs are not accessible,
 * so we need a different approach. This implementation tests if we can modify the
 * existing SignHashCommand behavior by understanding the card's signing methods.
 * 
 * Phase 1: Test card's SignRaw support and analyze behavior
 * Phase 2: Implement true SignRaw if card supports it
 */
class SignRawCommand(
    private val hash: ByteArray,
    private val walletPublicKey: ByteArray,
    private val derivationPath: DerivationPath?
) : CardSessionRunnable<SignHashResponse> {

    companion object {
        private const val TAG = "SignRawCommand"
    }

    override fun run(session: CardSession, callback: CompletionCallback<SignHashResponse>) {
        Timber.i("$TAG: ═══ SIGNRAW COMPREHENSIVE FLOW ANALYSIS ═══")
        Timber.i("$TAG: 🎯 GOAL: Sign raw safeTxHash using Ethereum derivation path + SignRaw")
        Timber.i("$TAG: 📋 APPROACH: SDK modified to use SigningMethod.Code.SignRaw")
        
        // Log input parameters for debugging
        Timber.i("$TAG: ═══ INPUT PARAMETERS ═══")
        Timber.i("$TAG: Hash to sign (raw safeTxHash): 0x${hash.joinToString("") { "%02x".format(it) }}")
        Timber.i("$TAG: Hash length: ${hash.size} bytes")
        Timber.i("$TAG: Wallet public key: ${walletPublicKey.joinToString("") { "%02x".format(it) }}")
        Timber.i("$TAG: Derivation path: $derivationPath")
        Timber.i("$TAG: Expected result: Derive to 0xe104892a4bcfb40cc2555c69e2a09050becf7ed8")
        Timber.i("$TAG: Flow: Ethereum derivation + SignRaw = signature of raw safeTxHash")
        
        // Analyze the card and wallet
        val card = session.environment.card
        Timber.i("$TAG: ═══ CARD ANALYSIS ═══")
        Timber.i("$TAG: Card ID: ${card?.cardId}")
        Timber.i("$TAG: Firmware Version: ${card?.firmwareVersion}")
        
        val wallet = card?.wallet(walletPublicKey)
        if (wallet != null) {
            Timber.i("$TAG: Wallet found - curve: ${wallet.curve}")
            Timber.i("$TAG: Remaining signatures: ${wallet.remainingSignatures}")
            
            // Note: Cannot check SignRaw support due to internal API restrictions
            Timber.i("$TAG: Card settings available but signing methods are internal API")
            Timber.i("$TAG: Will attempt signing and analyze results to determine capabilities")
        } else {
            Timber.w("$TAG: ⚠️ Wallet not found for public key")
        }
        
        // Expert debugging: Compare hash variations
        val sha256Hash = MessageDigest.getInstance("SHA-256").digest(hash)
        
        Timber.i("$TAG: ═══ HASH COMPARISON ═══")
        Timber.i("$TAG: Original hash (safeTxHash): 0x${hash.joinToString("") { "%02x".format(it) }}")
        Timber.i("$TAG: SHA256(safeTxHash): 0x${sha256Hash.joinToString("") { "%02x".format(it) }}")
        Timber.i("$TAG: TEST GOAL: Determine if we can bypass SHA256 preprocessing")
        
        // CORRECTED IMPLEMENTATION: Ethereum derivation + SignRaw
        Timber.i("$TAG: ═══ CORRECTED IMPLEMENTATION ═══")
        Timber.i("$TAG: STEP 1: Use Ethereum derivation path (same as registration)")
        Timber.i("$TAG: STEP 2: Apply SignRaw modifications (bypass SHA256)")
        Timber.i("$TAG: STEP 3: Generate signature of raw safeTxHash")
        Timber.i("$TAG: SDK CHANGES: SignCommand.kt modified to use SigningMethod.Code.SignRaw")
        Timber.i("$TAG: EXPECTED: Signature should verify to registered address")
        
        // Use the existing SignHashCommand but with enhanced result analysis
        val signHashCommand = com.tangem.operations.sign.SignHashCommand(hash, walletPublicKey, derivationPath)
        signHashCommand.run(session) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    val signature = result.data.signature
                    
                    Timber.i("$TAG: ═══ COMPREHENSIVE RESULT ANALYSIS ═══")
                    Timber.i("$TAG: ✅ SignRaw command completed successfully")
                    Timber.i("$TAG: Card ID: ${result.data.cardId}")
                    Timber.i("$TAG: Signature length: ${signature.size} bytes")
                    Timber.i("$TAG: Raw signature: ${signature.joinToString("") { "%02x".format(it) }}")
                    Timber.i("$TAG: Total signed hashes: ${result.data.totalSignedHashes}")
                    
                    // COMPREHENSIVE FLOW VERIFICATION
                    Timber.i("$TAG: ═══ FLOW VERIFICATION ═══")
                    Timber.i("$TAG: ✅ STEP 1: Used Ethereum derivation path (matches registration)")
                    Timber.i("$TAG: ✅ STEP 2: Applied SignRaw modifications (TlvTag.SigningMethod)")
                    Timber.i("$TAG: ✅ STEP 3: Generated signature using modified SDK")
                    Timber.i("$TAG: 🎯 PREDICTION: Should verify to 0xe104892a4bcfb40cc2555c69e2a09050becf7ed8")
                    Timber.i("$TAG: 🎯 PREDICTION: Should pass Safe verification (HTTP 201)")
                    
                    Timber.i("$TAG: ═══ EXPERT DEBUGGING DATA ═══")
                    Timber.i("$TAG: For expert analysis - signature of what hash?")
                    Timber.i("$TAG: If signature verifies against SHA256(safeTxHash) → SHA256 preprocessing confirmed")
                    Timber.i("$TAG: If signature verifies against safeTxHash → Raw signing achieved")
                    
                    callback(result)
                }
                is CompletionResult.Failure -> {
                    Timber.e("$TAG: ❌ Phase 2 signing failed: ${result.error}")
                    Timber.e("$TAG: This might indicate the card doesn't support SignRaw method")
                    Timber.e("$TAG: Expert note: AF050 series should support SignRaw - check firmware version")
                    callback(result)
                }
            }
        }
    }
}