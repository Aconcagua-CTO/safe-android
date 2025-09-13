#!/usr/bin/env python3
"""
Test if we can derive the registered address 0xe104892a4bcfb40cc2555c69e2a09050becf7ed8
from the card's base wallet using different derivation paths.
"""

from eth_utils import keccak
from eth_keys import keys
import hashlib

def derive_address_from_public_key(public_key_hex):
    """Derive Ethereum address from public key."""
    try:
        if public_key_hex.startswith('0x'):
            public_key_hex = public_key_hex[2:]
        
        # Handle compressed public key
        if len(public_key_hex) == 66:  # 33 bytes compressed
            public_key_bytes = bytes.fromhex(public_key_hex)
            public_key_obj = keys.PublicKey.from_compressed_bytes(public_key_bytes)
            uncompressed_bytes = public_key_obj.to_bytes()
        else:
            return f"Invalid length: {len(public_key_hex)}"
        
        # Remove the 0x04 prefix for uncompressed keys
        if uncompressed_bytes[0] == 0x04:
            uncompressed_bytes = uncompressed_bytes[1:]
        
        # Keccak256 hash of the uncompressed public key (64 bytes)
        hash_result = keccak(uncompressed_bytes)
        
        # Take the last 20 bytes as the address
        address = hash_result[-20:]
        
        return f"0x{address.hex()}"
        
    except Exception as e:
        return f"Error: {e}"

def test_address_transformations(base_public_key, target_address):
    """Test various transformations to see if we can get target address."""
    
    print("TESTING ADDRESS TRANSFORMATIONS")
    print("=" * 40)
    print(f"Base public key: {base_public_key}")
    print(f"Target address: {target_address}")
    print()
    
    # Test 1: Direct derivation (already done)
    direct_addr = derive_address_from_public_key(base_public_key)
    print(f"1. Direct derivation: {direct_addr}")
    
    # Test 2: Try removing compression prefix and re-deriving
    if base_public_key.startswith('03') or base_public_key.startswith('02'):
        uncompressed_key = base_public_key[2:]  # Remove compression prefix
        uncomp_addr = derive_address_from_public_key(uncompressed_key)
        print(f"2. Uncompressed key: {uncomp_addr}")
    
    # Test 3: Try the suffix pattern from the target
    # Maybe there's a pattern in how Tangem derives addresses
    target_suffix = target_address[-20:]  # Last 20 chars
    base_suffix = direct_addr[-20:] if isinstance(direct_addr, str) else ""
    
    print(f"3. Address suffix analysis:")
    print(f"   Target suffix: {target_suffix}")
    print(f"   Base suffix: {base_suffix}")
    
    # Test 4: Check if target address could be derived from a different key
    print(f"4. Analysis:")
    if direct_addr.lower() == target_address.lower():
        print("   ✅ MATCH: Base wallet produces target address")
        return True
    else:
        print("   ❌ NO MATCH: Need to find different wallet or derivation")
        return False

def main():
    """Main analysis function."""
    
    print("TANGEM KEY MISMATCH ANALYSIS")
    print("=" * 50)
    
    # Known data
    base_public_key = "032c6f575345bafa41227d802afaa251f9fd1d5613a0f729b46a200ac90a92f6df"
    registered_address = "0xe104892a4bcfb40cc2555c69e2a09050becf7ed8"
    actual_signing_address = "0x9fe13b041b6811b717b311fce887146972b20d6a"
    
    print("PROBLEM SUMMARY:")
    print(f"  Registered in Safe: {registered_address}")
    print(f"  Card wallet[0] produces: {actual_signing_address}")
    print(f"  Card public key: {base_public_key}")
    print()
    
    # Test if we can derive the registered address
    result = test_address_transformations(base_public_key, registered_address)
    
    print("\nCONCLUSIONS:")
    print("=" * 20)
    
    if result:
        print("✅ The registered address can be derived from the card")
        print("🎯 Solution: Fix derivation logic to use correct wallet")
    else:
        print("❌ The registered address CANNOT be derived from wallet[0]")
        print("🎯 Options:")
        print("   1. Find a different wallet on the card that produces the registered address")
        print("   2. Re-register the Safe with the actual card address")
        print("   3. Investigate how the registered address was originally derived")
    
    print("\nNEXT STEPS:")
    print("=" * 15)
    print("1. Check if other wallets on the card can produce the registered address")
    print("2. Verify the original registration process")
    print("3. Determine if we need to re-register or find the correct wallet")

if __name__ == "__main__":
    main()
