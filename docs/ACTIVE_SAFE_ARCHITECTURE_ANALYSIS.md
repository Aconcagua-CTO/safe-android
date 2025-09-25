# Active Safe Architecture Analysis

## 🚨 **Critical Design Issue: Dual Active Safe Systems**

You've identified a fundamental architectural challenge in our multichain implementation. Let me explain the current situation and the design decisions we need to make.

## 📊 **Current Architecture - Two Separate Active Safe Systems**

### **🔄 System 1: Single-Chain Active Safe (Existing)**

**Storage Location:** `SharedPreferences`

```kotlin
// Key: "prefs.string.active_safe"
// Value: "0xABC...;SafeName;chainId"
private const val ACTIVE_SAFE = "prefs.string.active_safe"
```

**How it works:**

```kotlin
// SafeRepository.kt
fun setActiveSafe(safe: Safe) {
    preferencesManager.prefs.edit {
        putString(ACTIVE_SAFE, "${safe.address.asEthereumAddressString()};${safe.localName};${safe.chainId}")
    }
}

fun getActiveSafe(): Safe? {
    // Parses "address;name;chainId" and returns specific Safe on specific chain
}
```

**What it stores:** **Specific Safe on specific chain**

- Address: `0xAc6a...CB08`
- Chain: `84532` (Base Sepolia)
- Result: **One specific Safe instance**

### **🔄 System 2: Multichain Active Safe (New)**

**Storage Location:** `SharedPreferences`

```kotlin
// Key: "prefs.string.active_multichain_safe"
// Value: "0xABC..."
private const val ACTIVE_MULTICHAIN_SAFE = "prefs.string.active_multichain_safe"
```

**How it works:**

```kotlin
// MultichainSafeRepository.kt
fun setActiveMultichainSafe(multichainSafe: MultichainSafe) {
    preferencesManager.prefs.edit {
        putString(ACTIVE_MULTICHAIN_SAFE, multichainSafe.address.asEthereumAddressString())
    }
}

fun getActiveMultichainSafe(): MultichainSafe? {
    // Gets address and finds all Safes with that address across all chains
}
```

**What it stores:** **Safe address only (no chain)**

- Address: `0xAc6a...CB08`
- Chains: **All chains** where this address exists
- Result: **Multichain Safe aggregating all chains**

## 🔍 **The Problem: Two Independent Systems**

### **Current State:**

```
Single-Chain Active Safe:
├─ Key: "prefs.string.active_safe"
├─ Value: "0xAc6a...CB08;safe tangem test 3;84532"
└─ Result: Safe on Base Sepolia only

Multichain Active Safe:
├─ Key: "prefs.string.active_multichain_safe"
├─ Value: "0xAc6a...CB08"
└─ Result: Safe on Base Sepolia + Sepolia
```

### **❌ Issues:**

1. **Two separate storage locations** - can get out of sync
2. **Different data formats** - single-chain stores chain ID, multichain doesn't
3. **Independent updates** - changing one doesn't update the other
4. **UI confusion** - different components read different active Safes

## 🎯 **Design Decision Required**

We need to decide on the **Active Safe Strategy** for multichain mode:

### **Option A: Dual Active Safe System (Current)**

**Keep both systems running in parallel:**

```kotlin
// When multichain mode is ON:
getActiveSafe() -> Safe on specific chain (for backward compatibility)
getActiveMultichainSafe() -> MultichainSafe across all chains (for new features)

// When multichain mode is OFF:
getActiveSafe() -> Safe on specific chain (existing behavior)
getActiveMultichainSafe() -> null (not used)
```

**✅ Pros:**

- **Backward Compatibility**: Existing code continues to work
- **Gradual Migration**: Can migrate components one by one
- **Fallback Safety**: If multichain fails, single-chain still works
- **Clear Separation**: Old and new systems are independent

**❌ Cons:**

- **Synchronization Complexity**: Must keep both systems in sync
- **Data Duplication**: Same information stored in two places
- **Potential Inconsistencies**: Systems can drift apart
- **Developer Confusion**: Two sources of truth

### **Option B: Unified Active Safe System**

**Replace single-chain with multichain when enabled:**

```kotlin
// When multichain mode is ON:
getActiveSafe() -> First Safe from active multichain Safe (compatibility layer)
getActiveMultichainSafe() -> Primary source of truth

// When multichain mode is OFF:
getActiveSafe() -> Safe on specific chain (existing behavior)
getActiveMultichainSafe() -> null
```

**✅ Pros:**

- **Single Source of Truth**: No synchronization issues
- **Simpler Logic**: One active Safe concept
- **Better Consistency**: Cannot get out of sync
- **Cleaner Architecture**: Less complexity

**❌ Cons:**

- **Migration Complexity**: Need to update existing components
- **Potential Breaking Changes**: Risk of affecting existing functionality
- **Chain Selection**: How to choose which chain from multichain Safe?

### **Option C: Smart Migration System (Recommended)**

**Automatically synchronize between systems:**

```kotlin
// Migration Helper automatically maintains synchronization:

when multichain mode is ENABLED:
1. If single-chain active Safe exists → Create/set multichain active Safe
2. If multichain active Safe exists → Set single-chain to first chain from multichain
3. Keep both synchronized on every change

when multichain mode is DISABLED:
1. Use only single-chain active Safe
2. Clear multichain active Safe
```

**✅ Pros:**

- **Best of Both Worlds**: Backward compatibility + new functionality
- **Automatic Sync**: Migration helper handles synchronization
- **Seamless Transitions**: Smooth switching between modes
- **Gradual Rollout**: Can enable/disable safely

**❌ Cons:**

- **Initial Complexity**: Need to implement migration logic
- **Chain Selection Logic**: Need rules for choosing default chain

## 🔧 **Current Implementation Analysis**

### **What We Have:**

```kotlin
// MultichainMigrationHelper.kt - Lines 45-75
private suspend fun migrateToMultichainActive() {
    val currentSingleChainSafe = safeRepository.getActiveSafe()
    val currentMultichainSafe = multichainSafeRepository.getActiveMultichainSafe()

    when {
        // Case 1: No multichain safe set, but single-chain safe exists
        currentMultichainSafe == null && currentSingleChainSafe != null -> {
            val multichainSafe = multichainSafeRepository.getMultichainSafeByAddress(currentSingleChainSafe.address)
            if (multichainSafe != null) {
                multichainSafeRepository.setActiveMultichainSafe(multichainSafe) // ✅ This should work
            }
        }
        // ... other cases
    }
}
```

### **❌ Current Issue:**

Based on your logs showing `multichain safe changed: none`, the migration is **not being triggered automatically** on app startup.

## 🎯 **The Solution: Automatic Migration on Startup**

### **Root Cause:**

The migration helper exists but is **not being called automatically** when the app starts. We need to trigger migration during app initialization.

### **✅ Proposed Fix:**

#### **1. Trigger Migration on App Startup:**

```kotlin
// In StartActivity.onCreate() or Application.onCreate()
lifecycleScope.launch {
    multichainMigrationHelper.synchronizeActiveSafe()
}
```

#### **2. Trigger Migration on Feature Flag Toggle:**

```kotlin
// In AdvancedAppSettingsFragment (already implemented)
multichainMigrationHelper.handleFeatureFlagToggle(enabled)
```

#### **3. Trigger Migration on Safe Selection:**

```kotlin
// In MultichainSafeSelectionViewModel.selectMultichainSafe()
multichainSafeRepository.setActiveMultichainSafe(multichainSafe)
// Also update single-chain for backward compatibility:
safeRepository.setActiveSafe(multichainSafe.safesPerChain.values.first())
```

## 📊 **Expected Behavior After Fix**

### **Scenario 1: App Startup with Multichain ON**

```
1. App starts
2. debugSafeStatus() shows:
   - active single-chain safe: safe tangem test 3 (Base Sepolia)
   - active multichain safe: none
   - migration synchronized: false

3. Migration triggers:
   - Finds single-chain active Safe
   - Creates multichain Safe from same address
   - Sets active multichain Safe
   - Synchronization complete

4. Result:
   - active single-chain safe: safe tangem test 3 (Base Sepolia)
   - active multichain safe: safe tangem test 3 (2 chains)
   - migration synchronized: true
```

### **Scenario 2: Safe Selection Working**

```
1. User taps Safe selection button
2. Logs: "Safe selection button clicked!"
3. Navigation: "navigating to multichain safe selection dialog"
4. Dialog shows: "safe tangem test 3" with "Base Sepolia, Sepolia"
5. User selects Safe
6. Both active Safes updated and synchronized
```

## 🛠️ **Implementation Strategy**

### **Phase 4.1: Add Automatic Migration**

1. **Trigger migration on app startup**
2. **Trigger migration on feature flag toggle**
3. **Trigger migration on Safe selection**
4. **Add comprehensive logging for all migrations**

### **Phase 4.2: Enhanced Synchronization**

1. **Bidirectional sync**: Single-chain ↔ Multichain
2. **Conflict resolution**: Rules for handling mismatches
3. **Error handling**: Fallback strategies

### **Phase 4.3: Testing Strategy**

1. **Fresh app installs**: Verify migration works
2. **Feature flag toggling**: Verify synchronization
3. **Safe selection**: Verify both systems update
4. **Edge cases**: No Safes, network errors, etc.

## 🎯 **Your Specific Issue**

### **Current Problem:**

```
✅ Multichain mode: ON
✅ Single-chain active Safe: "safe tangem test 3" on Base Sepolia
❌ Multichain active Safe: none (not set)
❌ Migration: Not triggered automatically
❌ Safe selection button: Not responding (probably due to no active multichain Safe)
```

### **✅ Solution:**

1. **Add automatic migration on app startup**
2. **Trigger migration when feature flag is enabled**
3. **Ensure Safe selection updates both systems**

The migration helper code is correct, but it's **not being called automatically**. Once we trigger the migration, you should see:

```
D/StartActivity: debugSafeStatus() - active multichain safe: safe tangem test 3
D/StartActivity: debugSafeStatus() - migration synchronized: true
```

And then the Safe selection button should work properly!

**Would you like me to implement the automatic migration triggers to fix this issue?**
