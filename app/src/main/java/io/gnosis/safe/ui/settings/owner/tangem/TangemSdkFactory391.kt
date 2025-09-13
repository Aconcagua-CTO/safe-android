package io.gnosis.safe.ui.settings.owner.tangem

import android.content.Context
import androidx.activity.ComponentActivity
import com.tangem.TangemSdk
import com.tangem.common.core.Config
import com.tangem.sdk.extensions.init
import timber.log.Timber

/**
 * Official TangemSdk factory based on actual 3.9.1 source code analysis
 * Uses the official extension method: TangemSdk.init(ComponentActivity)
 */
object TangemSdkFactory391 {
    
    private const val TAG = "TangemSdkFactory391"
    
    /**
     * Create TangemSdk using the official 3.9.1 extension method
     * Based on: com.tangem.sdk.extensions.init(ComponentActivity)
     */
    fun createOfficialSdk(context: Context): TangemSdk? {
        Timber.d("$TAG: Creating TangemSdk 3.9.1 using official extension method")
        
        return try {
            when (context) {
                is ComponentActivity -> {
                    Timber.d("$TAG: Using ComponentActivity - calling TangemSdk.init(activity)")
                    val sdk = TangemSdk.init(context, Config())
                    Timber.d("$TAG: ✅ TangemSdk 3.9.1 created successfully using official init method")
                    sdk
                }
                else -> {
                    Timber.w("$TAG: Context is not ComponentActivity, SDK initialization may require Activity context")
                    // For now, return null and we'll handle this in the UI layer
                    null
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: ❌ Failed to create TangemSdk 3.9.1: ${e.message}")
            null
        }
    }
}
