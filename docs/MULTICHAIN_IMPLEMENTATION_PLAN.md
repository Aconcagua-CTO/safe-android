# Multichain Safe Implementation Plan

## Overview

This document outlines the implementation plan for transforming the Safe Android app from a single-chain approach to a multichain approach. The goal is to provide users with a chain-agnostic experience where they can manage their Safe vaults across multiple blockchains seamlessly.

## Current Architecture Analysis

### Current System

- **Safe Storage**: Each Safe is stored with a composite primary key `(address, chainId)`
- **Safe Selection**: Safes are grouped by blockchain with chain headers
- **Active Safe**: Only one Safe (on one chain) can be active at a time
- **Balance Display**: Shows balances for the active Safe on its specific chain
- **UI Flow**: User must explicitly select both vault and blockchain

### Key Components

- `Safe.kt` - Data model with address, localName, chainId
- `SafeRepository.kt` - Manages Safe CRUD and active Safe selection
- `SafeSelectionViewModel.kt` - Handles Safe switching logic
- `TokenRepository.kt` - Fetches balances per Safe/chain
- `StartActivity.kt` - Main activity with toolbar showing active Safe

## Design Principles

### 1. Non-Breaking Approach

- **Keep existing files untouched**
- **Create new parallel components**
- **Use feature flags for gradual rollout**
- **Maintain backward compatibility**

### 2. UI-Based Data Loading

- **Query balances on-demand** instead of persistent storage
- **Real-time data fetching** from blockchain APIs
- **No complex caching/sync logic**
- **Always fresh balance information**

## Implementation Plan

### Phase 1: New Data Models and Core Services

#### 1.1 Create New Data Models

**File: `app/src/main/java/io/gnosis/safe/multichain/models/MultichainSafeModels.kt`**

```kotlin
data class MultichainSafe(
    val address: Solidity.Address,
    val localName: String,
    val safesPerChain: Map<BigInteger, Safe> // chainId -> Safe
) {
    val deployedChains: List<Chain> get() = safesPerChain.values.map { it.chain }
    val chainNames: String get() = deployedChains.joinToString(", ") { it.name }
    val chainCount: Int get() = safesPerChain.size
}

data class MultichainBalanceData(
    val totalFiatValue: BigDecimal,
    val balancesByChain: Map<Chain, CoinBalances>,
    val isLoading: Boolean = false,
    val errors: Map<Chain, Exception> = emptyMap()
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()
    val successfulChains: List<Chain> get() = balancesByChain.keys.toList()
    val failedChains: List<Chain> get() = errors.keys.toList()
}

sealed class MultichainSafeSelectionViewData {
    object AddSafeHeader : MultichainSafeSelectionViewData()

    data class MultichainSafeItem(
        val multichainSafe: MultichainSafe
    ) : MultichainSafeSelectionViewData()
}
```

#### 1.2 Multichain Safe Repository

**File: `app/src/main/java/io/gnosis/safe/multichain/repositories/MultichainSafeRepository.kt`**

```kotlin
class MultichainSafeRepository @Inject constructor(
    private val safeRepository: SafeRepository,
    private val chainInfoRepository: ChainInfoRepository,
    private val preferencesManager: PreferencesManager
) {

    suspend fun getMultichainSafes(): List<MultichainSafe> {
        return safeRepository.getSafes()
            .groupBy { it.address }
            .map { (address, safes) ->
                MultichainSafe(
                    address = address,
                    localName = safes.first().localName, // Assume same name across chains
                    safesPerChain = safes.associateBy { it.chainId }
                )
            }
            .sortedBy { it.localName }
    }

    suspend fun getMultichainSafeByAddress(address: Solidity.Address): MultichainSafe? {
        val safes = safeRepository.getSafes().filter { it.address == address }
        if (safes.isEmpty()) return null

        return MultichainSafe(
            address = address,
            localName = safes.first().localName,
            safesPerChain = safes.associateBy { it.chainId }
        )
    }

    fun getActiveMultichainSafe(): MultichainSafe? {
        val activeAddress = preferencesManager.prefs.getString(ACTIVE_MULTICHAIN_SAFE, null)
            ?.asEthereumAddress()
        return activeAddress?.let { runBlocking { getMultichainSafeByAddress(it) } }
    }

    fun setActiveMultichainSafe(multichainSafe: MultichainSafe) {
        preferencesManager.prefs.edit {
            putString(ACTIVE_MULTICHAIN_SAFE, multichainSafe.address.asEthereumAddressString())
        }
    }

    fun activeSafeFlow() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ACTIVE_MULTICHAIN_SAFE) trySend(key)
        }
        preferencesManager.prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferencesManager.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
        .onStart { emit(ACTIVE_MULTICHAIN_SAFE) }
        .map { getActiveMultichainSafe() }
        .conflate()

    companion object {
        private const val ACTIVE_MULTICHAIN_SAFE = "prefs.string.active_multichain_safe"
    }
}
```

#### 1.3 Multichain Balance Service

**File: `app/src/main/java/io/gnosis/safe/multichain/services/MultichainBalanceService.kt`**

```kotlin
class MultichainBalanceService @Inject constructor(
    private val tokenRepository: TokenRepository,
    appDispatchers: AppDispatchers
) {

    suspend fun loadAggregatedBalances(
        multichainSafe: MultichainSafe,
        fiatCode: String
    ): MultichainBalanceData = withContext(appDispatchers.io) {

        val balancesByChain = mutableMapOf<Chain, CoinBalances>()
        val errors = mutableMapOf<Chain, Exception>()

        // Load balances from all chains in parallel
        val deferredResults = multichainSafe.safesPerChain.values.map { safe ->
            async {
                try {
                    val balance = tokenRepository.loadBalanceOf(safe, fiatCode)
                    safe.chain to balance
                } catch (e: Exception) {
                    safe.chain to e
                }
            }
        }

        // Collect results
        deferredResults.awaitAll().forEach { result ->
            when (val value = result.second) {
                is CoinBalances -> balancesByChain[result.first] = value
                is Exception -> errors[result.first] = value
            }
        }

        // Calculate total fiat value
        val totalFiat = balancesByChain.values.sumOf { it.fiatTotal }

        MultichainBalanceData(
            totalFiatValue = totalFiat,
            balancesByChain = balancesByChain,
            errors = errors
        )
    }

    suspend fun loadBalanceForChain(
        safe: Safe,
        fiatCode: String
    ): Result<CoinBalances> = withContext(appDispatchers.io) {
        try {
            Result.success(tokenRepository.loadBalanceOf(safe, fiatCode))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### Phase 2: New UI Components

#### 2.1 Multichain Safe Selection ViewModel

**File: `app/src/main/java/io/gnosis/safe/multichain/ui/selection/MultichainSafeSelectionViewModel.kt`**

```kotlin
class MultichainSafeSelectionViewModel @Inject constructor(
    private val multichainSafeRepository: MultichainSafeRepository,
    private val settingsHandler: SettingsHandler,
    appDispatchers: AppDispatchers
) : BaseStateViewModel<MultichainSafeSelectionState>(appDispatchers),
    MultichainSafeSelectionAdapter.OnMultichainSafeSelectionItemClickedListener {

    private val items: MutableList<MultichainSafeSelectionViewData> = mutableListOf()
    private var activeMultichainSafe: MultichainSafe? = null

    override fun initialState(): MultichainSafeSelectionState =
        MultichainSafeSelectionState.SafeListState(
            listOf(MultichainSafeSelectionViewData.AddSafeHeader), null, null
        )

    fun loadMultichainSafes() {
        safeLaunch {
            activeMultichainSafe = multichainSafeRepository.getActiveMultichainSafe()

            with(items) {
                clear()
                add(MultichainSafeSelectionViewData.AddSafeHeader)

                val multichainSafes = multichainSafeRepository.getMultichainSafes()

                // Add active safe first if it exists
                activeMultichainSafe?.let { active ->
                    add(MultichainSafeSelectionViewData.MultichainSafeItem(active))
                    // Add other safes
                    multichainSafes.filter { it.address != active.address }
                        .forEach { add(MultichainSafeSelectionViewData.MultichainSafeItem(it)) }
                } ?: run {
                    // No active safe, add all safes
                    multichainSafes.forEach {
                        add(MultichainSafeSelectionViewData.MultichainSafeItem(it))
                    }
                }
            }

            updateState {
                MultichainSafeSelectionState.SafeListState(items, activeMultichainSafe, null)
            }
        }
    }

    private fun selectMultichainSafe(multichainSafe: MultichainSafe) {
        safeLaunch {
            if (multichainSafeRepository.getActiveMultichainSafe() != multichainSafe) {
                multichainSafeRepository.setActiveMultichainSafe(multichainSafe)
                updateState {
                    MultichainSafeSelectionState.SafeListState(
                        items,
                        multichainSafe,
                        ViewAction.CloseScreen
                    )
                }
            }
        }
    }

    override fun onMultichainSafeClicked(multichainSafe: MultichainSafe) {
        selectMultichainSafe(multichainSafe)
    }

    override fun onAddSafeClicked() {
        safeLaunch {
            updateState {
                MultichainSafeSelectionState.AddSafeState(
                    ViewAction.NavigateTo(
                        MultichainSafeSelectionDialogDirections.actionMultichainSafeSelectionDialogToAddSafe()
                    )
                )
            }
        }
    }
}

sealed class MultichainSafeSelectionState : BaseStateViewModel.State {
    data class SafeListState(
        val listItems: List<MultichainSafeSelectionViewData>,
        val activeMultichainSafe: MultichainSafe?,
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : MultichainSafeSelectionState()

    data class AddSafeState(
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : MultichainSafeSelectionState()
}
```

#### 2.2 Multichain Safe Selection Adapter

**File: `app/src/main/java/io/gnosis/safe/multichain/ui/selection/MultichainSafeSelectionAdapter.kt`**

```kotlin
class MultichainSafeSelectionAdapter(
    private val clickListener: WeakReference<OnMultichainSafeSelectionItemClickedListener>
) : RecyclerView.Adapter<BaseMultichainSafeSelectionViewHolder>() {

    private val items = mutableListOf<MultichainSafeSelectionViewData>()
    var activeMultichainSafe: MultichainSafe? = null
        set(value) {
            field = value
            notifyAllChanged()
        }

    fun setItems(items: List<MultichainSafeSelectionViewData>, activeMultichainSafe: MultichainSafe?) {
        this.activeMultichainSafe = activeMultichainSafe
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: BaseMultichainSafeSelectionViewHolder, position: Int) {
        when (holder) {
            is AddSafeHeaderViewHolder -> holder.bind()
            is MultichainSafeItemViewHolder -> {
                val safeItem = items[position] as MultichainSafeSelectionViewData.MultichainSafeItem
                holder.bind(safeItem.multichainSafe, safeItem.multichainSafe == activeMultichainSafe)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseMultichainSafeSelectionViewHolder {
        return when (MultichainSafeSelectionViewTypes.values()[viewType]) {
            MultichainSafeSelectionViewTypes.HEADER_ADD_SAFE -> AddSafeHeaderViewHolder(
                ItemAddSafeBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                clickListener
            )
            MultichainSafeSelectionViewTypes.MULTICHAIN_SAFE -> MultichainSafeItemViewHolder(
                ItemMultichainSafeBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                clickListener
            )
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        return when (item) {
            is MultichainSafeSelectionViewData.AddSafeHeader -> MultichainSafeSelectionViewTypes.HEADER_ADD_SAFE.ordinal
            is MultichainSafeSelectionViewData.MultichainSafeItem -> MultichainSafeSelectionViewTypes.MULTICHAIN_SAFE.ordinal
            else -> throw UnsupportedViewType(item.toString())
        }
    }

    override fun getItemCount() = items.size

    private fun notifyAllChanged() {
        notifyItemRangeChanged(0, items.size)
    }

    enum class MultichainSafeSelectionViewTypes {
        HEADER_ADD_SAFE,
        MULTICHAIN_SAFE
    }

    interface OnMultichainSafeSelectionItemClickedListener {
        fun onMultichainSafeClicked(multichainSafe: MultichainSafe)
        fun onAddSafeClicked()
    }
}

abstract class BaseMultichainSafeSelectionViewHolder(
    viewBinding: ViewBinding
) : RecyclerView.ViewHolder(viewBinding.root)

class MultichainSafeItemViewHolder(
    private val binding: ItemMultichainSafeBinding,
    private val clickListener: WeakReference<MultichainSafeSelectionAdapter.OnMultichainSafeSelectionItemClickedListener>
) : BaseMultichainSafeSelectionViewHolder(binding) {

    fun bind(multichainSafe: MultichainSafe, selected: Boolean) {
        with(binding) {
            safeName.text = multichainSafe.localName
            safeAddress.text = multichainSafe.address.asEthereumAddressChecksumString().abbreviateEthAddress()
            safeChains.text = multichainSafe.chainNames
            safeImage.setAddress(multichainSafe.address)
            safeSelection.visible(selected, View.INVISIBLE)

            root.setOnClickListener {
                clickListener.get()?.onMultichainSafeClicked(multichainSafe)
            }
        }
    }
}
```

#### 2.3 New Layout Files

**File: `app/src/main/res/layout/item_multichain_safe.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/background_secondary_selectable"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toTopOf="parent">

    <pm.gnosis.blockies.BlockiesImageView
        android:id="@+id/safe_image"
        android:layout_width="@dimen/safe_blockie_size"
        android:layout_height="@dimen/safe_blockie_size"
        android:layout_marginVertical="@dimen/default_margin"
        android:layout_marginStart="@dimen/default_margin"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <TextView
        android:id="@+id/safe_name"
        style="@style/ToolbarTitle"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="@dimen/default_margin"
        android:layout_marginTop="@dimen/default_small_margin"
        android:singleLine="true"
        android:visibility="visible"
        app:layout_constraintBottom_toTopOf="@+id/safe_address"
        app:layout_constraintEnd_toStartOf="@+id/safe_selection"
        app:layout_constraintStart_toEndOf="@+id/safe_image"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintVertical_chainStyle="packed"
        tools:text="My Company Funds" />

    <TextView
        android:id="@+id/safe_address"
        style="@style/ToolbarSubtitle"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="@dimen/default_margin"
        app:layout_constraintBottom_toTopOf="@+id/safe_chains"
        app:layout_constraintEnd_toStartOf="@+id/safe_selection"
        app:layout_constraintStart_toEndOf="@+id/safe_image"
        app:layout_constraintTop_toBottomOf="@+id/safe_name"
        tools:text="0xAB...146F" />

    <TextView
        android:id="@+id/safe_chains"
        style="@style/ToolbarSubtitle"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginBottom="@dimen/default_small_margin"
        android:layout_marginHorizontal="@dimen/default_margin"
        android:textColor="@color/medium_grey"
        android:textSize="12sp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toStartOf="@+id/safe_selection"
        app:layout_constraintStart_toEndOf="@+id/safe_image"
        app:layout_constraintTop_toBottomOf="@+id/safe_address"
        tools:text="Ethereum, Polygon, Arbitrum" />

    <ImageView
        android:id="@+id/safe_selection"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginEnd="@dimen/default_margin"
        app:tint="@color/primary"
        android:visibility="invisible"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:srcCompat="@drawable/ic_check_black_24dp"
        tools:visibility="visible" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

**File: `app/src/main/res/layout/dialog_multichain_safe_selection.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.core.widget.NestedScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <View
            android:id="@+id/drag_handle"
            android:layout_width="28dp"
            android:layout_height="4dp"
            android:layout_marginTop="16dp"
            android:background="@color/light_grey"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent" />

        <TextView
            android:id="@+id/title"
            style="@style/TextTitleSection"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="16dp"
            android:layout_marginTop="24dp"
            android:text="@string/switch_safes"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/drag_handle" />

        <View
            android:id="@+id/separator"
            android:layout_width="match_parent"
            android:layout_height="2dp"
            android:layout_marginTop="16dp"
            android:background="@color/background_primary"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/title" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/list"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:nestedScrollingEnabled="false"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/separator"
            tools:listitem="@layout/item_multichain_safe" />

    </androidx.constraintlayout.widget.ConstraintLayout>

</androidx.core.widget.NestedScrollView>
```

### Phase 3: Balance Display Components

#### 3.1 Multichain Assets ViewModel

**File: `app/src/main/java/io/gnosis/safe/multichain/ui/assets/MultichainAssetsViewModel.kt`**

```kotlin
class MultichainAssetsViewModel @Inject constructor(
    private val multichainSafeRepository: MultichainSafeRepository,
    private val multichainBalanceService: MultichainBalanceService,
    appDispatchers: AppDispatchers
) : BaseStateViewModel<MultichainAssetsState>(appDispatchers) {

    var activeMultichainSafe: MultichainSafe? = null
        private set

    override fun initialState(): MultichainAssetsState =
        MultichainAssetsState.SafeLoading(null)

    init {
        safeLaunch {
            multichainSafeRepository.activeSafeFlow().collect { multichainSafe ->
                updateState {
                    activeMultichainSafe = multichainSafe
                    MultichainAssetsState.ActiveMultichainSafe(multichainSafe, null)
                }
            }
        }
    }

    fun loadBalances(refreshing: Boolean = false) {
        safeLaunch {
            val multichainSafe = activeMultichainSafe ?: return@safeLaunch

            updateState {
                MultichainAssetsState.LoadingBalances(
                    multichainSafe = multichainSafe,
                    refreshing = refreshing,
                    viewAction = null
                )
            }

            try {
                val balanceData = multichainBalanceService.loadAggregatedBalances(
                    multichainSafe,
                    settingsHandler.userDefaultFiat
                )

                updateState {
                    MultichainAssetsState.BalancesLoaded(
                        multichainSafe = multichainSafe,
                        balanceData = balanceData,
                        viewAction = null
                    )
                }
            } catch (e: Exception) {
                updateState {
                    MultichainAssetsState.BalanceError(
                        multichainSafe = multichainSafe,
                        error = e,
                        viewAction = ViewAction.ShowError(e)
                    )
                }
            }
        }
    }

    fun updateTotalBalance(balance: String) {
        safeLaunch {
            updateState {
                MultichainAssetsState.TotalBalance(balance, null)
            }
            updateState {
                MultichainAssetsState.ActiveMultichainSafe(activeMultichainSafe, null)
            }
        }
    }
}

sealed class MultichainAssetsState : BaseStateViewModel.State {

    data class SafeLoading(
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : MultichainAssetsState()

    data class ActiveMultichainSafe(
        val multichainSafe: MultichainSafe?,
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : MultichainAssetsState()

    data class LoadingBalances(
        val multichainSafe: MultichainSafe,
        val refreshing: Boolean,
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : MultichainAssetsState()

    data class BalancesLoaded(
        val multichainSafe: MultichainSafe,
        val balanceData: MultichainBalanceData,
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : MultichainAssetsState()

    data class BalanceError(
        val multichainSafe: MultichainSafe,
        val error: Exception,
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : MultichainAssetsState()

    data class TotalBalance(
        val totalBalance: String,
        override var viewAction: BaseStateViewModel.ViewAction?
    ) : MultichainAssetsState()
}
```

#### 3.2 Multichain Coins ViewModel

**File: `app/src/main/java/io/gnosis/safe/multichain/ui/assets/coins/MultichainCoinsViewModel.kt`**

```kotlin
class MultichainCoinsViewModel @Inject constructor(
    private val multichainSafeRepository: MultichainSafeRepository,
    private val multichainBalanceService: MultichainBalanceService,
    private val credentialsRepository: CredentialsRepository,
    private val settingsHandler: SettingsHandler,
    private val balanceFormatter: BalanceFormatter,
    private val tracker: Tracker,
    appDispatchers: AppDispatchers
) : BaseStateViewModel<MultichainCoinsState>(appDispatchers) {

    override fun initialState(): MultichainCoinsState =
        MultichainCoinsState(loading = false, refreshing = false, viewAction = null)

    init {
        safeLaunch {
            multichainSafeRepository.activeSafeFlow().collect {
                load()
            }
        }
    }

    fun load(refreshing: Boolean = false) {
        safeLaunch {
            val multichainSafe = multichainSafeRepository.getActiveMultichainSafe()
            val userDefaultFiat = settingsHandler.userDefaultFiat

            if (multichainSafe != null) {
                updateState {
                    MultichainCoinsState(
                        loading = !refreshing,
                        refreshing = refreshing,
                        viewAction = if (refreshing) null else ViewAction.UpdateActiveMultichainSafe(multichainSafe)
                    )
                }

                try {
                    val balanceData = multichainBalanceService.loadAggregatedBalances(
                        multichainSafe,
                        userDefaultFiat
                    )

                    val banner = determineBannerType()
                    val totalBalance = getTotalBalanceViewData(balanceData)
                    val balances = getBalanceViewData(balanceData, banner)

                    updateState {
                        MultichainCoinsState(
                            loading = false,
                            refreshing = false,
                            viewAction = UpdateMultichainBalances(totalBalance, balances)
                        )
                    }
                } catch (e: Exception) {
                    updateState {
                        MultichainCoinsState(
                            loading = false,
                            refreshing = false,
                            viewAction = ViewAction.ShowError(e)
                        )
                    }
                }
            }
        }
    }

    private fun determineBannerType(): MultichainCoinsViewData.Banner.Type {
        return when {
            settingsHandler.showOwnerBanner && credentialsRepository.ownerCount() == 0 ->
                MultichainCoinsViewData.Banner.Type.ADD_OWNER_KEY
            settingsHandler.showPasscodeBanner && credentialsRepository.ownerCount() > 0 ->
                MultichainCoinsViewData.Banner.Type.PASSCODE
            else -> MultichainCoinsViewData.Banner.Type.NONE
        }
    }

    suspend fun getTotalBalanceViewData(balanceData: MultichainBalanceData): MultichainCoinsViewData.TotalBalance {
        val userCurrencyCode = settingsHandler.userDefaultFiat
        return MultichainCoinsViewData.TotalBalance(
            balanceFormatter.fiatBalanceWithCurrency(
                balanceData.totalFiatValue.setScale(2, RoundingMode.HALF_UP),
                userCurrencyCode
            )
        )
    }

    suspend fun getBalanceViewData(
        balanceData: MultichainBalanceData,
        banner: MultichainCoinsViewData.Banner.Type
    ): List<MultichainCoinsViewData> {
        val userCurrencyCode = settingsHandler.userDefaultFiat
        val result = mutableListOf<MultichainCoinsViewData>()

        // Add banner if needed
        if (banner != MultichainCoinsViewData.Banner.Type.NONE) {
            result.add(MultichainCoinsViewData.Banner(banner))
        }

        // Group tokens by address across chains and sum balances
        val tokenGroups = mutableMapOf<String, MutableList<Pair<Chain, Balance>>>()

        balanceData.balancesByChain.forEach { (chain, coinBalances) ->
            coinBalances.items.forEach { balance ->
                val tokenAddress = balance.tokenInfo.address.asEthereumAddressChecksumString()
                tokenGroups.getOrPut(tokenAddress) { mutableListOf() }
                    .add(chain to balance)
            }
        }

        // Create aggregated coin balance view data
        tokenGroups.forEach { (tokenAddress, chainBalances) ->
            val firstBalance = chainBalances.first().second
            val totalTokenBalance = chainBalances.sumOf { it.second.balance }
            val totalFiatBalance = chainBalances.sumOf { it.second.fiatBalance }
            val chainsWithToken = chainBalances.map { it.first.name }.joinToString(", ")

            result.add(
                MultichainCoinsViewData.AggregatedCoinBalance(
                    tokenAddress = tokenAddress,
                    decimals = firstBalance.tokenInfo.decimals,
                    symbol = firstBalance.tokenInfo.symbol,
                    logoUri = firstBalance.tokenInfo.logoUri,
                    totalBalance = totalTokenBalance.convertAmount(firstBalance.tokenInfo.decimals),
                    displayBalance = balanceFormatter.shortAmount(
                        totalTokenBalance.convertAmount(firstBalance.tokenInfo.decimals)
                    ),
                    fiatBalance = balanceFormatter.fiatBalanceWithCurrency(
                        totalFiatBalance.setScale(2, RoundingMode.HALF_UP),
                        userCurrencyCode
                    ),
                    chainsWithBalance = chainsWithToken,
                    chainCount = chainBalances.size
                )
            )
        }

        return result
    }
}

data class MultichainCoinsState(
    val loading: Boolean,
    val refreshing: Boolean,
    override var viewAction: BaseStateViewModel.ViewAction?
) : BaseStateViewModel.State

data class UpdateMultichainBalances(
    val newTotalBalance: MultichainCoinsViewData.TotalBalance,
    val newBalances: List<MultichainCoinsViewData>
) : BaseStateViewModel.ViewAction
```

#### 3.3 Multichain Coins View Data

**File: `app/src/main/java/io/gnosis/safe/multichain/ui/assets/coins/MultichainCoinsViewData.kt`**

```kotlin
sealed class MultichainCoinsViewData {

    data class TotalBalance(
        val totalBalance: String
    ) : MultichainCoinsViewData()

    data class Banner(
        val type: Type
    ) : MultichainCoinsViewData() {
        enum class Type {
            ADD_OWNER_KEY,
            PASSCODE,
            NONE
        }
    }

    data class AggregatedCoinBalance(
        val tokenAddress: String,
        val decimals: Int,
        val symbol: String,
        val logoUri: String?,
        val totalBalance: BigDecimal,
        val displayBalance: String,
        val fiatBalance: String,
        val chainsWithBalance: String,
        val chainCount: Int
    ) : MultichainCoinsViewData()

    data class ChainSpecificBalance(
        val chain: Chain,
        val balance: CoinBalances,
        val isExpanded: Boolean = false
    ) : MultichainCoinsViewData()
}
```

### Phase 4: Feature Toggle and Migration Strategy

#### 4.1 Feature Flag Implementation

**File: `app/src/main/java/io/gnosis/safe/multichain/MultichainFeatureFlag.kt`**

```kotlin
class MultichainFeatureFlag @Inject constructor(
    private val settingsHandler: SettingsHandler
) {

    fun isMultichainEnabled(): Boolean = settingsHandler.isMultichainModeEnabled

    fun enableMultichainMode() {
        settingsHandler.isMultichainModeEnabled = true
    }

    fun disableMultichainMode() {
        settingsHandler.isMultichainModeEnabled = false
    }

    companion object {
        // For A/B testing or gradual rollout
        fun isEligibleForMultichain(userId: String): Boolean {
            // Implement logic based on user ID hash, percentage rollout, etc.
            return userId.hashCode() % 100 < 20 // 20% rollout
        }
    }
}
```

#### 4.2 Settings Handler Extension

**File: `app/src/main/java/io/gnosis/safe/ui/settings/app/SettingsHandler.kt` (extend existing)**

```kotlin
// Add to existing SettingsHandler class
var isMultichainModeEnabled: Boolean
    get() = prefs.getBoolean(MULTICHAIN_MODE_ENABLED, false)
    set(value) = prefs.edit().putBoolean(MULTICHAIN_MODE_ENABLED, value).apply()

companion object {
    // Add to existing constants
    private const val MULTICHAIN_MODE_ENABLED = "prefs.boolean.multichain_mode_enabled"
}
```

#### 4.3 Navigation Logic Update

**File: `app/src/main/java/io/gnosis/safe/multichain/navigation/MultichainNavigationHelper.kt`**

```kotlin
class MultichainNavigationHelper @Inject constructor(
    private val multichainFeatureFlag: MultichainFeatureFlag
) {

    fun navigateToSafeSelection(navController: NavController) {
        if (multichainFeatureFlag.isMultichainEnabled()) {
            navController.navigate(R.id.multichainSafeSelectionDialog)
        } else {
            navController.navigate(R.id.safeSelectionDialog)
        }
    }

    fun shouldUseMultichainAssets(): Boolean {
        return multichainFeatureFlag.isMultichainEnabled()
    }
}
```

#### 4.4 Multichain Safe Selection Dialog

**File: `app/src/main/java/io/gnosis/safe/multichain/ui/selection/MultichainSafeSelectionDialog.kt`**

```kotlin
class MultichainSafeSelectionDialog : BaseBottomSheetDialogFragment<DialogMultichainSafeSelectionBinding>() {

    @Inject
    lateinit var viewModel: MultichainSafeSelectionViewModel

    @Inject
    lateinit var adapter: MultichainSafeSelectionAdapter

    var navHandler: SafeOverviewNavigationHandler? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        navHandler = context as SafeOverviewNavigationHandler
    }

    override fun onDetach() {
        super.onDetach()
        navHandler = null
    }

    override fun screenId() = ScreenId.MULTICHAIN_SAFE_SELECT

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun viewModelProvider() = this

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): DialogMultichainSafeSelectionBinding =
        DialogMultichainSafeSelectionBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapter
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is MultichainSafeSelectionState.SafeListState -> {
                    adapter.setItems(state.listItems, state.activeMultichainSafe)
                    state.viewAction?.let { action ->
                        when (action) {
                            is BaseStateViewModel.ViewAction.CloseScreen -> {
                                dismiss()
                            }
                            is BaseStateViewModel.ViewAction.NavigateTo -> {
                                findNavController().navigate(action.navDirections)
                            }
                        }
                    }
                }
                is MultichainSafeSelectionState.AddSafeState -> {
                    state.viewAction?.let { action ->
                        when (action) {
                            is BaseStateViewModel.ViewAction.NavigateTo -> {
                                findNavController().navigate(action.navDirections)
                            }
                        }
                    }
                }
            }
        }

        viewModel.loadMultichainSafes()
    }
}
```

## Performance Considerations

### 1. Parallel API Calls

- Load balances from multiple chains simultaneously using `async`/`await`
- Implement timeout handling for slow chains
- Show partial results while other chains are still loading

### 2. Error Handling

- Graceful degradation when some chains fail to load
- Display which chains failed and allow retry
- Don't block UI for failed chain requests

### 3. Caching Strategy (Optional)

- Implement in-memory cache with TTL for balance data
- Cache chain information to reduce API calls
- Invalidate cache on user actions (send, receive)

### 4. Progressive Loading

- Show Safe selection immediately
- Load balances in background
- Update UI as balance data becomes available

## Design Pros and Cons Analysis

### Non-Breaking Approach (Keep Existing Files)

#### Pros:

- **Zero Risk of Breaking Existing Functionality**: Current single-chain workflow remains completely intact
- **Easy Rollback**: If multichain implementation has issues, you can easily disable it
- **Gradual Migration**: Users can be gradually migrated from old to new system
- **A/B Testing Friendly**: Can test multichain vs single-chain approaches with different user groups
- **Parallel Development**: Multiple developers can work on multichain without conflicts
- **Clear Separation of Concerns**: Old and new logic are clearly separated
- **Easier Code Review**: Reviewers only need to focus on new files

#### Cons:

- **Code Duplication**: Some logic will be duplicated between old and new implementations
- **Increased App Size**: More classes and methods mean larger APK
- **Maintenance Overhead**: Two codepaths to maintain during transition period
- **Potential Inconsistencies**: Risk of old and new implementations diverging over time
- **Complex Navigation**: Need to decide when to use old vs new flows

### UI-Based Approach (Query on Demand)

#### Pros:

- **Always Fresh Data**: No stale balance data, always queries latest from blockchain
- **No Data Sync Issues**: Don't need to worry about keeping aggregated data in sync
- **Simpler Architecture**: No complex caching/invalidation logic needed
- **Storage Efficiency**: Don't store redundant aggregated data
- **Real-time Accuracy**: Users always see current balances
- **No Migration Complexity**: Don't need to migrate existing balance storage

#### Cons:

- **Performance Impact**: Multiple API calls every time user switches or refreshes
- **Network Dependency**: Heavier reliance on network connectivity
- **User Experience**: Potentially slower loading times, especially on poor networks
- **API Rate Limiting**: More frequent API calls might hit rate limits
- **Battery Usage**: More network requests consume more battery
- **Error Handling Complexity**: Need to handle partial failures across multiple chains

## Testing Strategy

### 1. Unit Tests

- Test multichain Safe aggregation logic
- Test balance aggregation across chains
- Test error handling for partial failures

### 2. Integration Tests

- Test API calls to multiple chains
- Test UI updates with real data
- Test feature flag behavior

### 3. Performance Tests

- Measure loading times with multiple chains
- Test with slow network conditions
- Monitor memory usage with large datasets

## Migration Path

### Phase A: Development (Feature Hidden)

1. Implement all new components with feature flag disabled
2. Test thoroughly in isolation
3. No impact on existing users
4. Parallel development without conflicts

### Phase B: Internal Testing

1. Enable feature flag for development builds
2. Test with real Safe data across multiple chains
3. Performance testing and optimization
4. Bug fixes and refinements

### Phase C: Beta Testing

1. Enable for small percentage of users (5-10%)
2. Monitor crash rates and performance metrics
3. Collect user feedback
4. Iterate based on findings

### Phase D: Gradual Rollout

1. Increase rollout percentage gradually (20%, 50%, 80%)
2. Monitor key metrics at each stage
3. Rollback capability if issues arise
4. Full rollout once stable

### Phase E: Cleanup

1. Remove feature flags once fully rolled out
2. Deprecate old single-chain components
3. Clean up unused code and resources
4. Update documentation

## Risk Mitigation

### 1. Backward Compatibility

- Keep existing components functional
- Feature flag allows instant rollback
- Data models remain compatible

### 2. Performance Risks

- Implement request timeouts
- Limit concurrent API calls
- Cache frequently accessed data

### 3. User Experience Risks

- Progressive loading prevents blank screens
- Clear error messages for failed chains
- Fallback to single-chain mode if needed

## Success Metrics

### 1. Performance Metrics

- Balance loading time < 3 seconds for 3 chains
- UI responsiveness maintained
- Memory usage within acceptable limits

### 2. User Experience Metrics

- Reduced support tickets about chain switching
- Increased user engagement with multichain features
- Positive user feedback scores

### 3. Technical Metrics

- Zero critical bugs in production
- API error rate < 5%
- Successful rollout to 100% of users

## Implementation Timeline

### Week 1-2: Foundation

- Create data models (`MultichainSafe`, `MultichainBalanceData`)
- Implement `MultichainSafeRepository`
- Implement `MultichainBalanceService`
- Add feature flag infrastructure

### Week 3-4: UI Components

- Create multichain Safe selection UI
- Implement new adapters and view holders
- Create layout files
- Add navigation logic with feature flags

### Week 5-6: Balance Display

- Implement multichain assets view model
- Create balance aggregation logic
- Update toolbar and main activity integration
- Add error handling and loading states

### Week 7-8: Testing & Polish

- Unit tests for all new components
- Integration testing with real data
- Performance optimization
- UI/UX refinements

### Week 9-10: Beta Release

- Internal testing with feature flag enabled
- Bug fixes and stability improvements
- Performance monitoring setup
- Documentation updates

### Week 11-12: Gradual Rollout

- 10% user rollout
- Monitor metrics and feedback
- 50% rollout if stable
- Full rollout preparation

## Conclusion

This implementation plan provides a comprehensive approach to implementing multichain functionality while maintaining system stability and user experience. The phased approach allows for careful testing and gradual rollout, minimizing risks while delivering significant value to users.

The key benefits of this approach:

- **Non-breaking**: Existing functionality remains intact
- **Flexible**: Feature flags allow for easy rollout control
- **Performant**: Parallel loading and caching optimize user experience
- **Maintainable**: Clean separation between old and new systems
- **User-focused**: UI-driven approach prioritizes user experience

Next steps should focus on implementing Phase 1 components and establishing the foundation for multichain functionality.
