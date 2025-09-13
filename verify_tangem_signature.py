#!/usr/bin/env python3
"""
Expert's Python verification method to test Tangem signatures.
Based on the expert's analysis that our local verification is bugged.

This script tests signature recovery against multiple hash candidates
to determine what hash our Tangem card is actually signing.
"""

import hashlib
from eth_utils import keccak
from eth_keys import keys

def verify_signature_recovery(signature_hex, hash_candidates, expected_address):
    """
    Test signature recovery against multiple hash candidates.
    Based on expert's analysis from the Phase 1 report.
    """
    print("=" * 60)
    print("EXPERT'S PYTHON VERIFICATION METHOD")
    print("=" * 60)
    
    # Parse signature components
    if signature_hex.startswith('0x'):
        signature_hex = signature_hex[2:]
    
    if len(signature_hex) != 130:  # 64 bytes r + 64 bytes s + 2 bytes v = 130 hex chars
        print(f"❌ Invalid signature length: {len(signature_hex)} (expected 130)")
        return
    
    r_hex = signature_hex[:64]
    s_hex = signature_hex[64:128]
    v_hex = signature_hex[128:130]
    
    print(f"Signature Analysis:")
    print(f"  r: {r_hex}")
    print(f"  s: {s_hex}")
    print(f"  v: {v_hex}")
    print(f"  Expected signer: {expected_address}")
    print()
    
    # Test recovery against each hash candidate
    for i, (name, hash_bytes) in enumerate(hash_candidates.items(), 1):
        print(f"Test {i} - {name}:")
        print(f"  Hash: {hash_bytes.hex()}")
        
        try:
            # Test both v values (27 and 28)
            for v_int in [27, 28]:
                try:
                    # Create signature object
                    r = int(r_hex, 16)
                    s = int(s_hex, 16)
                    recovery_id = v_int - 27  # Convert v (27/28) to recovery_id (0/1)
                    
                    # Create signature using eth_keys (expects recovery_id 0/1, not v 27/28)
                    signature = keys.Signature(vrs=(recovery_id, r, s))
                    
                    # Recover public key from hash and signature
                    public_key = signature.recover_public_key_from_msg_hash(hash_bytes)
                    
                    # Get address from public key
                    recovered_address = public_key.to_checksum_address()
                    
                    print(f"    v={v_int} (0x{v_int:02x}): {recovered_address}")
                    
                    if recovered_address.lower() == expected_address.lower():
                        print(f"    ✅ MATCH! Signature signs {name}")
                        return name, v_int, recovered_address
                        
                except Exception as e:
                    print(f"    v={v_int}: Recovery failed - {e}")
                    
        except Exception as e:
            print(f"    ❌ Recovery failed: {e}")
        
        print()
    
    print("❌ No hash candidate matched - signature doesn't recover to expected address")
    return None

def main():
    """Test our Phase 2 signature using expert's verification method."""
    
    # Phase 2 test data from logs
    print("PHASE 2 SIGNATURE VERIFICATION")
    print("Testing SDK-modified SignRaw implementation")
    print()
    
    # Data from latest test logs
    safe_tx_hash = "16691fce510878c9ddf704da6de1402d2e440ebf67338ca50619573af2780bb9"
    phase2_signature = "2caa5547d2248aeec3fc0c0e9c8a9750fd872689aca34c3e1488b1f2c6ef90080a21ea327c5025fb00aba106827e7963297157570b269ddd78dce8d8d19e30a71c"
    expected_address = "0xe104892a4bcfb40cc2555c69e2a09050becf7ed8"
    
    # Convert to bytes
    safe_tx_hash_bytes = bytes.fromhex(safe_tx_hash)
    
    # Hash candidates based on expert's analysis
    hash_candidates = {
        "Raw safeTxHash": safe_tx_hash_bytes,
        "SHA256(safeTxHash)": hashlib.sha256(safe_tx_hash_bytes).digest(),
        "SHA256(SHA256(safeTxHash))": hashlib.sha256(hashlib.sha256(safe_tx_hash_bytes).digest()).digest(),
        "Keccak256(safeTxHash)": keccak(safe_tx_hash_bytes),
    }
    
    # Add Ethereum personal sign format
    personal_sign_message = b"\x19Ethereum Signed Message:\n32" + safe_tx_hash_bytes
    hash_candidates["Personal Sign Format"] = keccak(personal_sign_message)
    
    print("Hash Candidates:")
    for name, hash_bytes in hash_candidates.items():
        print(f"  {name}: {hash_bytes.hex()}")
    print()
    
    # Test Phase 2 signature
    print("TESTING PHASE 2 SIGNATURE:")
    print(f"Signature: 0x{phase2_signature}")
    print()
    
    result = verify_signature_recovery(
        f"0x{phase2_signature}",
        hash_candidates,
        expected_address
    )
    
    if result:
        hash_type, v_value, recovered_addr = result
        print("=" * 60)
        print("🎯 EXPERT VERIFICATION RESULT:")
        print(f"✅ Signature signs: {hash_type}")
        print(f"✅ Correct v value: {v_value}")
        print(f"✅ Recovered address: {recovered_addr}")
        print("=" * 60)
        
        if hash_type == "Raw safeTxHash":
            print("🎉 SUCCESS: SignRaw implementation is working!")
            print("🎯 Next step: Debug why Safe Transaction Service still rejects it")
        else:
            print(f"⚠️ ISSUE: Still signing {hash_type} instead of raw safeTxHash")
            print("🎯 Next step: SDK modifications need refinement")
    else:
        print("=" * 60)
        print("❌ VERIFICATION FAILED:")
        print("Signature doesn't recover to expected address for any hash candidate")
        print("🎯 This confirms our local verification bug")
        print("🎯 Need to investigate signature generation process")
        print("=" * 60)

    # Also test previous signatures for comparison
    print("\n" + "=" * 60)
    print("COMPARISON WITH PREVIOUS SIGNATURES:")
    print("=" * 60)
    
    # Phase 1 signature for comparison
    phase1_signature = "1544b3a6c4cc9dea91612c0566e1a6dfa01ef3cebef3d6b18e6ec97fe7599c6f10d12d68f706792cedc4595639265a2f66414121b6beca512e2a78f2b6e30dc11c"
    
    print("\nTesting Phase 1 signature:")
    print(f"Signature: 0x{phase1_signature}")
    
    result1 = verify_signature_recovery(
        f"0x{phase1_signature}",
        hash_candidates,
        expected_address
    )
    
    if result1:
        hash_type1, v_value1, recovered_addr1 = result1
        print(f"Phase 1 result: Signs {hash_type1} with v={v_value1}")
    else:
        print("Phase 1: No match found")

if __name__ == "__main__":
    main()