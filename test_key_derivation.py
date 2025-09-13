#!/usr/bin/env python3
"""
Test key derivation to understand how to get from the card's public key
to the registered address 0xe104892a4bcfb40cc2555c69e2a09050becf7ed8
"""

from eth_utils import keccak
from eth_keys import keys

def derive_address_from_public_key(public_key_hex, compressed=True):
    """Derive Ethereum address from public key."""
    try:
        if public_key_hex.startswith('0x'):
            public_key_hex = public_key_hex[2:]
        
        if compressed and len(public_key_hex) == 66:  # 33 bytes compressed
            # Decompress the public key
            public_key_bytes = bytes.fromhex(public_key_hex)
            public_key_obj = keys.PublicKey.from_compressed_bytes(public_key_bytes)
            uncompressed_bytes = public_key_obj.to_bytes()
        elif len(public_key_hex) == 130:  # 65 bytes uncompressed
            uncompressed_bytes = bytes.fromhex(public_key_hex)
        else:
            return f"Invalid public key length: {len(public_key_hex)}"
        
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

def main():
    """Analyze key derivation for Tangem card."""
    
    print("TANGEM KEY DERIVATION ANALYSIS")
    print("=" * 50)
    
    # Data from our logs
    registered_address = "0xe104892a4bcfb40cc2555c69e2a09050becf7ed8"
    actual_signing_address = "0x9fe13b041b6811b717b311fce887146972b20d6a"
    
    # Wallet public keys from card
    wallet_keys = {
        "Wallet[0] (secp256k1)": "032c6f575345bafa41227d802afaa251f9fd1d5613a0f729b46a200ac90a92f6df",
        "Wallet[3] (Bip0340)": "2c6f575345bafa41227d802afaa251f9fd1d5613a0f729b46a200ac90a92f6df",  # Same but uncompressed?
    }
    
    print(f"Registered address: {registered_address}")
    print(f"Actual signing address: {actual_signing_address}")
    print()
    
    print("Testing address derivation from wallet public keys:")
    print()
    
    for wallet_name, public_key in wallet_keys.items():
        print(f"{wallet_name}:")
        print(f"  Public Key: {public_key}")
        
        # Test both compressed and uncompressed derivation
        compressed_addr = derive_address_from_public_key(public_key, compressed=True)
        print(f"  Compressed derivation: {compressed_addr}")
        
        # Check if this matches any of our addresses
        if compressed_addr.lower() == registered_address.lower():
            print(f"  ✅ MATCHES REGISTERED ADDRESS!")
        elif compressed_addr.lower() == actual_signing_address.lower():
            print(f"  ✅ MATCHES ACTUAL SIGNING ADDRESS!")
        else:
            print(f"  ❌ No match")
        
        print()
    
    # Test if the actual signing address could be derived from the registered key
    print("REVERSE ANALYSIS:")
    print("=" * 30)
    print(f"If we're signing with: {actual_signing_address}")
    print(f"But registered: {registered_address}")
    print()
    print("Possible explanations:")
    print("1. We're using the wrong wallet from the card")
    print("2. We're using a derived key instead of the primary key")
    print("3. The registration process stored the wrong address")
    print("4. The primary key concept doesn't work as expected")
    
    # Check if there's a pattern in the addresses
    print("\nADDRESS PATTERN ANALYSIS:")
    print("=" * 30)
    
    # Extract the key parts to see if there's a relationship
    registered_suffix = registered_address[-20:]  # Last 20 chars
    signing_suffix = actual_signing_address[-20:]
    
    print(f"Registered suffix: {registered_suffix}")
    print(f"Signing suffix: {signing_suffix}")
    
    if registered_suffix == signing_suffix:
        print("✅ Suffixes match - might be derivation issue")
    else:
        print("❌ Suffixes different - completely different keys")

if __name__ == "__main__":
    main()
