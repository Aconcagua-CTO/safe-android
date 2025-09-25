# Tangem Wallet Integration - Implementation Plan

## Overview

This document provides a detailed, step-by-step implementation plan for integrating Tangem hardware wallet support into the Safe Android app, following the established patterns used for Ledger and Keystone wallets.

## Prerequisites

- Android Studio with NFC-enabled device/emulator
- Tangem SDK documentation and API keys
- Understanding of existing Ledger/Keystone integration patterns
- Access to Tangem cards for testing

---

## Phase 1: Foundation Setup

### 1.1 Add Tangem SDK Dependency

**File: `buildsystem/versions.gradle`**

```gradle
// Add to versions map
tangem: '3.0.0', // Use latest stable version
```

**File: `app/build.gradle`**

```gradle
// Add to dependencies section
implementation "com.tangem:tangem-sdk-android:$versions.tangem"
```

### 1.2 Add NFC Permissions

**File: `app/src/main/AndroidManifest.xml`**

```xml
<!-- Add NFC permissions -->
<uses-permission android:name="android.permission.NFC" />
<uses-feature android:name="android.hardware.nfc" android:required="true" />

<!-- Add NFC intent filter to MainActivity -->
<activity android:name=".ui.MainActivity">
    <intent-filter>
        <action android:name="android.nfc.action.TECH_DISCOVERED" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <meta-data android:name="android.nfc.action.TECH_DISCOVERED"
               android:resource="@xml/nfc_tech_filter" />
</activity>
```

**File: `app/src/main/res/xml/nfc_tech_filter.xml` (Create new)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
    <tech-list>
        <tech>android.nfc.tech.IsoDep</tech>
    </tech-list>
</resources>
```

### 1.3 Update Owner Model

**File: `data/src/main/java/io/gnosis/data/models/Owner.kt`**

```kotlin
enum class Type(val value: Int) {
    IMPORTED(0),
    GENERATED(1),
    LEDGER_NANO_X(2),
    KEYSTONE(3),
    TANGEM(4); // Add Tangem type

    companion object {
        fun get(value: Int) = when (value) {
            0 -> IMPORTED
            1 -> GENERATED
            2 -> LEDGER_NANO_X
            3 -> KEYSTONE
            4 -> TANGEM // Add Tangem case
            else -> IMPORTED
        }
    }
}
```

### 1.4 Update Credentials Repository

**File: `data/src/main/java/io/gnosis/data/repositories/CredentialsRepository.kt`**

```kotlin
// Add new method after saveKeystoneOwner()
suspend fun saveTangemOwner(
    address: Solidity.Address,
    name: String? = null,
    cardId: String,
    derivationPath: String
) {
    val owner = Owner(
        address = address,
        name = name,
        type = Owner.Type.TANGEM,
        keyDerivationPath = derivationPath,
        sourceFingerprint = cardId // Use cardId as fingerprint
    )
    ownerDao.save(owner)
}

// Update ownerCount method to include TANGEM
suspend fun ownerCount(ownerType: Owner.Type? = null): Int {
    return when {
        ownerType == Owner.Type.IMPORTED ||
                ownerType == Owner.Type.GENERATED ||
                ownerType == Owner.Type.LEDGER_NANO_X ||
                ownerType == Owner.Type.KEYSTONE ||
                ownerType == Owner.Type.TANGEM -> { // Add TANGEM
            ownerDao.ownerCountForType(OwnerTypeConverter().toValue(ownerType))
        }
        else -> {
            ownerDao.ownerCount()
        }
    }
}
```

---

## Phase 2: Core Integration

### 2.1 Create Tangem Controller

**File: `app/src/main/java/io/gnosis/safe/ui/settings/owner/tangem/TangemController.kt` (Create new)**

```kotlin
package io.gnosis.safe.ui.settings.owner.tangem

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import com.tangem.TangemSdk
import com.tangem.card.Card
import com.tangem.commands.ScanTask
import com.tangem.commands.SignHashTask
import com.tangem.commands.DeriveWalletPublicKeyTask
import com.tangem.common.CompletionResult
import com.tangem.common.core.TangemSdkError
import kotlinx.coroutines.suspendCancellableCoroutine
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TangemController(private val context: Context) {

    private val tangemSdk = TangemSdk()
    private var nfcAdapter: NfcAdapter? = null

    init {
        nfcAdapter = NfcAdapter.getDefaultAdapter(context)
    }

    fun isNfcAvailable(): Boolean {
        return nfcAdapter?.isEnabled == true
    }

    suspend fun scanCard(): Card = suspendCancellableCoroutine { continuation ->
        val scanTask = ScanTask()
        tangemSdk.startSession(scanTask) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    Timber.d("Card scanned successfully: ${result.data.cardId}")
                    continuation.resume(result.data)
                }
                is CompletionResult.Failure -> {
                    Timber.e("Card scan failed: ${result.error}")
                    continuation.resumeWithException(TangemException(result.error))
                }
            }
        }
    }

    suspend fun getAddress(card: Card, derivationPath: String): Solidity.Address = suspendCancellableCoroutine { continuation ->
        val deriveTask = DeriveWalletPublicKeyTask(
            walletPublicKey = card.wallets.first().publicKey,
            derivationPath = derivationPath
        )

        tangemSdk.startSession(deriveTask) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    val publicKey = result.data.publicKey
                    val address = deriveEthereumAddress(publicKey)
                    Timber.d("Derived address: ${address.asEthereumAddressString()}")
                    continuation.resume(address)
                }
                is CompletionResult.Failure -> {
                    Timber.e("Address derivation failed: ${result.error}")
                    continuation.resumeWithException(TangemException(result.error))
                }
            }
        }
    }

    suspend fun signTransaction(card: Card, hash: String): String = suspendCancellableCoroutine { continuation ->
        val signTask = SignHashTask(
            walletPublicKey = card.wallets.first().publicKey,
            hash = hash
        )

        tangemSdk.startSession(signTask) { result ->
            when (result) {
                is CompletionResult.Success -> {
                    val signature = result.data.signature
                    Timber.d("Transaction signed successfully")
                    continuation.resume(signature)
                }
                is CompletionResult.Failure -> {
                    Timber.e("Transaction signing failed: ${result.error}")
                    continuation.resumeWithException(TangemException(result.error))
                }
            }
        }
    }

    private fun deriveEthereumAddress(publicKey: ByteArray): Solidity.Address {
        // Implement Ethereum address derivation from public key
        // This should use the same logic as other wallet types
        val keyPair = KeyPair.fromPublicOnly(publicKey)
        return Solidity.Address(keyPair.address.asBigInteger())
    }

    fun teardownConnection() {
        tangemSdk.stopSession()
    }
}

class TangemException(error: TangemSdkError) : Exception(error.message)
```

### 2.2 Add Dependency Injection

**File: `app/src/main/java/io/gnosis/safe/di/modules/ApplicationModule.kt`**

```kotlin
// Add imports
import io.gnosis.safe.ui.settings.owner.tangem.TangemController
import com.tangem.TangemSdk

// Add providers after existing wallet providers
@Provides
@Singleton
fun providesTangemController(@ApplicationContext context: Context) = TangemController(context)

@Provides
fun providesTangemSDK(): TangemSdk = TangemSdk()
```

### 2.3 Create Tangem Owner Paging Provider

**File: `app/src/main/java/io/gnosis/safe/ui/settings/owner/tangem/TangemOwnerPagingProvider.kt` (Create new)**

```kotlin
package io.gnosis.safe.ui.settings.owner.tangem

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tangem.card.Card
import io.gnosis.safe.ui.settings.owner.tangem.TangemOwnerPagingSource
import pm.gnosis.model.Solidity

class TangemOwnerPagingProvider(
    private val tangemController: TangemController
) {

    fun createPagingSource(
        card: Card,
        derivationPath: String,
        pageSize: Int = 20
    ): PagingSource<Long, Solidity.Address> {
        return TangemOwnerPagingSource(tangemController, card, derivationPath, pageSize)
    }
}
```

**File: `app/src/main/java/io/gnosis/safe/ui/settings/owner/tangem/TangemOwnerPagingSource.kt` (Create new)**

```kotlin
package io.gnosis.safe.ui.settings.owner.tangem

import androidx.paging.PagingSource
import com.tangem.card.Card
import pm.gnosis.model.Solidity
import timber.log.Timber

class TangemOwnerPagingSource(
    private val tangemController: TangemController,
    private val card: Card,
    private val derivationPath: String,
    private val pageSize: Int
) : PagingSource<Long, Solidity.Address>() {

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, Solidity.Address> {
        return try {
            val startIndex = params.key ?: 0L
            val addresses = mutableListOf<Solidity.Address>()

            for (i in startIndex until startIndex + pageSize) {
                val pathWithIndex = derivationPath.replace("{index}", i.toString())
                val address = tangemController.getAddress(card, pathWithIndex)
                addresses.add(address)
            }

            LoadResult.Page(
                data = addresses,
                prevKey = if (startIndex == 0L) null else startIndex - pageSize,
                nextKey = startIndex + pageSize
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load Tangem addresses")
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Long, Solidity.Address>): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}
```

---

## Phase 3: UI Integration

### 3.1 Update Owner Add Options Layout

**File: `app/src/main/res/layout/fragment_owner_add_options.xml`**

```xml
<!-- Add after Keystone item (around line 111) -->
<io.gnosis.safe.ui.settings.view.SettingItem
    android:id="@+id/item_connect_tangem"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@color/background_secondary"
    app:setting_name="@string/signing_owner_connect_tangem_title"
    app:setting_description="@string/signing_owner_connect_tangem_desc"
    app:setting_openable="true"
    app:setting_image="@drawable/ic_key_type_tangem_24dp" />

<View
    android:layout_width="match_parent"
    android:layout_height="@dimen/item_separator_height"
    android:background="@color/separator" />
```

### 3.2 Update Owner Add Options Fragment

**File: `app/src/main/java/io/gnosis/safe/ui/settings/owner/OwnerAddOptionsFragment.kt`**

```kotlin
// Add click listener in onViewCreated method (around line 44)
itemConnectTangem.setOnClickListener {
    findNavController().navigate(OwnerAddOptionsFragmentDirections.actionOwnerAddOptionsFragmentToOwnerInfoTangemFragment())
}
```

### 3.3 Add Navigation Routes

**File: `app/src/main/res/navigation/main_nav.xml`**

```xml
<!-- Add after Keystone navigation actions (around line 288) -->
<action
    android:id="@+id/action_ownerAddOptionsFragment_to_ownerInfoTangemFragment"
    app:destination="@id/ownerInfoTangemFragment" />

<!-- Add Tangem fragments after Keystone fragments -->
<fragment
    android:id="@+id/ownerInfoTangemFragment"
    android:name="io.gnosis.safe.ui.settings.owner.intro.OwnerInfoTangemFragment"
    android:label="OwnerInfoTangemFragment"
    tools:layout="@layout/fragment_owner_info_tangem">

    <action
        android:id="@+id/action_ownerInfoTangemFragment_to_tangemCardScanFragment"
        app:destination="@id/tangemCardScanFragment" />
</fragment>

<fragment
    android:id="@+id/tangemCardScanFragment"
    android:name="io.gnosis.safe.ui.settings.owner.tangem.TangemCardScanFragment"
    android:label="TangemCardScanFragment"
    tools:layout="@layout/fragment_tangem_card_scan">

    <argument
        android:name="derivationPath"
        app:argType="string" />

    <action
        android:id="@+id/action_tangemCardScanFragment_to_tangemOwnerSelectionFragment"
        app:destination="@id/tangemOwnerSelectionFragment" />
</fragment>

<fragment
    android:id="@+id/tangemOwnerSelectionFragment"
    android:name="io.gnosis.safe.ui.settings.owner.tangem.TangemOwnerSelectionFragment"
    android:label="TangemOwnerSelectionFragment"
    tools:layout="@layout/fragment_tangem_owner_selection">

    <argument
        android:name="cardId"
        app:argType="string" />

    <argument
        android:name="derivationPath"
        app:argType="string" />

    <action
        android:id="@+id/action_tangemOwnerSelectionFragment_to_ownerEnterNameFragment"
        app:destination="@id/ownerEnterNameFragment" />
</fragment>

<!-- Add signing fragment -->
<fragment
    android:id="@+id/tangemSignFragment"
    android:name="io.gnosis.safe.ui.settings.owner.tangem.TangemSignFragment"
    android:label="TangemSignFragment"
    tools:layout="@layout/fragment_tangem_sign">

    <argument
        android:name="owner"
        app:argType="string" />

    <argument
        android:name="signingMode"
        app:argType="io.gnosis.safe.ui.transactions.details.SigningMode" />

    <argument
        android:name="chain"
        app:argType="io.gnosis.data.models.Chain" />

    <argument
        android:name="safeTxHash"
        app:argType="string"
        app:nullable="true" />
</fragment>
```

### 3.4 Create Tangem Info Fragment

**File: `app/src/main/java/io/gnosis/safe/ui/settings/owner/intro/OwnerInfoTangemFragment.kt` (Create new)**

```kotlin
package io.gnosis.safe.ui.settings.owner.intro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import io.gnosis.safe.ScreenId
import io.gnosis.safe.databinding.FragmentOwnerInfoTangemBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.fragment.BaseViewBindingFragment
import java.math.BigInteger

class OwnerInfoTangemFragment : BaseViewBindingFragment<FragmentOwnerInfoTangemBinding>() {

    override fun screenId() = ScreenId.OWNER_INFO_TANGEM

    override suspend fun chainId(): BigInteger? = null

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentOwnerInfoTangemBinding =
        FragmentOwnerInfoTangemBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            backButton.setOnClickListener {
                findNavController().navigateUp()
            }

            continueButton.setOnClickListener {
                findNavController().navigate(
                    OwnerInfoTangemFragmentDirections.actionOwnerInfoTangemFragmentToTangemCardScanFragment(
                        derivationPath = "m/44'/60'/0'/{index}" // Default Ethereum path
                    )
                )
            }
        }
    }
}
```

### 3.5 Create Tangem Card Scan Fragment

**File: `app/src/main/java/io/gnosis/safe/ui/settings/owner/tangem/TangemCardScanFragment.kt` (Create new)**

```kotlin
package io.gnosis.safe.ui.settings.owner.tangem

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.tangem.card.Card
import io.gnosis.safe.ScreenId
import io.gnosis.safe.databinding.FragmentTangemCardScanBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.fragment.BaseViewBindingFragment
import io.gnosis.safe.ui.settings.owner.tangem.TangemCardScanViewModel
import java.math.BigInteger
import javax.inject.Inject

class TangemCardScanFragment : BaseViewBindingFragment<FragmentTangemCardScanBinding>() {

    @Inject
    lateinit var viewModel: TangemCardScanViewModel

    private val args: TangemCardScanFragmentArgs by navArgs()
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null

    override fun screenId() = ScreenId.TANGEM_CARD_SCAN

    override suspend fun chainId(): BigInteger? = null

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentTangemCardScanBinding =
        FragmentTangemCardScanBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext())
        pendingIntent = PendingIntent.getActivity(
            requireContext(), 0,
            Intent(requireContext(), requireContext().javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        with(binding) {
            backButton.setOnClickListener {
                findNavController().navigateUp()
            }

            scanButton.setOnClickListener {
                viewModel.startScanning()
            }
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            handleState(state)
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(requireActivity(), pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(requireActivity())
    }

    private fun handleState(state: TangemCardScanState) {
        when (state.viewAction) {
            is TangemCardScanState.CardDetected -> {
                val card = state.viewAction.card
                findNavController().navigate(
                    TangemCardScanFragmentDirections.actionTangemCardScanFragmentToTangemOwnerSelectionFragment(
                        cardId = card.cardId,
                        derivationPath = args.derivationPath
                    )
                )
            }
            is TangemCardScanState.Error -> {
                // Handle error
            }
        }
    }
}
```

---

## Phase 4: Signing Integration

### 4.1 Update Owner List ViewModel

**File: `app/src/main/java/io/gnosis/safe/ui/settings/owner/list/OwnerListViewModel.kt`**

```kotlin
// Add TANGEM case in selectKeyForSigning method (around line 140)
Owner.Type.TANGEM -> {
    updateState {
        OwnerListState(
            ViewAction.NavigateTo(
                SigningOwnerSelectionFragmentDirections.actionSigningOwnerSelectionFragmentToTangemSignFragment(
                    owner = owner.asEthereumAddressString(),
                    signingMode = signingMode,
                    chain = chain,
                    safeTxHash = safeTxHash
                )
            )
        )
    }
    updateState { OwnerListState(ViewAction.None) }
}
```

### 4.2 Create Tangem Sign ViewModel

**File: `app/src/main/java/io/gnosis/safe/ui/settings/owner/tangem/TangemSignViewModel.kt` (Create new)**

```kotlin
package io.gnosis.safe.ui.settings.owner.tangem

import com.tangem.card.Card
import io.gnosis.data.repositories.CredentialsRepository
import io.gnosis.safe.ui.base.AppDispatchers
import io.gnosis.safe.ui.base.BaseStateViewModel
import io.gnosis.safe.ui.transactions.details.SigningMode
import pm.gnosis.model.Solidity
import timber.log.Timber
import javax.inject.Inject

class TangemSignViewModel @Inject constructor(
    private val tangemController: TangemController,
    private val credentialsRepository: CredentialsRepository,
    appDispatchers: AppDispatchers
) : BaseStateViewModel<TangemSignState>(appDispatchers) {

    override fun initialState() = TangemSignState(ViewAction.Loading(true))

    fun signTransaction(ownerAddress: Solidity.Address, safeTxHash: String) {
        safeLaunch {
            val owner = credentialsRepository.owner(ownerAddress)
            owner?.let {
                try {
                    // For now, we'll need to scan the card again
                    // In a real implementation, you might cache the card
                    val card = tangemController.scanCard()
                    val signature = tangemController.signTransaction(card, safeTxHash)

                    updateState {
                        TangemSignState(Signature(signature))
                    }
                    updateState {
                        TangemSignState(ViewAction.None)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sign transaction")
                    updateState {
                        TangemSignState(ViewAction.ShowError(TangemSignFailed()))
                    }
                }
            }
        }
    }

    fun disconnectFromDevice() {
        tangemController.teardownConnection()
    }
}

class TangemSignFailed : Throwable()

data class TangemSignState(
    override var viewAction: BaseStateViewModel.ViewAction?
) : BaseStateViewModel.State

data class Signature(
    val signature: String
) : BaseStateViewModel.ViewAction
```

### 4.3 Create Tangem Sign Fragment

**File: `app/src/main/java/io/gnosis/safe/ui/settings/owner/tangem/TangemSignFragment.kt` (Create new)**

```kotlin
package io.gnosis.safe.ui.settings.owner.tangem

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import io.gnosis.safe.ScreenId
import io.gnosis.safe.databinding.FragmentTangemSignBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.fragment.BaseViewBindingFragment
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import java.math.BigInteger
import javax.inject.Inject

class TangemSignFragment : BaseViewBindingFragment<FragmentTangemSignBinding>() {

    @Inject
    lateinit var viewModel: TangemSignViewModel

    private val args: TangemSignFragmentArgs by navArgs()

    override fun screenId() = ScreenId.TANGEM_SIGN

    override suspend fun chainId(): BigInteger? = null

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentTangemSignBinding =
        FragmentTangemSignBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            backButton.setOnClickListener {
                findNavController().navigateUp()
            }

            signButton.setOnClickListener {
                val ownerAddress = args.owner.asEthereumAddress()
                val safeTxHash = args.safeTxHash
                if (ownerAddress != null && safeTxHash != null) {
                    viewModel.signTransaction(ownerAddress, safeTxHash)
                }
            }
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            handleState(state)
        }
    }

    private fun handleState(state: TangemSignState) {
        when (state.viewAction) {
            is TangemSignState.Signature -> {
                // Handle successful signature
                findNavController().navigateUp()
            }
            is TangemSignState.Error -> {
                // Handle error
            }
        }
    }
}
```

---

## Phase 5: Resources

### 5.1 Add String Resources

**File: `app/src/main/res/values/strings.xml`**

```xml
<!-- Add Tangem strings -->
<string name="signing_owner_connect_tangem_title">Connect Tangem Card</string>
<string name="signing_owner_connect_tangem_desc">Connect your Tangem hardware wallet via NFC</string>
<string name="tangem_scan_card">Scan Tangem Card</string>
<string name="tangem_card_detected">Tangem card detected</string>
<string name="tangem_signing_complete">Transaction signed successfully</string>
<string name="tangem_nfc_not_available">NFC is not available on this device</string>
<string name="tangem_nfc_disabled">Please enable NFC to use Tangem cards</string>
<string name="tangem_card_scan_instructions">Hold your Tangem card near the back of your device</string>
<string name="tangem_sign_transaction">Sign Transaction</string>
<string name="tangem_hold_card_to_sign">Hold your Tangem card near the device to sign</string>
```

### 5.2 Add Drawable Resources

**File: `app/src/main/res/drawable/ic_key_type_tangem_24dp.xml` (Create new)**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorOnSurface">
  <path
      android:fillColor="@android:color/white"
      android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM12,6c-3.31,0 -6,2.69 -6,6s2.69,6 6,6 6,-2.69 6,-6 -2.69,-6 -6,-6zM12,16c-2.21,0 -4,-1.79 -4,-4s1.79,-4 4,-4 4,1.79 4,4 -1.79,4 -4,4z"/>
</vector>
```

### 5.3 Create Layout Files

**File: `app/src/main/res/layout/fragment_owner_info_tangem.xml` (Create new)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background_secondary">

    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/appbar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/background_secondary">

        <LinearLayout
            android:id="@+id/toolbar_layout"
            style="@style/Toolbar"
            android:orientation="horizontal"
            android:padding="16dp"
            app:elevation="4dp">

            <ImageButton
                android:id="@+id/back_button"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginEnd="16dp"
                android:background="@android:color/transparent"
                android:src="@drawable/ic_baseline_arrow_back_24" />

            <TextView
                android:id="@+id/title"
                style="@style/ToolbarTitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="16dp"
                android:text="@string/signing_owner_connect_tangem_title" />

        </LinearLayout>

    </com.google.android.material.appbar.AppBarLayout>

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/background_primary"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <ImageView
                android:layout_width="120dp"
                android:layout_height="120dp"
                android:layout_gravity="center_horizontal"
                android:layout_marginBottom="24dp"
                android:src="@drawable/ic_key_type_tangem_24dp" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:gravity="center"
                android:text="@string/signing_owner_connect_tangem_title"
                android:textAppearance="@style/TextAppearance.MaterialComponents.Headline6" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="32dp"
                android:gravity="center"
                android:text="@string/signing_owner_connect_tangem_desc"
                android:textAppearance="@style/TextAppearance.MaterialComponents.Body1" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/continue_button"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/tangem_scan_card" />

        </LinearLayout>

    </androidx.core.widget.NestedScrollView>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

**File: `app/src/main/res/layout/fragment_tangem_card_scan.xml` (Create new)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background_secondary">

    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/appbar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/background_secondary">

        <LinearLayout
            android:id="@+id/toolbar_layout"
            style="@style/Toolbar"
            android:orientation="horizontal"
            android:padding="16dp"
            app:elevation="4dp">

            <ImageButton
                android:id="@+id/back_button"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginEnd="16dp"
                android:background="@android:color/transparent"
                android:src="@drawable/ic_baseline_arrow_back_24" />

            <TextView
                android:id="@+id/title"
                style="@style/ToolbarTitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="16dp"
                android:text="@string/tangem_scan_card" />

        </LinearLayout>

    </com.google.android.material.appbar.AppBarLayout>

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/background_primary"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <ImageView
                android:layout_width="120dp"
                android:layout_height="120dp"
                android:layout_gravity="center_horizontal"
                android:layout_marginBottom="24dp"
                android:src="@drawable/ic_key_type_tangem_24dp" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:gravity="center"
                android:text="@string/tangem_card_scan_instructions"
                android:textAppearance="@style/TextAppearance.MaterialComponents.Body1" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/scan_button"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/tangem_scan_card" />

        </LinearLayout>

    </androidx.core.widget.NestedScrollView>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

---

## Phase 6: Testing and Validation

### 6.1 Unit Tests

**File: `app/src/test/java/io/gnosis/safe/ui/settings/owner/tangem/TangemControllerTest.kt` (Create new)**

```kotlin
package io.gnosis.safe.ui.settings.owner.tangem

import com.tangem.card.Card
import com.tangem.common.core.TangemSdkError
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class TangemControllerTest {

    private lateinit var tangemController: TangemController
    private val mockContext = mockk<android.content.Context>()

    @Before
    fun setup() {
        tangemController = TangemController(mockContext)
    }

    @Test
    fun `test NFC availability check`() {
        // Test NFC availability
        val isAvailable = tangemController.isNfcAvailable()
        // Assert based on test environment
    }

    @Test
    fun `test card scanning success`() = runTest {
        // Mock successful card scan
        val mockCard = mockk<Card>()

        // Test card scanning
        val result = tangemController.scanCard()
        assertEquals(mockCard, result)
    }

    @Test
    fun `test card scanning failure`() = runTest {
        // Mock failed card scan
        val mockError = mockk<TangemSdkError>()

        // Test error handling
        assertThrows(TangemException::class.java) {
            tangemController.scanCard()
        }
    }
}
```

### 6.2 Integration Tests

**File: `app/src/androidTest/java/io/gnosis/safe/ui/settings/owner/tangem/TangemIntegrationTest.kt` (Create new)**

```kotlin
package io.gnosis.safe.ui.settings.owner.tangem

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class TangemIntegrationTest {

    @Test
    fun testTangemControllerIntegration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val controller = TangemController(context)

        // Test NFC availability
        assertTrue("NFC should be available", controller.isNfcAvailable())
    }
}
```

### 6.3 UI Tests

**File: `app/src/androidTest/java/io/gnosis/safe/ui/settings/owner/tangem/TangemUITest.kt` (Create new)**

```kotlin
package io.gnosis.safe.ui.settings.owner.tangem

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import io.gnosis.safe.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TangemUITest {

    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun testTangemWalletSelection() {
        // Navigate to owner add options
        // Click on Tangem option
        // Verify navigation to Tangem info fragment
    }

    @Test
    fun testTangemCardScanning() {
        // Navigate to card scan fragment
        // Test scan button functionality
        // Verify NFC interaction
    }
}
```

---

## Implementation Checklist

### Phase 1: Foundation Setup

- [ ] Add Tangem SDK dependency to `buildsystem/versions.gradle`
- [ ] Add Tangem SDK implementation to `app/build.gradle`
- [ ] Add NFC permissions to `AndroidManifest.xml`
- [ ] Create NFC tech filter XML file
- [ ] Update `Owner.Type` enum to include `TANGEM(4)`
- [ ] Update `OwnerTypeConverter` to handle Tangem type
- [ ] Add `saveTangemOwner` method to `CredentialsRepository`
- [ ] Update `ownerCount` method to include Tangem

### Phase 2: Core Integration

- [ ] Create `TangemController` class
- [ ] Add dependency injection providers in `ApplicationModule`
- [ ] Create `TangemOwnerPagingProvider` class
- [ ] Create `TangemOwnerPagingSource` class
- [ ] Test NFC functionality and card scanning

### Phase 3: UI Integration

- [ ] Update `fragment_owner_add_options.xml` layout
- [ ] Add click listener in `OwnerAddOptionsFragment`
- [ ] Add navigation routes in `main_nav.xml`
- [ ] Create `OwnerInfoTangemFragment`
- [ ] Create `TangemCardScanFragment`
- [ ] Create `TangemOwnerSelectionFragment`
- [ ] Create corresponding layout files

### Phase 4: Signing Integration

- [ ] Update `OwnerListViewModel` to handle Tangem signing
- [ ] Create `TangemSignViewModel`
- [ ] Create `TangemSignFragment`
- [ ] Create signing layout file
- [ ] Test transaction signing flow

### Phase 5: Resources

- [ ] Add string resources for Tangem
- [ ] Create Tangem icon drawable
- [ ] Create all required layout files
- [ ] Add localization support
- [ ] Test UI components

### Phase 6: Testing and Validation

- [ ] Write unit tests for `TangemController`
- [ ] Write unit tests for `TangemSignViewModel`
- [ ] Write integration tests for NFC functionality
- [ ] Write UI tests for Tangem flows
- [ ] Test with real Tangem cards
- [ ] Validate error handling scenarios
- [ ] Performance testing
- [ ] Security validation

---

## Testing Strategy

### 1. Unit Testing

- Test `TangemController` methods individually
- Mock NFC interactions and Tangem SDK responses
- Test error handling and edge cases
- Validate address derivation logic

### 2. Integration Testing

- Test NFC communication with mock cards
- Test end-to-end wallet creation flow
- Test transaction signing process
- Validate database operations

### 3. UI Testing

- Test wallet selection flow
- Test card scanning interface
- Test address selection process
- Test signing confirmation flow

### 4. Device Testing

- Test with real Tangem cards
- Test on different Android versions
- Test NFC compatibility across devices
- Validate user experience

---

## Risk Mitigation

### 1. NFC Reliability

- Implement retry mechanisms for failed scans
- Provide clear user feedback for NFC issues
- Handle device positioning and timing

### 2. Card Management

- Implement proper card caching
- Handle multiple card scenarios
- Validate card authenticity

### 3. Security

- Ensure private keys never leave the card
- Implement proper card authentication
- Validate signature integrity

### 4. User Experience

- Provide clear scanning instructions
- Handle NFC state changes gracefully
- Implement proper error messaging

---

## Deployment Considerations

### 1. Feature Flags

- Consider implementing feature flags for gradual rollout
- Allow disabling Tangem support if needed
- Monitor usage and performance metrics

### 2. Documentation

- Update user documentation
- Create troubleshooting guides
- Document NFC requirements

### 3. Support

- Train support team on Tangem-specific issues
- Create FAQ for common problems
- Monitor user feedback and issues

This implementation plan provides a comprehensive roadmap for integrating Tangem wallet support while maintaining consistency with the existing codebase architecture and patterns.
