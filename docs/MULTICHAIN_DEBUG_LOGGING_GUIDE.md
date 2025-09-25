# Multichain Debug Logging Guide

## Overview

Comprehensive debug logging has been added to all Phase 1 and Phase 2 multichain components. This logging is **only active in debug builds** and provides detailed insights into the multichain functionality.

## Log Tags and Components

### 🏷️ **Log Tags by Component**

| Component                          | Log Tag                            | Purpose                                     |
| ---------------------------------- | ---------------------------------- | ------------------------------------------- |
| `MultichainFeatureFlag`            | `MultichainFeatureFlag`            | Feature flag state changes and checks       |
| `MultichainSafeRepository`         | `MultichainSafeRepository`         | Safe aggregation and active Safe management |
| `MultichainBalanceService`         | `MultichainBalanceService`         | Balance loading across multiple chains      |
| `MultichainNavigationHelper`       | `MultichainNavigationHelper`       | Navigation routing decisions                |
| `MultichainSafeSelectionViewModel` | `MultichainSafeSelectionViewModel` | Safe selection logic and state              |
| `MultichainSafeSelectionAdapter`   | `MultichainSafeSelectionAdapter`   | UI adapter operations                       |
| `MultichainSafeItemViewHolder`     | `MultichainSafeItemViewHolder`     | Individual Safe item binding                |
| `MultichainSafeSelectionDialog`    | `MultichainSafeSelectionDialog`    | Dialog lifecycle and setup                  |
| `AdvancedAppSettings`              | `AdvancedAppSettings`              | Settings toggle interactions                |

## 📋 **How to View Logs**

### **Method 1: Android Studio Logcat**

1. Connect your device
2. Open Android Studio
3. Go to View → Tool Windows → Logcat
4. Filter by any of the tags above (e.g., `MultichainFeatureFlag`)

### **Method 2: ADB Command Line**

```bash
# View all multichain logs
adb logcat | grep -E "Multichain|multichain"

# View specific component logs
adb logcat | grep "MultichainFeatureFlag"
adb logcat | grep "MultichainSafeRepository"
adb logcat | grep "MultichainNavigationHelper"
```

### **Method 3: Filtered Logcat**

```bash
# View only multichain-related logs with timestamps
adb logcat -s MultichainFeatureFlag:D MultichainSafeRepository:D MultichainNavigationHelper:D MultichainSafeSelectionViewModel:D
```

## 🧪 **Testing Scenarios and Expected Logs**

### **Scenario 1: Toggle Multichain Mode**

**Action:** Go to Settings → Advanced → Toggle "Enable multichain mode"

**Expected Logs:**

```
D/AdvancedAppSettings: User enabled multichain mode
D/MultichainFeatureFlag: enableMultichainMode() - enabling multichain mode
```

### **Scenario 2: Open Safe Selection Dialog**

**Action:** Tap Safe selection button in main toolbar

**Expected Logs:**

```
D/MultichainFeatureFlag: isMultichainEnabled() = true
D/MultichainFeatureFlag: shouldShowMultichainFeatures() = true (enabled=true, stable=true)
D/MultichainNavigationHelper: navigateToSafeSelection() - useMultichain: true
D/MultichainNavigationHelper: navigateToSafeSelection() - navigating to multichain safe selection dialog
D/MultichainSafeSelectionDialog: onViewCreated() - multichain safe selection dialog created
D/MultichainSafeSelectionDialog: onViewCreated() - loading multichain safes
```

### **Scenario 3: Load Multichain Safes**

**Action:** Dialog opens and loads Safes

**Expected Logs:**

```
D/MultichainSafeSelectionViewModel: loadMultichainSafes() - starting to load multichain safes
D/MultichainSafeRepository: getMultichainSafes() - fetching all multichain safes
D/MultichainSafeRepository: getMultichainSafes() - found X individual safes
D/MultichainSafeRepository: getMultichainSafes() - grouping Y safes for address 0xABC...
D/MultichainSafeRepository:   - Safe on chain 1 (Ethereum)
D/MultichainSafeRepository:   - Safe on chain 137 (Polygon)
D/MultichainSafeRepository: getMultichainSafes() - returning Z multichain safes
D/MultichainSafeRepository:   - My Safe: Ethereum, Polygon
D/MultichainSafeSelectionViewModel: loadMultichainSafes() - loaded Z multichain safes
D/MultichainSafeSelectionViewModel: loadMultichainSafes() - active safe: My Safe
D/MultichainSafeSelectionAdapter: setItems() - setting N items, active safe: My Safe
```

### **Scenario 4: Bind Safe Items in UI**

**Action:** Dialog displays Safe items

**Expected Logs:**

```
D/MultichainSafeItemViewHolder: bind() - binding multichain safe: My Safe, selected: true
D/MultichainSafeItemViewHolder: bind() - address: 0xABC...DEF
D/MultichainSafeItemViewHolder: bind() - chains: Ethereum, Polygon
```

### **Scenario 5: Select a Different Safe**

**Action:** Tap on a different Safe in the dialog

**Expected Logs:**

```
D/MultichainSafeItemViewHolder: bind() - multichain safe clicked: Other Safe
D/MultichainSafeSelectionViewModel: onMultichainSafeClicked() - safe: Other Safe (0x123...456)
D/MultichainSafeRepository: setActiveMultichainSafe() - setting active safe: Other Safe (0x123...456) on 2 chains
```

### **Scenario 6: Balance Loading (Future Phase 3)**

**Action:** When balance loading is implemented

**Expected Logs:**

```
D/MultichainBalanceService: loadAggregatedBalances() - loading balances for My Safe across 2 chains
D/MultichainBalanceService: loadAggregatedBalances() - chains: Ethereum, Polygon
D/MultichainBalanceService: loadAggregatedBalances() - fiat: USD, timeout: 30000ms
D/MultichainBalanceService: loadAggregatedBalances() - completed: 2 successful, 0 failed
D/MultichainBalanceService: loadAggregatedBalances() - total fiat value: 1234.56 USD
```

## 🔍 **Debugging Common Issues**

### **Issue: Multichain Dialog Not Showing**

**Check These Logs:**

```bash
adb logcat | grep -E "MultichainFeatureFlag|MultichainNavigationHelper"
```

**Expected Flow:**

1. `MultichainFeatureFlag: isMultichainEnabled() = true`
2. `MultichainNavigationHelper: navigating to multichain safe selection dialog`

**If Missing:** Feature flag is disabled or navigation helper not working

### **Issue: No Multichain Safes Displayed**

**Check These Logs:**

```bash
adb logcat | grep -E "MultichainSafeRepository|MultichainSafeSelectionViewModel"
```

**Expected Flow:**

1. `MultichainSafeRepository: getMultichainSafes() - found X individual safes`
2. `MultichainSafeRepository: getMultichainSafes() - returning Y multichain safes`

**If Missing:** No Safes in database or grouping logic issue

### **Issue: Safe Selection Not Working**

**Check These Logs:**

```bash
adb logcat | grep -E "MultichainSafeItemViewHolder|MultichainSafeSelectionViewModel"
```

**Expected Flow:**

1. `MultichainSafeItemViewHolder: bind() - multichain safe clicked`
2. `MultichainSafeSelectionViewModel: onMultichainSafeClicked()`
3. `MultichainSafeRepository: setActiveMultichainSafe()`

## 🚀 **Testing Instructions**

### **Step 1: Enable Logging**

```bash
# Clear logcat and start fresh
adb logcat -c

# Start filtered logging for multichain components
adb logcat | grep -E "Multichain|multichain|AdvancedAppSettings"
```

### **Step 2: Install Updated APK**

```bash
# Install the debug APK with logging
adb install app/build/outputs/apk/debug/safe-16-debug.apk
```

### **Step 3: Test Feature Flag**

1. Open app
2. Go to Settings → Advanced
3. Toggle "Enable multichain mode"
4. **Expected Log:** `User enabled multichain mode`

### **Step 4: Test Safe Selection**

1. Go back to main screen
2. Tap Safe selection button (dropdown in toolbar)
3. **Expected Logs:** Navigation and dialog creation logs

### **Step 5: Test Safe Loading**

1. Dialog should open
2. **Expected Logs:** Safe repository and ViewModel loading logs

### **Step 6: Test Safe Selection**

1. Tap on a Safe in the dialog
2. **Expected Logs:** Click handling and active Safe setting logs

## 📊 **Log Analysis Examples**

### **Successful Multichain Flow:**

```
D/AdvancedAppSettings: User enabled multichain mode
D/MultichainFeatureFlag: enableMultichainMode() - enabling multichain mode
D/MultichainFeatureFlag: isMultichainEnabled() = true
D/MultichainNavigationHelper: navigateToSafeSelection() - useMultichain: true
D/MultichainNavigationHelper: navigateToSafeSelection() - navigating to multichain safe selection dialog
D/MultichainSafeSelectionDialog: onViewCreated() - multichain safe selection dialog created
D/MultichainSafeSelectionViewModel: loadMultichainSafes() - starting to load multichain safes
D/MultichainSafeRepository: getMultichainSafes() - fetching all multichain safes
D/MultichainSafeRepository: getMultichainSafes() - found 3 individual safes
D/MultichainSafeRepository: getMultichainSafes() - grouping 2 safes for address 0x1234...
D/MultichainSafeRepository:   - Safe on chain 1 (Ethereum)
D/MultichainSafeRepository:   - Safe on chain 137 (Polygon)
D/MultichainSafeRepository: getMultichainSafes() - returning 2 multichain safes
D/MultichainSafeRepository:   - My Safe: Ethereum, Polygon
D/MultichainSafeRepository:   - Company Safe: Ethereum
D/MultichainSafeSelectionAdapter: setItems() - setting 3 items, active safe: My Safe
```

### **Feature Flag Disabled Flow:**

```
D/MultichainFeatureFlag: isMultichainEnabled() = false
D/MultichainNavigationHelper: navigateToSafeSelection() - useMultichain: false
D/MultichainNavigationHelper: navigateToSafeSelection() - navigating to standard safe selection dialog
```

## 🛠️ **Troubleshooting**

### **No Logs Appearing:**

- Ensure you're using a debug build
- Check logcat filters are correct
- Verify device is connected properly

### **Multichain Dialog Not Opening:**

- Check feature flag logs
- Verify navigation helper logs
- Ensure multichain mode is enabled

### **No Safes Showing:**

- Check repository logs for Safe count
- Verify database has Safes
- Check grouping logic logs

## 📝 **Log Output Examples**

### **Example 1: Fresh App Start with Multichain ON**

```
D/MultichainFeatureFlag: isMultichainEnabled() = true
D/MultichainFeatureFlag: shouldShowMultichainFeatures() = true (enabled=true, stable=true)
```

### **Example 2: Safe Repository Grouping**

```
D/MultichainSafeRepository: getMultichainSafes() - fetching all multichain safes
D/MultichainSafeRepository: getMultichainSafes() - found 4 individual safes
D/MultichainSafeRepository: getMultichainSafes() - grouping 2 safes for address 0x1234567890123456789012345678901234567890
D/MultichainSafeRepository:   - Safe on chain 1 (Ethereum)
D/MultichainSafeRepository:   - Safe on chain 137 (Polygon)
D/MultichainSafeRepository: getMultichainSafes() - grouping 1 safes for address 0x0987654321098765432109876543210987654321
D/MultichainSafeRepository:   - Safe on chain 1 (Ethereum)
D/MultichainSafeRepository: getMultichainSafes() - returning 2 multichain safes
D/MultichainSafeRepository:   - My Safe: Ethereum, Polygon
D/MultichainSafeRepository:   - Company Safe: Ethereum
```

### **Example 3: UI Binding**

```
D/MultichainSafeSelectionAdapter: setItems() - setting 3 items, active safe: My Safe
D/MultichainSafeItemViewHolder: bind() - binding multichain safe: My Safe, selected: true
D/MultichainSafeItemViewHolder: bind() - address: 0x1234567890123456789012345678901234567890
D/MultichainSafeItemViewHolder: bind() - chains: Ethereum, Polygon
D/MultichainSafeItemViewHolder: bind() - binding multichain safe: Company Safe, selected: false
D/MultichainSafeItemViewHolder: bind() - address: 0x0987654321098765432109876543210987654321
D/MultichainSafeItemViewHolder: bind() - chains: Ethereum
```

## 🎯 **Key Benefits of This Logging**

1. **🔍 Debugging**: Easy to identify where issues occur in the multichain flow
2. **📊 Performance**: Can measure Safe loading and grouping performance
3. **🧪 Testing**: Verify feature flag behavior and navigation routing
4. **📈 Monitoring**: Track user interactions with multichain features
5. **🚀 Development**: Faster iteration and bug fixing

## 🚨 **Important Notes**

- **Debug Only**: All logging is wrapped in `BuildConfig.DEBUG` checks
- **Performance**: Zero impact on release builds
- **Privacy**: No sensitive data is logged (only Safe names and public addresses)
- **Comprehensive**: Covers all major operations in Phases 1 and 2

## 📱 **Ready for Testing**

The updated APK with comprehensive logging is ready:

- **Location**: `app/build/outputs/apk/debug/safe-16-debug.apk`
- **Features**: Phase 1 + Phase 2 + Comprehensive Debug Logging
- **Default**: Multichain mode ON by default
- **Logging**: All components instrumented for debugging

Install the APK and start testing - the logs will provide detailed insights into exactly how the multichain functionality is working!
