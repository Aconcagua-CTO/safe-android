#!/usr/bin/env python3
"""
Test all wallets on the Tangem card to see which one produces 
the registered address 0xe104892a4bcfb40cc2555c69e2a09050becf7ed8
"""

from eth_utils import keccak
from eth_keys import keys

def derive_address_from_public_key(public_key_hex):
    """Derive Ethereum address from public key."""
    try:
        if public_key_hex.startswith('0x'):
            public_key_hex = public_key_hex[2:]
        
        # Handle different key lengths
        if len(public_key_hex) == 66:  # 33 bytes compressed
            public_key_bytes = bytes.fromhex(public_key_hex)
            public_key_obj = keys.PublicKey.from_compressed_bytes(public_key_bytes)
            uncompressed_bytes = public_key_obj.to_bytes()
        elif len(public_key_hex) == 128:  # 64 bytes uncompressed (no prefix)
            # Add the 0x04 prefix for uncompressed keys
            uncompressed_bytes = bytes.fromhex('04' + public_key_hex)
        elif len(public_key_hex) == 130:  # 65 bytes uncompressed (with prefix)
            uncompressed_bytes = bytes.fromhex(public_key_hex)
        else:
            return f"Invalid length: {len(public_key_hex)} chars"
        
        # Remove the 0x04 prefix for address calculation
        if uncompressed_bytes[0] == 0x04:
            uncompressed_bytes = uncompressed_bytes[1:]
        
        # Keccak256 hash of the uncompressed public key (64 bytes)
        hash_result = keccak(uncompressed_bytes)
        
        # Take the last 20 bytes as the address
        address = hash_result[-20:]
        
        return f"0x{address.hex()}"
        
    except Exception as e:
        return f"Error: {e}"

def main():
    """Test all wallets from the card logs."""
    
    print("TANGEM CARD WALLET ANALYSIS")
    print("=" * 40)
    
    # Target address we need to match
    target_address = "0xe104892a4bcfb40cc2555c69e2a09050becf7ed8"
    print(f"Target address (registered): {target_address}")
    print()
    
    # All wallets from the card logs
    wallets = {
        "Wallet[0] (Secp256k1)": "032c6f575345bafa41227d802afaa251f9fd1d5613a0f729b46a200ac90a92f6df",
        "Wallet[1] (Ed25519)": "271f7d7a3ffdfcc66031fd75252bb28f7ad33f11e03301d399aed6df7b2e5f44",
        "Wallet[2] (Bls12381G2Aug)": "a3a44c11ac9ecf023b90cab1e1fc4c4bc7f35ce52eaf7132527c06bff395ee6ed8b543a80e0aee2eb5277ea8a91b8b5d",
        "Wallet[3] (Bip0340)": "2c6f575345bafa41227d802afaa251f9fd1d5613a0f729b46a200ac90a92f6df",
        "Wallet[4] (Ed25519Slip0010)": "61a81c549be90fb3b3ace08c35c2100684fda87e553b931c09ab109717a828e9"
    }
    
    print("Testing each wallet:")
    print()
    
    matches_found = []
    
    for wallet_name, public_key in wallets.items():
        print(f"{wallet_name}:")
        print(f"  Public Key: {public_key}")
        
        # Derive address
        derived_address = derive_address_from_public_key(public_key)
        print(f"  Derived Address: {derived_address}")
        
        # Check for match
        if isinstance(derived_address, str) and derived_address.lower() == target_address.lower():
            print(f"  ✅ MATCH! This wallet produces the registered address!")
            matches_found.append((wallet_name, public_key, derived_address))
        else:
            print(f"  ❌ No match")
        
        print()
    
    print("ANALYSIS RESULTS:")
    print("=" * 25)
    
    if matches_found:
        print(f"✅ Found {len(matches_found)} matching wallet(s):")
        for wallet_name, public_key, address in matches_found:
            print(f"  {wallet_name}")
            print(f"    Public Key: {public_key}")
            print(f"    Address: {address}")
        print()
        print("🎯 SOLUTION: Use the matching wallet for signing!")
    else:
        print("❌ NO WALLETS MATCH the registered address")
        print()
        print("🤔 POSSIBLE EXPLANATIONS:")
        print("1. The registered address was derived using a specific derivation path")
        print("2. The registered address came from a different card scan")
        print("3. The registered address was manually entered")
        print("4. There's a bug in our address derivation logic")
        
    print("\nNEXT STEPS:")
    print("=" * 15)
    if matches_found:
        print("1. Modify TangemController to use the matching wallet")
        print("2. Update the signing logic to use the correct public key")
        print("3. Test signing with the matching wallet")
    else:
        print("1. Investigate the original registration process")
        print("2. Check if the registered address needs to be updated")
        print("3. Verify with the Tangem app what address it shows")
        print("4. Consider re-registering with the actual card address")

if __name__ == "__main__":
    main()
