package io.gnosis.safe.ui

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.ui.setupWithNavController
import com.google.android.play.core.review.ReviewManagerFactory
import io.gnosis.data.models.Safe
import io.gnosis.data.repositories.CredentialsRepository
import io.gnosis.data.repositories.SafeRepository
import io.gnosis.safe.AppStateListener
import io.gnosis.safe.HeimdallApplication
import io.gnosis.safe.HeimdallIntercom
import io.gnosis.safe.R
import io.gnosis.safe.databinding.ActivityStartBinding
import io.gnosis.safe.databinding.ToolbarSafeOverviewBinding
import io.gnosis.safe.multichain.migration.MultichainMigrationHelper
import io.gnosis.safe.multichain.navigation.MultichainNavigationHelper
import io.gnosis.safe.multichain.repositories.MultichainSafeRepository
import io.gnosis.safe.multichain.services.MultichainBalanceService
import io.gnosis.safe.notifications.NotificationRepository
import io.gnosis.safe.ui.base.SafeOverviewNavigationHandler
import io.gnosis.safe.ui.base.activity.BaseActivity
import io.gnosis.safe.ui.transactions.TransactionsFragmentDirections
import io.gnosis.safe.ui.transactions.TxPagerAdapter
import io.gnosis.safe.ui.updates.UpdatesFragment
import io.gnosis.safe.utils.abbreviateEthAddress
import io.gnosis.safe.utils.dpToPx
import io.gnosis.safe.utils.toColor
import io.intercom.android.sdk.UnreadConversationCountListener
import kotlinx.coroutines.launch
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.visible
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import java.math.BigInteger
import javax.inject.Inject

class StartActivity : BaseActivity(), SafeOverviewNavigationHandler, AppStateListener,
    UnreadConversationCountListener {

    @Inject
    lateinit var safeRepository: SafeRepository

    @Inject
    lateinit var credentialsRepository: CredentialsRepository

    @Inject
    lateinit var notificationRepository: NotificationRepository

    @Inject
    lateinit var multichainNavigationHelper: MultichainNavigationHelper

    @Inject
    lateinit var multichainSafeRepository: MultichainSafeRepository

    @Inject
    lateinit var multichainBalanceService: MultichainBalanceService

    @Inject
    lateinit var multichainMigrationHelper: MultichainMigrationHelper

    private lateinit var navController: NavController

    private val binding by lazy {
        ActivityStartBinding.inflate(layoutInflater)
    }

    private val toolbarBinding by lazy {
        ToolbarSafeOverviewBinding.bind(binding.toolbarContainer.root)
    }

    private val handler = Handler(Looper.getMainLooper())

    var comingFromBackground = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        navController = Navigation.findNavController(this@StartActivity, R.id.nav_host)

        viewComponent().inject(this)

        // Debug: Check Safe status on startup and trigger migration if needed
        lifecycleScope.launch {
            debugSafeStatus()
            
            // Trigger automatic migration if multichain mode is enabled
            if (multichainNavigationHelper.shouldShowMultichainUI()) {
                android.util.Log.d("StartActivity", "onCreate() - multichain mode is ON, triggering automatic migration")
                try {
                    multichainMigrationHelper.synchronizeActiveSafe()
                    android.util.Log.d("StartActivity", "onCreate() - automatic migration completed")
                    
                    // Debug status after migration
                    debugSafeStatus()
                } catch (e: Exception) {
                    android.util.Log.e("StartActivity", "onCreate() - error during automatic migration: ${e.message}", e)
                }
            } else {
                android.util.Log.d("StartActivity", "onCreate() - multichain mode is OFF, no migration needed")
            }
        }

        toolbarBinding.safeSelection.setOnClickListener {
            android.util.Log.d("StartActivity", "Safe selection button clicked!")
            try {
                multichainNavigationHelper.navigateToSafeSelection(navController)
            } catch (e: Exception) {
                android.util.Log.e("StartActivity", "Error in safe selection navigation: ${e.message}", e)
            }
        }
        setupNav()

        handleIntent(intent)

        (application as? HeimdallApplication)?.registerForAppState(this)

        onCountUpdate(HeimdallIntercom.unreadConversationCount())
    }

    override fun onResume() {
        super.onResume()
        HeimdallIntercom.addUnreadConversationCountListener(this)
    }

    override fun onPause() {
        super.onPause()
        HeimdallIntercom.removeUnreadConversationCountListener(this)
    }

    private fun setupPasscode() {
        navController.navigate(R.id.createPasscodeFragment, Bundle().apply {
            putBoolean("ownerImported", false)
        })
        settingsHandler.askForPasscodeSetupOnFirstLaunch = false
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            val chainId =
                it.getSerializableExtra(EXTRA_CHAIN_ID)?.let { it as BigInteger } ?: BigInteger.ZERO
            val safeAddress = it.getStringExtra(EXTRA_SAFE)?.asEthereumAddress()
            val txId = it.getStringExtra(EXTRA_TX_ID)

            safeAddress?.let {
                // Workaround in order to change active safe when push notification for unselected safe is received
                lifecycleScope.launch {
                    val safe = safeRepository.getSafeBy(safeAddress, chainId)
                    safe?.let {
                        safeRepository.setActiveSafe(it)
                        setSafeData(it)

                        if (txId == null) {
                            navController.navigate(R.id.transactionsFragment, Bundle().apply {
                                putInt(
                                    "activeTab",
                                    TxPagerAdapter.Tabs.HISTORY.ordinal
                                ) // open history tab
                            })
                        } else {
                            with(navController) {
                                navigate(R.id.transactionsFragment, Bundle().apply {
                                    putInt(
                                        "activeTab",
                                        TxPagerAdapter.Tabs.QUEUE.ordinal
                                    ) // open queued tab
                                })
                                navigate(
                                    TransactionsFragmentDirections.actionTransactionsFragmentToTransactionDetailsFragment(
                                        it.chain,
                                        txId
                                    )
                                )
                            }
                        }
                    }

                    if (settingsHandler.showUpdateInfo) {
                        askToUpdate()
                    }
                    if (settingsHandler.usePasscode && settingsHandler.requirePasscodeToOpen && comingFromBackground) {
                        askForPasscode()
                        comingFromBackground = false
                    } else {
                        if (settingsHandler.showWhatsNew) {
                            onAppUpdated()
                        }
                    }
                }
            } ?: run {

                if (notificationRepository.intercomPushReceived) {

                    if (settingsHandler.showUpdateInfo) {
                        askToUpdate()
                    }
                    if (settingsHandler.usePasscode && settingsHandler.requirePasscodeToOpen && comingFromBackground) {
                        askForPasscode()
                        comingFromBackground = false
                    } else {
                        handleIntercom()
                    }

                } else {

                    if (!settingsHandler.showUpdateInfo) {
                        settingsHandler.appStartCount++
                    }

                    // do not start rate flow and update screen together
                    when {
                        settingsHandler.showUpdateInfo -> {
                            askToUpdate()
                            if (settingsHandler.requirePasscodeToOpen && settingsHandler.usePasscode && !settingsHandler.updateDeprecated) {
                                askForPasscode()
                            }
                        }
                        settingsHandler.askForPasscodeSetupOnFirstLaunch -> {
                            if (settingsHandler.usePasscode) {
                                settingsHandler.askForPasscodeSetupOnFirstLaunch = false
                                settingsHandler.requirePasscodeToOpen = false
                                settingsHandler.requirePasscodeForConfirmations = true
                                askForPasscode()
                            } else {
                                setupPasscode()
                            }
                        }
                        else -> if (settingsHandler.requirePasscodeToOpen && settingsHandler.usePasscode) {
                            askForPasscode()
                        } else {
                            if (settingsHandler.showWhatsNew) {
                                onAppUpdated()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupNav() {
        with(binding) {
            navBar.setupWithNavController(navController)
            
            // Add additional navigation handling to ensure correct destination
            navBar.setOnItemSelectedListener { item ->
                android.util.Log.d("StartActivity", "Bottom nav item selected: ${item.title} (id: ${item.itemId})")
                
                // Handle navigation manually to ensure it works correctly
                try {
                    when (item.itemId) {
                        R.id.assetsFragment -> {
                            android.util.Log.d("StartActivity", "🎯 User clicked Assets (Activos) - ensuring navigation to Assets Fragment")
                            if (navController.currentDestination?.id != R.id.assetsFragment) {
                                navController.navigate(R.id.assetsFragment)
                            }
                            true
                        }
                        R.id.transactionsFragment -> {
                            android.util.Log.d("StartActivity", "🎯 User clicked Transactions - ensuring navigation to Transactions Fragment")
                            if (navController.currentDestination?.id != R.id.transactionsFragment) {
                                navController.navigate(R.id.transactionsFragment)
                            }
                            true
                        }
                        R.id.settingsFragment -> {
                            android.util.Log.d("StartActivity", "🎯 User clicked Settings - ensuring navigation to Settings Fragment")
                            if (navController.currentDestination?.id != R.id.settingsFragment) {
                                navController.navigate(R.id.settingsFragment)
                            }
                            true
                        }
                        else -> {
                            android.util.Log.w("StartActivity", "⚠️ Unknown navigation item clicked: ${item.itemId}")
                            false
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("StartActivity", "❌ Error during navigation: ${e.message}", e)
                    false
                }
            }
            
            navController.addOnDestinationChangedListener { _, destination, _ ->
                // Add debug logging to track navigation
                android.util.Log.d("StartActivity", "Navigation destination changed to: ${destination.label} (id: ${destination.id})")
                
                when (destination.id) {
                    R.id.assetsFragment -> android.util.Log.d("StartActivity", "✅ Now showing Assets Fragment")
                    R.id.transactionsFragment -> android.util.Log.d("StartActivity", "✅ Now showing Transactions Fragment") 
                    R.id.settingsFragment -> android.util.Log.d("StartActivity", "✅ Now showing Settings Fragment")
                }

                if (destination.id == R.id.assetsFragment || destination.id == R.id.settingsFragment || destination.id == R.id.transactionsFragment) {
                    if (settingsHandler.showWhatsNew) {
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            onAppUpdated()
                        }
                    } else if (settingsHandler.appStartCount >= 3) {
                        startRateFlow()
                    }
                }

                if (destination.id != R.id.enterPasscodeFragment) {
                    handleIntercom()
                }

                if (isFullscreen(destination.id)) {
                    toolbar.visible(false)
                    navBar.visible(false)
                } else {
                    toolbar.visible(true)
                    navBar.visible(true)
                }
            }
        }
    }

    private fun isFullscreen(id: Int?): Boolean =
        id != R.id.assetsFragment &&
                id != R.id.transactionsFragment &&
                id != R.id.settingsFragment &&
                id != R.id.safeSelectionDialog &&
                id != R.id.shareSafeDialog

    override fun setSafeData(safe: Safe?) {
        if (safe == null) {
            setNoSafe()
        } else {
            setSafe(safe)
            checkReadOnly()
        }
    }

    override fun checkSafeReadOnly() {
        checkReadOnly()
    }

    private fun setNoSafe() {
        with(toolbarBinding) {
            safeImage.setOnClickListener(null)
            safeImage.setAddress(null)
            safeImage.setImageResource(R.drawable.ic_no_safe_loaded_36dp)
            safeName.visible(false)
            readOnly.visible(false, View.INVISIBLE)
            safeAddress.text = getString(R.string.no_safes_loaded)
            safeAddress.setOnClickListener(null)
            safeSelection.visible(false)
        }

        with(binding) {
            toolbarShadow.visible(true)
            chainRibbon.visible(false)
        }
    }

    private fun setSafe(safe: Safe) {
        with(toolbarBinding) {
            safeImage.setOnClickListener {
                navigateToShareSafeDialog()
            }
            safeImage.setAddress(safe.address)
            safeName.visible(true)
            safeName.text = safe.localName
            safeName.setOnClickListener {
                navigateToShareSafeDialog()
            }

            adjustSafeNameWidth()

            safeAddress.text = safe.address
                .asEthereumAddressChecksumString()
                .abbreviateEthAddress(
                    if (settingsHandler.chainPrefixPrepend) safe.chain.shortName else null
                )
            safeAddress.setOnClickListener {
                navigateToShareSafeDialog()
            }

            safeSelection.visible(true)
        }

        with(binding) {
            toolbarShadow.visible(false)
            
            // Show multichain information if multichain mode is enabled
            if (multichainNavigationHelper.shouldShowMultichainUI()) {
                updateToolbarForMultichainSafe(safe)
            } else {
                updateToolbarForSingleChainSafe(safe)
            }
        }

        // Test multichain balance aggregation if feature is enabled
        testMultichainBalanceAggregation(safe)
    }

    private fun testMultichainBalanceAggregation(safe: Safe) {
        if (multichainNavigationHelper.shouldUseMultichainAssets()) {
            lifecycleScope.launch {
                try {
                    android.util.Log.d("StartActivity", "testMultichainBalanceAggregation() - testing balance aggregation for ${safe.localName}")
                    
                    val multichainSafe = multichainSafeRepository.getMultichainSafeByAddress(safe.address)
                    if (multichainSafe != null) {
                        android.util.Log.d("StartActivity", "testMultichainBalanceAggregation() - found multichain safe: ${multichainSafe.localName} on ${multichainSafe.chainCount} chains")
                        
                        val balanceData = multichainBalanceService.loadAggregatedBalances(multichainSafe, "USD")
                        
                        android.util.Log.d("StartActivity", "testMultichainBalanceAggregation() - balance aggregation completed!")
                        android.util.Log.d("StartActivity", "testMultichainBalanceAggregation() - total fiat value: ${balanceData.totalFiatValue} USD")
                        android.util.Log.d("StartActivity", "testMultichainBalanceAggregation() - successful chains: ${balanceData.successfulChains.size}")
                        android.util.Log.d("StartActivity", "testMultichainBalanceAggregation() - failed chains: ${balanceData.failedChains.size}")
                        
                        if (balanceData.hasErrors) {
                            balanceData.failedChains.forEach { chain ->
                                android.util.Log.w("StartActivity", "testMultichainBalanceAggregation() - failed to load ${chain.name}")
                            }
                        }
                    } else {
                        android.util.Log.d("StartActivity", "testMultichainBalanceAggregation() - no multichain safe found for address")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("StartActivity", "testMultichainBalanceAggregation() - error: ${e.message}", e)
                }
            }
        }
    }

    private fun updateToolbarForMultichainSafe(safe: Safe) {
        android.util.Log.d("StartActivity", "updateToolbarForMultichainSafe() - updating toolbar for multichain mode")
        
        lifecycleScope.launch {
            try {
                val multichainSafe = multichainSafeRepository.getMultichainSafeByAddress(safe.address)
                if (multichainSafe != null) {
                    android.util.Log.d("StartActivity", "updateToolbarForMultichainSafe() - found multichain safe: ${multichainSafe.localName} on ${multichainSafe.chainCount} chains")
                    
                    binding.chainRibbon.visible(true)
                    binding.chainRibbon.text = "Multichain: ${multichainSafe.chainNames}"
                    binding.chainRibbon.setTextColor(
                        ContextCompat.getColor(applicationContext, R.color.white)
                    )
                    binding.chainRibbon.setBackgroundColor(
                        ContextCompat.getColor(applicationContext, R.color.primary)
                    )
                } else {
                    android.util.Log.d("StartActivity", "updateToolbarForMultichainSafe() - no multichain safe found, falling back to single-chain")
                    updateToolbarForSingleChainSafe(safe)
                }
            } catch (e: Exception) {
                android.util.Log.e("StartActivity", "updateToolbarForMultichainSafe() - error: ${e.message}", e)
                updateToolbarForSingleChainSafe(safe)
            }
        }
    }

    private fun updateToolbarForSingleChainSafe(safe: Safe) {
        android.util.Log.d("StartActivity", "updateToolbarForSingleChainSafe() - updating toolbar for single-chain mode")
        
        binding.chainRibbon.visible(true)
        safe.chain.let {
            binding.chainRibbon.text = it.name
            binding.chainRibbon.setTextColor(
                it.textColor.toColor(
                    applicationContext,
                    R.color.white
                )
            )
            binding.chainRibbon.setBackgroundColor(
                it.backgroundColor.toColor(
                    applicationContext,
                    R.color.primary
                )
            )
        }
    }

    private suspend fun debugSafeStatus() {
        try {
            android.util.Log.d("StartActivity", "debugSafeStatus() - checking Safe status on startup")
            
            // Check single-chain active Safe
            val activeSafe = safeRepository.getActiveSafe()
            android.util.Log.d("StartActivity", "debugSafeStatus() - active single-chain safe: ${activeSafe?.localName ?: "none"}")
            activeSafe?.let { safe ->
                android.util.Log.d("StartActivity", "debugSafeStatus() - single-chain safe address: ${safe.address.asEthereumAddressString()}")
                android.util.Log.d("StartActivity", "debugSafeStatus() - single-chain safe chain: ${safe.chain.name}")
            }
            
            // Check multichain active Safe
            val activeMultichainSafe = multichainSafeRepository.getActiveMultichainSafe()
            android.util.Log.d("StartActivity", "debugSafeStatus() - active multichain safe: ${activeMultichainSafe?.localName ?: "none"}")
            activeMultichainSafe?.let { safe ->
                android.util.Log.d("StartActivity", "debugSafeStatus() - multichain safe address: ${safe.address.asEthereumAddressString()}")
                android.util.Log.d("StartActivity", "debugSafeStatus() - multichain safe chains: ${safe.chainNames}")
            }
            
            // Check all available Safes
            val allSafes = safeRepository.getSafes()
            android.util.Log.d("StartActivity", "debugSafeStatus() - total safes in database: ${allSafes.size}")
            allSafes.forEach { safe ->
                android.util.Log.d("StartActivity", "debugSafeStatus() - safe: ${safe.localName} on ${safe.chain.name} (${safe.address.asEthereumAddressString()})")
            }
            
            // Check multichain Safes
            val multichainSafes = multichainSafeRepository.getMultichainSafes()
            android.util.Log.d("StartActivity", "debugSafeStatus() - total multichain safes: ${multichainSafes.size}")
            multichainSafes.forEach { safe ->
                android.util.Log.d("StartActivity", "debugSafeStatus() - multichain safe: ${safe.localName} on ${safe.chainCount} chains (${safe.address.asEthereumAddressString()})")
            }
            
            // Check migration status
            val migrationStatus = multichainMigrationHelper.getMigrationStatus()
            android.util.Log.d("StartActivity", "debugSafeStatus() - migration synchronized: ${migrationStatus.safesAreSynchronized}")
            
        } catch (e: Exception) {
            android.util.Log.e("StartActivity", "debugSafeStatus() - error: ${e.message}", e)
        }
    }

    private fun adjustSafeNameWidth() {
        with(toolbarBinding) {
            val bounds = Rect()
            safeName.paint.getTextBounds(
                safeName.text.toString(),
                0,
                safeName.text.length,
                bounds
            )
            val safeNameLength = bounds.right - bounds.left

            // wait till views are measured
            safeName.post {
                val readOnlySpace =
                    readOnly.measuredWidth + readOnly.marginLeft + readOnly.marginRight
                if (safeNameLength > safeAddress.measuredWidth - readOnlySpace) {
                    safeName.width = safeAddress.measuredWidth - readOnlySpace
                    safeName.ellipsize = TextUtils.TruncateAt.END
                } else {
                    safeName.ellipsize = null
                    safeName.width = safeNameLength + dpToPx(1)
                }
            }
        }
    }

    private fun checkReadOnly() {
        lifecycleScope.launch {
            // fail silently if safe info cannot be loaded
            kotlin.runCatching {
                val activeSafe = safeRepository.getActiveSafe()
                activeSafe?.let {
                    val safeInfo = safeRepository.getSafeInfo(it)
                    val safeOwners = safeInfo.owners.map { it.value }.toSet()
                    val localOwners = credentialsRepository.owners().map { it.address }.toSet()
                    val signingOwners = safeOwners.intersect(localOwners)
                    toolbarBinding.readOnly.visible(
                        signingOwners.isEmpty(),
                        View.INVISIBLE
                    )
                    safeRepository.setActiveSafeSigningOwners(signingOwners.map {
                        Solidity.Address(
                            it.value
                        )
                    })
                    safeRepository.saveSafe(activeSafe.copy(version = safeInfo.version))
                }
            }.onFailure {
                tracker.logException(it)
                toolbarBinding.readOnly.visible(false, View.INVISIBLE)
            }
        }
    }

    private fun startRateFlow() {
        val manager = ReviewManagerFactory.create(this)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { request ->
            if (request.isSuccessful) {
                // We got the ReviewInfo object
                val reviewInfo = request.result
                val flow = manager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener {
                    // The flow has finished. The API does not indicate whether the user
                    // reviewed or not, or even whether the review dialog was shown. Thus, no
                    // matter the result, we continue our app flow and reset the counter
                    settingsHandler.appStartCount = 0
                }
            }
        }
    }

    private fun askToUpdate() {
        with(navController) {
            if (currentDestination?.id != R.id.updatesFragment) {
                navigate(R.id.updatesFragment, Bundle().apply {
                    putString(
                        "mode",
                        when {
                            settingsHandler.updateDeprecated -> UpdatesFragment.Mode.DEPRECATED.name
                            settingsHandler.updateDeprecatedSoon -> UpdatesFragment.Mode.UPDATE_DEPRECATED_SOON.name
                            else -> UpdatesFragment.Mode.UPDATE_NEW_VERSION.name
                        }
                    )
                })
            }
        }
    }

    private fun onAppUpdated() {
        showWhatsNew()
        settingsHandler.showWhatsNew = false
    }

    private fun showWhatsNew() {
        navController.navigate(R.id.whatsNewDialog)
    }

    private fun navigateToShareSafeDialog() {
        navController.navigate(R.id.shareSafeDialog)
    }

    private fun handleIntercom() {
        if (notificationRepository.intercomPushReceived) {
            HeimdallIntercom.handlePushMessage()
            notificationRepository.intercomPushReceived = false
        }
    }

    // Intercom UnreadConversationCountListener
    override fun onCountUpdate(count: Int) {
        if (count > 0) {
            binding.navBar.getOrCreateBadge(R.id.settingsFragment)
        } else {
            binding.navBar.removeBadge(R.id.settingsFragment)
        }
    }

    /*
       * appInForeground() is triggered when the whole app is resumed from background not only one activity.
       * As it happens when we return from the QR code activity. We want to lock the screen when the app
       * is resumed from background but not when the user returns from the QR code scanner activity.
       */
    override fun appInForeground() {
        if (settingsHandler.requirePasscodeToOpen && settingsHandler.usePasscode && comingFromBackground) {
            askForPasscode()
            // do not reset comingFromBackground here because this method is called before
            // onNewIntent() is called and we need to distinguish between push notifications
            // that were opened in the foreground and the background.
        }
    }

    /*
     * appInBackground() is triggered when the last activity is going into onActivityStopped().
     * See HeimdallApplication.activityLifecycleCallbacks()
     */
    override fun appInBackground() {
        comingFromBackground = true
    }

    private fun askForPasscode() {
        handler.post {
            navigateToPasscodePrompt()
        }
    }

    private fun navigateToPasscodePrompt() {
        with(navController) {
            if (currentDestination?.id != R.id.enterPasscodeFragment) {
                navigate(R.id.enterPasscodeFragment, Bundle().apply {
                    putBoolean("requirePasscodeToOpen", true)
                })
            }
        }
    }

    override fun screenId() = null

    companion object {
        const val EXTRA_CHAIN_ID = "extra.string.chain_id"
        const val EXTRA_SAFE = "extra.string.safe"
        const val EXTRA_TX_ID = "extra.string.tx_id"

        fun createIntent(
            context: Context,
            chainId: BigInteger,
            safeAddress: String,
            txId: String? = null
        ) =
            Intent(context, StartActivity::class.java).apply {
                putExtra(EXTRA_CHAIN_ID, chainId)
                putExtra(EXTRA_SAFE, safeAddress)
                putExtra(EXTRA_TX_ID, txId)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
    }
}
