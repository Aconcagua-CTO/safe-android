# Native Token Grouping Analysis

## 🚨 **Critical Issue Identified**

Based on the API response analysis and codebase review, there's an important design decision needed for how native tokens should be grouped across chains.

## 📊 **Current Native Token Landscape**

### **ETH-Based Chains:**

| Chain            | Chain ID | Native Token       | Symbol | Address       |
| ---------------- | -------- | ------------------ | ------ | ------------- |
| Ethereum Mainnet | 1        | Ether              | ETH    | 0x0000...0000 |
| Sepolia Testnet  | 11155111 | Sepolia Ether      | ETH    | 0x0000...0000 |
| Base Mainnet     | 8453     | Ether              | ETH    | 0x0000...0000 |
| Base Sepolia     | 84532    | Base Sepolia Ether | ETH    | 0x0000...0000 |
| Arbitrum         | 42161    | Ether              | ETH    | 0x0000...0000 |
| Polygon          | 137      | Matic              | MATIC  | 0x0000...0000 |

### **Non-ETH Chains:**

| Chain             | Chain ID | Native Token       | Symbol | Address       |
| ----------------- | -------- | ------------------ | ------ | ------------- |
| Rootstock Mainnet | 30       | Smart Bitcoin      | RBTC   | 0x0000...0000 |
| Rootstock Testnet | 31       | Test Smart Bitcoin | tRBTC  | 0x0000...0000 |
| Gnosis Chain      | 100      | xDAI               | xDAI   | 0x0000...0000 |

## 🔍 **The Grouping Challenge**

### **Current Implementation Issue:**

```kotlin
// Current grouping logic - PROBLEMATIC for native tokens
val tokenAddress = balance.tokenInfo.address.asEthereumAddressChecksumString()
tokenGroups.getOrPut(tokenAddress) { mutableListOf() }
```

### **❌ Problem:**

All native tokens use the **same address** (`0x0000000000000000000000000000000000000000`), so they would be incorrectly grouped together:

**Wrong Grouping:**

```
"0x0000000000000000000000000000000000000000" -> {
  ETH from Ethereum: 1.5 ETH
  ETH from Base: 0.3 ETH
  RBTC from Rootstock: 0.1 RBTC
  MATIC from Polygon: 100 MATIC
}
// This would show: "4.9 ETH" (incorrect!)
```

## ✅ **Proposed Solution: Enhanced Token Grouping**

### **New Grouping Strategy:**

```kotlin
fun getTokenGroupingKey(balance: Balance): String {
    val address = balance.tokenInfo.address.asEthereumAddressChecksumString()
    val symbol = balance.tokenInfo.symbol
    val isNativeToken = balance.tokenInfo.tokenType == TokenType.NATIVE_CURRENCY ||
                       address.equals("0x0000000000000000000000000000000000000000", ignoreCase = true)

    return if (isNativeToken) {
        "NATIVE_${symbol.uppercase()}" // Group by symbol for native tokens
    } else {
        "ERC20_$address" // Group by address for ERC20 tokens
    }
}
```

### **✅ Correct Grouping:**

```
"NATIVE_ETH" -> {
  ETH from Ethereum: 1.5 ETH
  ETH from Base: 0.3 ETH
  ETH from Sepolia: 0.5 ETH
  ETH from Base Sepolia: 0.01 ETH
}
// Shows: "2.31 ETH" ✅

"NATIVE_RBTC" -> {
  RBTC from Rootstock: 0.1 RBTC
}
// Shows: "0.1 RBTC" ✅

"NATIVE_MATIC" -> {
  MATIC from Polygon: 100 MATIC
}
// Shows: "100 MATIC" ✅
```

## 🎯 **Real-World Examples**

### **Your Current Case:**

Based on your logs:

```
Sepolia: 0.5 ETH (symbol: "ETH", address: "0x0000...0000")
Base Sepolia: 0.01 ETH (symbol: "ETH", address: "0x0000...0000")
```

**With Enhanced Grouping:**

- **Grouping Key**: `NATIVE_ETH`
- **Total Balance**: 0.51 ETH ✅
- **Display**: "ETH - Available on Sepolia, Base Sepolia"

### **Multi-Network ETH Example:**

```
Ethereum: 2.0 ETH
Base: 0.5 ETH
Arbitrum: 1.2 ETH
Sepolia: 0.3 ETH
```

**Result:**

- **Grouping Key**: `NATIVE_ETH`
- **Total Balance**: 4.0 ETH
- **Display**: "ETH - Available on Ethereum, Base, Arbitrum, Sepolia"

### **Mixed Native Tokens Example:**

```
Ethereum: 1.0 ETH
Rootstock: 0.05 RBTC
Polygon: 500 MATIC
```

**Result:**

- **Group 1**: `NATIVE_ETH` → "1.0 ETH on Ethereum"
- **Group 2**: `NATIVE_RBTC` → "0.05 RBTC on Rootstock"
- **Group 3**: `NATIVE_MATIC` → "500 MATIC on Polygon"

## 🔧 **Implementation Strategy**

### **Phase 3.1: Enhanced Token Grouping**

#### **1. Update MultichainBalanceService:**

```kotlin
// Replace current grouping logic
balanceData.balancesByChain.forEach { (chain, coinBalances) ->
    coinBalances.items.forEach { balance ->
        val groupingKey = TokenGroupingStrategy.getTokenGroupingKey(balance)
        tokenGroups.getOrPut(groupingKey) { mutableListOf() }
            .add(chain to balance)
    }
}
```

#### **2. Update UI Display Logic:**

```kotlin
tokenGroups.forEach { (groupingKey, chainBalances) ->
    val displayName = TokenGroupingStrategy.getTokenDisplayName(groupingKey, chainBalances)
    val chainsString = TokenGroupingStrategy.getChainsDisplayString(chainBalances)

    // Create UI item with proper grouping
}
```

### **Phase 3.2: Testing Strategy**

#### **Test Cases:**

1. **Same Native Token**: ETH across multiple ETH-compatible chains
2. **Different Native Tokens**: ETH + RBTC + MATIC on different chains
3. **Mixed Tokens**: Native + ERC20 tokens across chains
4. **Edge Cases**: Chains with no balances, API failures

## 📊 **Expected Behavior**

### **Your Current Setup (ETH only):**

```
Input:
- Sepolia: 0.5 ETH
- Base Sepolia: 0.01 ETH

Output:
- Group: "NATIVE_ETH"
- Total: 0.51 ETH
- Display: "ETH - Available on Sepolia, Base Sepolia"
```

### **Complex Multi-Token Scenario:**

```
Input:
- Ethereum: 1.0 ETH + 100 USDC
- Base: 0.5 ETH + 50 USDC
- Rootstock: 0.1 RBTC
- Polygon: 200 MATIC + 75 USDC

Output:
Group 1: "NATIVE_ETH"
- Total: 1.5 ETH
- Display: "ETH - Available on Ethereum, Base"

Group 2: "NATIVE_RBTC"
- Total: 0.1 RBTC
- Display: "RBTC - Available on Rootstock"

Group 3: "NATIVE_MATIC"
- Total: 200 MATIC
- Display: "MATIC - Available on Polygon"

Group 4: "ERC20_0x[USDC_ADDRESS]"
- Total: 225 USDC
- Display: "USDC - Available on Ethereum, Base, Polygon"
```

## 🎯 **Recommendation**

### **✅ Implement Enhanced Grouping:**

1. **Group native tokens by symbol** (ETH, RBTC, MATIC separately)
2. **Group ERC20 tokens by address** (same contract across chains)
3. **Add comprehensive logging** to verify grouping behavior
4. **Test with your current ETH setup** to validate

### **🧪 Testing Priority:**

1. **Your Current Case**: Verify 0.51 ETH total for Sepolia + Base Sepolia
2. **Add Rootstock Safe**: Test ETH vs RBTC separation
3. **Add ERC20 Tokens**: Test cross-chain ERC20 aggregation

## 🚨 **Critical Questions for You:**

### **1. Native Token Display Preference:**

**Option A**: Show separate entries

- "1.5 ETH (Ethereum, Base)"
- "0.1 RBTC (Rootstock)"

**Option B**: Show unified "Native Currency" section

- "Native Assets: 1.5 ETH, 0.1 RBTC, 200 MATIC"

### **2. Cross-Chain ERC20 Handling:**

**Should USDC on Ethereum + USDC on Polygon be:**

- ✅ **Grouped together**: "225 USDC (Ethereum, Polygon)"
- ❌ **Kept separate**: "200 USDC (Ethereum)" + "25 USDC (Polygon)"

### **3. Symbol Conflicts:**

**What if two different tokens have the same symbol on different chains?**

- Use symbol + chain for grouping?
- Use address-based grouping for all tokens?

**Would you like me to implement the enhanced token grouping strategy and test it with your current ETH setup?**
