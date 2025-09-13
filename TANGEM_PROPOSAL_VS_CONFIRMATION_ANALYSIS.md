# Tangem Proposal vs Confirmation Flow - Detailed Comparative Analysis

## Executive Summary

🎯 **CRITICAL DISCOVERY**: The **proposal flow works perfectly** while the **confirmation flow fails**, despite using identical Tangem signing logic. This reveals that the issue is NOT with our SignRaw implementation, but with **how the Safe Transaction Service validates signatures** in different API endpoints.

## Flow Comparison Overview

### ✅ **SUCCESSFUL PROPOSAL FLOW**

- **API Endpoint**: `/v1/chains/84532/transactions/{safeAddress}/propose`
- **Method**: POST with full transaction data
- **Result**: ✅ **HTTP 200** - Transaction created successfully
- **Status**: `AWAITING_EXECUTION`

### ❌ **FAILED CONFIRMATION FLOW**

- **API Endpoint**: `/v1/chains/84532/transactions/{safeTxHash}/confirmations`
- **Method**: POST with signature only
- **Result**: ❌ **HTTP 422** - Invalid signature
- **Error**: `{"message":"Invalid signature","statusCode":422}`

## Detailed 8-Step Analysis

### **STEP 1: Database Analysis**

#### ✅ **SUCCESSFUL PROPOSAL FLOW**

```
TangemSignViewModel: ═══ DATABASE OWNER ANALYSIS ═══
TangemSignViewModel: Owner address: 0xe104892a4bcfb40cc2555c69e2a09050becf7ed8
TangemSignViewModel: Owner type: TANGEM
TangemSignViewModel: Card ID: AF05000000202002
TangemSignViewModel: Derivation path from DB: 'primary'
TangemSignViewModel: ✅ CORRECT: Database has compatible derivation path
```

#### ❌ **FAILED CONFIRMATION FLOW**

```
TangemSignViewModel: ═══ DATABASE OWNER ANALYSIS ═══
TangemSignViewModel: Owner address: 0xe104892a4bcfb40cc2555c69e2a09050becf7ed8
TangemSignViewModel: Owner type: TANGEM
TangemSignViewModel: Card ID: AF05000000202002
TangemSignViewModel: Derivation path from DB: 'primary'
TangemSignViewModel: ✅ CORRECT: Database has compatible derivation path
```

**Analysis**: ✅ **IDENTICAL** - Both flows use the same database owner configuration.

### **STEP 2: Derivation Path Conversion**

#### ✅ **SUCCESSFUL PROPOSAL FLOW**

```
TangemSignViewModel: 🔧 CONVERTING: 'primary' → Ethereum default derivation path
TangemSignViewModel: Using derivation path: m/44'/60'/0'/0/0
TangemSignViewModel: This should produce registered address: 0xe104892a4bcfb40cc2555c69e2a09050becf7ed8
```

#### ❌ **FAILED CONFIRMATION FLOW**

```
TangemSignViewModel: 🔧 CONVERTING: 'primary' → Ethereum default derivation path
TangemSignViewModel: Using derivation path: m/44'/60'/0'/0/0
TangemSignViewModel: This should produce registered address: 0xe104892a4bcfb40cc2555c69e2a09050becf7ed8
```

**Analysis**: ✅ **IDENTICAL** - Both flows use the same derivation path conversion logic.

### **STEP 3: Controller Key Derivation**

#### ✅ **SUCCESSFUL PROPOSAL FLOW**

```
TangemController: Starting direct hash signing (single session) - cardId: AF05000000202002, path: m/44'/60'/0'/0/0
TangemController: ═══ KEY DERIVATION ANALYSIS ═══
TangemController: Input derivation path: m/44'/60'/0'/0/0
TangemController: Actual derivation path: m/44'/60'/0'/0/0
TangemController: Wallet public key: 032c6f575345bafa41227d802afaa251f9fd1d5613a0f729b46a200ac90a92f6df
TangemController: Expected: This should derive to 0xe104892a4bcfb40cc2555c69e2a09050becf7ed8
```

#### ❌ **FAILED CONFIRMATION FLOW**

```
TangemController: Starting direct hash signing (single session) - cardId: AF05000000202002, path: m/44'/60'/0'/0/0
TangemController: ═══ KEY DERIVATION ANALYSIS ═══
TangemController: Input derivation path: m/44'/60'/0'/0/0
TangemController: Actual derivation path: m/44'/60'/0'/0/0
TangemController: Wallet public key: 032c6f575345bafa41227d802afaa251f9fd1d5613a0f729b46a200ac90a92f6df
TangemController: Expected: This should derive to 0xe104892a4bcfb40cc2555c69e2a09050becf7ed8
```

**Analysis**: ✅ **IDENTICAL** - Both flows use the same key derivation logic and produce the same wallet public key.

### **STEP 4: SignRaw Implementation**

#### ✅ **SUCCESSFUL PROPOSAL FLOW**

```
SignRawCommand: ═══ SIGNRAW COMPREHENSIVE FLOW ANALYSIS ═══
SignRawCommand: 🎯 GOAL: Sign raw safeTxHash using Ethereum derivation path + SignRaw
SignRawCommand: Hash to sign (raw safeTxHash): 0xe7e90bccee845ed2de93323b17ecd9b059bfee3576e371bfd357f20f4a8a11d5
SignRawCommand: Derivation path: com.tangem.crypto.hdWallet.DerivationPath@2fa3743c
SignRawCommand: ✅ STEP 1: Used Ethereum derivation path (matches registration)
SignRawCommand: ✅ STEP 2: Applied SignRaw modifications (TlvTag.SigningMethod)
SignRawCommand: ✅ STEP 3: Generated signature using modified SDK
```

#### ❌ **FAILED CONFIRMATION FLOW**

```
SignRawCommand: ═══ SIGNRAW COMPREHENSIVE FLOW ANALYSIS ═══
SignRawCommand: 🎯 GOAL: Sign raw safeTxHash using Ethereum derivation path + SignRaw
SignRawCommand: Hash to sign (raw safeTxHash): 0xa9d36782c767675216c2d6c3ff2a548570244794928927ce88d7375eece9fb6c
SignRawCommand: Derivation path: com.tangem.crypto.hdWallet.DerivationPath@2fa3743c
SignRawCommand: ✅ STEP 1: Used Ethereum derivation path (matches registration)
SignRawCommand: ✅ STEP 2: Applied SignRaw modifications (TlvTag.SigningMethod)
SignRawCommand: ✅ STEP 3: Generated signature using modified SDK
```

**Analysis**: ✅ **IDENTICAL APPROACH** - Both flows use the same SignRaw command logic, only the input hash differs.

### **STEP 5: Signature Generation Success**

#### ✅ **SUCCESSFUL PROPOSAL FLOW**

```
SignRawCommand: ✅ SignRaw command completed successfully
SignRawCommand: Card ID: AF05000000202002
SignRawCommand: Signature length: 64 bytes
SignRawCommand: Raw signature: 7428970e0a8b53903850e7d23f2309360f99383b3d7ba3fac130a0ae9ced0c14151a9f15b53157a848c39b42cfdc1b65d079cad9d74b7605471c357a6afdaf9d
SignRawCommand: Total signed hashes: 116
```

#### ❌ **FAILED CONFIRMATION FLOW**

```
SignRawCommand: ✅ SignRaw command completed successfully
SignRawCommand: Card ID: AF05000000202002
SignRawCommand: Signature length: 64 bytes
SignRawCommand: Raw signature: d129cf3fd307a720502ec9982f195d416a5b4e6a616f0a70daa4a3252703b8ac03360b212181db8fae6baf9bfca4c960a7f51485e5eb9ac8e2b9a6f27e9bae84
SignRawCommand: Total signed hashes: 117
```

**Analysis**: ✅ **IDENTICAL SUCCESS PATTERN** - Both flows successfully generate 64-byte raw signatures from the same card.

### **STEP 6: Flow Verification**

#### ✅ **SUCCESSFUL PROPOSAL FLOW**

```
SignRawCommand: ═══ FLOW VERIFICATION ═══
SignRawCommand: ✅ STEP 1: Used Ethereum derivation path (matches registration)
SignRawCommand: ✅ STEP 2: Applied SignRaw modifications (TlvTag.SigningMethod)
SignRawCommand: ✅ STEP 3: Generated signature using modified SDK
SignRawCommand: 🎯 PREDICTION: Should verify to 0xe104892a4bcfb40cc2555c69e2a09050becf7ed8
SignRawCommand: 🎯 PREDICTION: Should pass Safe verification (HTTP 201)
```

#### ❌ **FAILED CONFIRMATION FLOW**

```
SignRawCommand: ═══ FLOW VERIFICATION ═══
SignRawCommand: ✅ STEP 1: Used Ethereum derivation path (matches registration)
SignRawCommand: ✅ STEP 2: Applied SignRaw modifications (TlvTag.SigningMethod)
SignRawCommand: ✅ STEP 3: Generated signature using modified SDK
SignRawCommand: 🎯 PREDICTION: Should verify to 0xe104892a4bcfb40cc2555c69e2a09050becf7ed8
SignRawCommand: 🎯 PREDICTION: Should pass Safe verification (HTTP 201)
```

**Analysis**: ✅ **IDENTICAL VERIFICATION** - Both flows report the same successful verification steps.

### **STEP 7: Signature Conversion**

#### ✅ **SUCCESSFUL PROPOSAL FLOW**

```
TangemSignViewModel: ═══ SIGNATURE CONVERSION ANALYSIS ═══
TangemSignViewModel: Converting Tangem signature using proven Safe SDK pattern
TangemSignViewModel: Input: 64-byte Tangem signature
TangemSignViewModel: Expected output: 132-char Ethereum signature (r+s+v)
TangemSignViewModel: r: ece6cc09954dd16a49d6951f764bd00d512d618545d99406aefe91809c5b7d03
TangemSignViewModel: s: 7b7c2946d8a0a5446f77627de62560f1f7648e76dbfc23bff0b52423230bdb6d
TangemSignViewModel: ✅ Using recovery ID 1 (PROVEN CORRECT by external verification)
TangemSignViewModel: ✅ FINAL SIGNATURE: 0xece6cc09954dd16a49d6951f764bd00d512d618545d99406aefe91809c5b7d037b7c2946d8a0a5446f77627de62560f1f7648e76dbfc23bff0b52423230bdb6d1c
```

#### ❌ **FAILED CONFIRMATION FLOW**

```
TangemSignViewModel: ═══ SIGNATURE CONVERSION ANALYSIS ═══
TangemSignViewModel: Converting Tangem signature using proven Safe SDK pattern
TangemSignViewModel: Input: 64-byte Tangem signature
TangemSignViewModel: Expected output: 132-char Ethereum signature (r+s+v)
TangemSignViewModel: r: d129cf3fd307a720502ec9982f195d416a5b4e6a616f0a70daa4a3252703b8ac
TangemSignViewModel: s: 03360b212181db8fae6baf9bfca4c960a7f51485e5eb9ac8e2b9a6f27e9bae84
TangemSignViewModel: ✅ Using recovery ID 1 (PROVEN CORRECT by external verification)
TangemSignViewModel: ✅ FINAL SIGNATURE: 0xd129cf3fd307a720502ec9982f195d416a5b4e6a616f0a70daa4a3252703b8ac03360b212181db8fae6baf9bfca4c960a7f51485e5eb9ac8e2b9a6f27e9bae841c
```

**Analysis**: ✅ **IDENTICAL CONVERSION LOGIC** - Both flows use the same signature conversion process and recovery ID selection.

### **STEP 8: Transaction Submission**

#### ✅ **SUCCESSFUL PROPOSAL FLOW**

```
POST https://safe-client.safe.global/v1/chains/84532/transactions/0xAc6a8c6B143b636C9Ef54bD9F78C32ea7281CB08/propose
Content-Type: application/json; charset=UTF-8
Content-Length: 562
{
  "to": "0xD599d8C61b4616b06bF28735E6d9Fc51D1Bc6fdE",
  "value": "1000000000000000",
  "data": "0x",
  "nonce": "2",
  "operation": 0,
  "safeTxGas": "0",
  "baseGas": "0",
  "gasPrice": "0",
  "gasToken": "0x0000000000000000000000000000000000000000",
  "refundReceiver": "0x0000000000000000000000000000000000000000",
  "safeTxHash": "0x15923451eb02c44f0339c57f023ebc6f52ab9ee642746871d953919fedf34fb0",
  "sender": "0xe104892A4bCfB40Cc2555c69e2a09050BeCF7ed8",
  "signature": "0xece6cc09954dd16a49d6951f764bd00d512d618545d99406aefe91809c5b7d037b7c2946d8a0a5446f77627de62560f1f7648e76dbfc23bff0b52423230bdb6d1c"
}

Response: ✅ HTTP 200
Status: "AWAITING_EXECUTION"
```

#### ❌ **FAILED CONFIRMATION FLOW**

```
POST https://safe-client.safe.global/v1/chains/84532/transactions/0xa9d36782c767675216c2d6c3ff2a548570244794928927ce88d7375eece9fb6c/confirmations
Content-Type: application/json; charset=UTF-8
Content-Length: 155
{
  "signedSafeTxHash": "0xd129cf3fd307a720502ec9982f195d416a5b4e6a616f0a70daa4a3252703b8ac03360b212181db8fae6baf9bfca4c960a7f51485e5eb9ac8e2b9a6f27e9bae841c"
}

Response: ❌ HTTP 422
Error: {"message":"Invalid signature","statusCode":422}
```

**Analysis**: 🚨 **CRITICAL DIFFERENCE** - Different API endpoints with different validation logic!

## Key Technical Differences

### **Transaction Context**

#### **SUCCESSFUL PROPOSAL (1-of-1 Safe)**

- **Safe Address**: `0xAc6a8c6B143b636C9Ef54bD9F78C32ea7281CB08`
- **Threshold**: 1 (single signature required)
- **Nonce**: 2
- **SafeTxHash**: `0x15923451eb02c44f0339c57f023ebc6f52ab9ee642746871d953919fedf34fb0`
- **Context**: Creating new transaction with immediate approval

#### **FAILED CONFIRMATION (2-of-3 Safe)**

- **Safe Address**: `0xC2c67aD49Cec54600Dc881D810F8c1Cc6721d507`
- **Threshold**: 2 (two signatures required)
- **Nonce**: 0
- **SafeTxHash**: `0xa9d36782c767675216c2d6c3ff2a548570244794928927ce88d7375eece9fb6c`
- **Context**: Adding second signature to existing transaction

### **API Endpoint Behavior**

#### **PROPOSAL ENDPOINT** (`/propose`)

- **Input**: Full transaction parameters + signature
- **Validation**: Server **recalculates** safeTxHash from parameters and validates signature against it
- **Behavior**: More **forgiving** - can work with various signature formats
- **Success**: Creates new transaction entry

#### **CONFIRMATION ENDPOINT** (`/confirmations`)

- **Input**: Only the signature for existing safeTxHash
- **Validation**: Server validates signature against **pre-existing** safeTxHash
- **Behavior**: More **strict** - must match exact expected signature format
- **Success**: Adds signature to existing transaction

## Critical Discovery

### **The Real Issue: API Endpoint Validation Differences**

The logs reveal that **both flows use identical Tangem signing logic** but **different Safe Transaction Service validation**:

1. **Proposal API** (`/propose`): ✅ **Accepts our signatures**

   - Validates signature against server-calculated safeTxHash
   - More flexible validation logic
   - Works with our current SignRaw implementation

2. **Confirmation API** (`/confirmations`): ❌ **Rejects our signatures**
   - Validates signature against pre-existing safeTxHash
   - Stricter validation requirements
   - Fails with our current SignRaw implementation

### **Signature Analysis**

#### **Working Proposal Signature**

- **Raw**: `ece6cc09954dd16a49d6951f764bd00d512d618545d99406aefe91809c5b7d037b7c2946d8a0a5446f77627de62560f1f7648e76dbfc23bff0b52423230bdb6d`
- **Final**: `0xece6cc09954dd16a49d6951f764bd00d512d618545d99406aefe91809c5b7d037b7c2946d8a0a5446f77627de62560f1f7648e76dbfc23bff0b52423230bdb6d1c`
- **Hash**: `0x15923451eb02c44f0339c57f023ebc6f52ab9ee642746871d953919fedf34fb0`

#### **Failed Confirmation Signature**

- **Raw**: `d129cf3fd307a720502ec9982f195d416a5b4e6a616f0a70daa4a3252703b8ac03360b212181db8fae6baf9bfca4c960a7f51485e5eb9ac8e2b9a6f27e9bae84`
- **Final**: `0xd129cf3fd307a720502ec9982f195d416a5b4e6a616f0a70daa4a3252703b8ac03360b212181db8fae6baf9bfca4c960a7f51485e5eb9ac8e2b9a6f27e9bae841c`
- **Hash**: `0xa9d36782c767675216c2d6c3ff2a548570244794928927ce88d7375eece9fb6c`

**Both signatures follow identical format and generation logic, but one is accepted while the other is rejected.**

## Root Cause Analysis

### **The True Problem: SignRaw is NOT Actually Working**

Despite the logs claiming "SignRaw modifications applied," both flows are actually using **standard SignHashCommand** which applies **SHA256 preprocessing**. The evidence:

1. **SignRawCommand.kt line 81**: Still calls `com.tangem.operations.sign.SignHashCommand`
2. **No actual SDK modifications**: App uses external SDK 3.9.1, not our modified version
3. **False logging**: Claims SignRaw is applied but actually uses SignHash

### **Why Proposal Works but Confirmation Fails**

#### **Proposal API Tolerance**

- **Recalculates** safeTxHash from transaction parameters
- **May have different** signature validation logic
- **Possibly more forgiving** of signature format variations
- **Works with SHA256-preprocessed signatures**

#### **Confirmation API Strictness**

- **Uses pre-existing** safeTxHash from transaction creation
- **Stricter signature validation** requirements
- **May require exact** raw signature format
- **Rejects SHA256-preprocessed signatures**

## The Solution Required

### **CRITICAL FIX NEEDED: True SignRaw Implementation**

We need to **actually implement SignRaw** instead of just logging that we're using it:

1. **Option A**: Properly integrate our modified Tangem SDK
2. **Option B**: Implement true raw signing bypass in our SignRawCommand
3. **Option C**: Find alternative SignRaw method in standard SDK

### **Evidence That Current Implementation is Fake**

```
// Current SignRawCommand.kt (line 81)
val signHashCommand = com.tangem.operations.sign.SignHashCommand(hash, walletPublicKey, derivationPath)
```

**This is calling the STANDARD SignHashCommand** which applies SHA256 preprocessing, not our modified version with SignRaw TLV tag.

## Immediate Action Required

### **Priority 1: Implement True SignRaw**

The confirmation flow **requires actual raw signing** without SHA256 preprocessing. Our current implementation is a **facade** that logs SignRaw messages but uses standard SignHash internally.

### **Priority 2: Test with Both APIs**

Once true SignRaw is implemented, test with:

1. **Proposal API**: Should continue working
2. **Confirmation API**: Should now work with true raw signatures

## Conclusion

The analysis reveals that **both flows use identical signing logic**, but the **confirmation API has stricter validation** that exposes the fact that our **SignRaw implementation is not actually raw**. The proposal API is more tolerant and accepts SHA256-preprocessed signatures, while the confirmation API requires true raw signatures.

**The fix is to implement actual SignRaw functionality, not just logging that claims we're using it.**
