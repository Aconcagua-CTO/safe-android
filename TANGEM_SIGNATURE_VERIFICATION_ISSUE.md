# Tangem Signature Verification Issue - Technical Analysis

## Problem Summary

The Tangem integration for Safe multisig transactions is failing with HTTP 500 Internal Server Error from the Safe Transaction Service, while identical transactions using local keys succeed. The root cause appears to be a signature-hash mismatch where the Tangem card's signature doesn't verify against the transaction hash being sent to the server.

## Environment Details

- **Network**: Base Sepolia (Chain ID: 84532)
- **Gateway**: Production Safe Transaction Service (`safe-client.safe.global`)
- **Tangem SDK**: Version 3.9.1
- **Safe Transaction Service API**: `/v1/chains/84532/transactions/{safeAddress}/propose`

## Detailed Comparison

### ✅ Working Local Key Transaction

```
Transaction Parameters:
- chainId: 84532
- safeAddress: 0xCa8caAcD57a0D066dE7269f9Ea07EFC3c0477E88
- toAddress: 0xD599d8C61b4616b06bF28735E6d9Fc51D1Bc6fdE
- value: 1000000000000000 (0.001 ETH)
- nonce: 0
- safeTxHash: 0x5969b957db0ee83a19a9802753512a7b155e6013190f0f2f4ec2cfb0ebf9b9cc
- sender: 0x25126d74Dd05Ba4c7Ab3E2B5F392531164cbC57A
- signature: 0x55dbb9e0f8cee47ba6bdbfe7b1cc7f7162a7817eccb8380616c65101cc4a9e586eb48f13dea7e8d6f559b1ecedfdf32ba14ee262beb4a7f7295d73e5f3aa29291b

API Request:
POST https://safe-client.safe.global/v1/chains/84532/transactions/0xCa8caAcD57a0D066dE7269f9Ea07EFC3c0477E88/propose

Result: ✅ HTTP 200 OK - Transaction queued successfully
Response: {"txStatus":"AWAITING_EXECUTION", ...}
```

### ❌ Failing Tangem Transaction

```
Transaction Parameters:
- chainId: 84532
- safeAddress: 0x9d169744530bC71951E4ca2Dc02bABef13D1C041
- toAddress: 0xD599d8C61b4616b06bF28735E6d9Fc51D1Bc6fdE (SAME recipient)
- value: 1000000000000000 (SAME amount: 0.001 ETH)
- nonce: 0
- safeTxHash: 0xf4f71ddc61b38d2ecdaa5384acdf94d291ce7a92b6d9484f586851589b8ff232
- sender: 0xfaa251f9Fd1d5613a0F729B46A200Ac90A92F6dF
- signature: 0xf00885910667dc53eac5385cfb7fbeac21792b8199c14327ca72d938a73f47a56f4f18a658e630fb68826037f9b8790d286871a6a8283a8e855adf94111475b904

API Request:
POST https://safe-client.safe.global/v1/chains/84532/transactions/0x9d169744530bC71951E4ca2Dc02bABef13D1C041/propose

Result: ❌ HTTP 500 Internal Server Error
Response: {"code":500,"message":"Internal server error"}
```

## Critical Observation: Signature-Hash Mismatch

### The Problem

```
Expected safeTxHash: 0xf4f71ddc61b38d2ecdaa5384acdf94d291ce7a92b6d9484f586851589b8ff232
Actual signature:    0xf00885910667dc53eac5385cfb7fbeac21792b8199c14327ca72d938a73f47a5...
```

**The signature does NOT start with the same bytes as the hash!** This indicates the Tangem card signed a different hash than what the server expects.

### Tangem Signing Flow Analysis

From the logs, the Tangem signing process:

```
1. TangemSignViewModel: Generating preview hash for: 0xf4f71ddc61b38d2ecdaa5384acdf94d291ce7a92b6d9484f586851589b8ff232
2. TangemSignViewModel: Generated preview hash: C48F2C6C9FD4463DC429B54614A90A35184F26E21E5D51A30702216C2946C0DD
3. TangemSignViewModel: Hash bytes: f4f71ddc61b38d2ecdaa5384acdf94d291ce7a92b6d9484f586851589b8ff232
4. TangemController: Direct hash signing successful in single session
5. TangemSignViewModel: Raw signature: f00885910667dc53eac5385cfb7fbeac21792b8199c14327ca72d938a73f47a56f4f18a658e630fb68826037f9b8790d286871a6a8283a8e855adf94111475b9
6. TangemSignViewModel: Converting to ECDSA format
7. TangemSignViewModel: r: f00885910667dc53eac5385cfb7fbeac21792b8199c14327ca72d938a73f47a5
8. TangemSignViewModel: s: 6f4f18a658e630fb68826037f9b8790d286871a6a8283a8e855adf94111475b9
9. TangemSignViewModel: Using recovery ID 0 + 4 = 4 (hex: 04)
10. TangemSignViewModel: Final ECDSA signature: 0x...04
```

## Potential Root Causes

### 1. Hash Transformation Issue

The "Generated preview hash" (`C48F2C6C9FD4463DC429B54614A90A35184F26E21E5D51A30702216C2946C0DD`) doesn't match the "Hash bytes" being signed (`f4f71ddc61b38d2ecdaa5384acdf94d291ce7a92b6d9484f586851589b8ff232`).

**Question**: Is the Tangem card signing the preview hash or the original hash bytes?

### 2. Hash Calculation Differences

The local key flow uses `signWithOwner` fallback, while Tangem uses direct hash signing. There might be different hash calculation methods:

- **Local Key**: Uses standard Safe hash calculation
- **Tangem**: May be using a different hash derivation or encoding

### 3. Signature Format Issues

While the signature format appears correct (130 chars, ends with `04`), the signature verification fails on the server side.

### 4. Recovery ID Calculation

Both transactions use recovery ID `4` (hex: `04`), but the server might expect a different recovery method for different signing approaches.

## Technical Questions for Second Opinion

### 1. Safe Transaction Hash Calculation

```javascript
// Standard Safe transaction hash calculation
// Should this be identical for both local keys and hardware wallets?
const safeTxHash = calculateSafeTxHash(
  safeAddress,
  to,
  value,
  data,
  operation,
  safeTxGas,
  baseGas,
  gasPrice,
  gasToken,
  refundReceiver,
  nonce
);
```

### 2. Tangem Signature Process

```kotlin
// Current Tangem implementation
val hashBytes = safeTxHash.hexToByteArray()
val signature = tangemSdk.signHash(hashBytes, cardId, derivationPath)
val ecdsaSignature = convertToECDSAFormat(signature)
```

**Is this the correct approach, or should we be signing a different hash format?**

### 3. Server-Side Verification

The Safe Transaction Service likely performs:

```javascript
// Server-side verification (pseudocode)
const recoveredAddress = ecRecover(safeTxHash, signature);
if (recoveredAddress !== senderAddress) {
    return HTTP 500 "Internal server error";
}
```

**Why would this fail for Tangem but succeed for local keys?**

## Request for Second Opinion

### Primary Questions:

1. **Hash Calculation**: Should the Safe transaction hash calculation be identical regardless of signing method?
2. **Signature Verification**: What could cause the signature to not verify against the hash on the server side?
3. **Tangem Integration**: Are there known issues with Tangem card signatures in Safe multisig contexts?
4. **Debugging Approach**: What's the best way to debug signature verification issues with the Safe Transaction Service?

### Specific Technical Points:

1. The signature format appears correct (130 characters, proper r/s/v structure)
2. The recovery ID calculation follows the same pattern as working local keys
3. All transaction parameters are identical except for the Safe addresses and owner addresses
4. The API endpoint and request format are identical

### Expected Outcome:

The Tangem signature should verify correctly against the Safe transaction hash, allowing the transaction to be queued successfully just like the local key transaction.

## Code References

The issue likely lies in one of these components:

- `TangemSignViewModel.kt`: Hash calculation and signature conversion
- `TangemController.kt`: Direct hash signing implementation
- `SendAssetReviewViewModel.kt`: Transaction preparation and submission
- Safe Transaction Service: Server-side signature verification

Any insights on the signature verification process or potential Tangem-specific considerations would be greatly appreciated.
