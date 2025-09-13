import Safe, { EthersAdapter, EthSafeSignature } from '@safe-global/protocol-kit';
import { SafeSignature } from '@safe-global/safe-core-sdk-types';
import { ethers } from 'ethers';
// Import any other required dependencies

// Helper function to create a valid SafeSignature object
const createSafeSignature = (signer: string, signatureData: string): SafeSignature => {
  return {
    signer,
    data: signatureData,
    // These are required by the SafeSignature interface
    staticPart: () => signatureData.slice(0, 130),
    dynamicPart: () => signatureData.slice(130),
  };
};

export default class SafeSDKNoAPIBackend {
  sdk: Safe | undefined;
  safeAddress: string | undefined;
  adapter: any;
  static instance: SafeSDKNoAPIBackend;

  static getInstance(): SafeSDKNoAPIBackend {
    if (!SafeSDKNoAPIBackend.instance) {
      SafeSDKNoAPIBackend.instance = new SafeSDKNoAPIBackend();
    }
    return SafeSDKNoAPIBackend.instance;
  }

  async initSDK(adapter: EthersAdapter, address: string) {
    this.adapter = adapter;
    this.safeAddress = ethers.utils.getAddress(address);

    this.sdk = await Safe.create({
      ethAdapter: this.adapter,
      safeAddress: this.safeAddress,
    });
  }

  async createTransaction(tx: any, sender: string) {
    console.log('SafeSDKNoAPIBackend.createTransaction - Input:', { tx, sender });

    const currentNonce = await this.sdk.getNonce();
    const safeTransaction = await this.sdk.createTransaction({
      safeTransactionData: tx,
      options: { nonce: currentNonce },
    });

    const txHash = await this.sdk.getTransactionHash(safeTransaction);
    const signedTx = await this.sdk.signTransaction(safeTransaction);
    const signature = signedTx.signatures.get(sender.toLowerCase());

    return {
      signer: sender.toLowerCase(),
      signature: signature.data,
      hash: txHash,
      safeTxData: safeTransaction.data,
      nonce: currentNonce,
    };
  }

  async confirmTransaction(txData: any) {
    try {
      const txHash = txData.hash;
      const signature = await this.sdk.signTransactionHash(txHash);
      return signature;
    } catch (error) {
      console.error('SafeSDKNoAPIBackend.confirmTransaction - Error:', error);
      throw error;
    }
  }

  async getSafeOwners() {
    const owners: string[] | undefined = await this.sdk?.getOwners();
    return {
      count: owners?.length || 0,
      owners: owners || [],
    };
  }

  async executeTransaction(txData: any): Promise<any> {
    try {
      console.log('SafeSDKNoAPIBackend.executeTransaction - Starting execution with data:', txData);
      if (!this.sdk) throw new Error('Safe SDK not initialized');

      const safeTransaction = await this.sdk.createTransaction({
        safeTransactionData: txData.safeTxData,
        options: { nonce: txData.nonce },
      });

      const computedHash = await this.sdk.getTransactionHash(safeTransaction);
      if (computedHash !== txData.hash) {
        throw new Error('Computed transaction hash does not match stored hash');
      }

      // Add Party 1's signature from safeMainTransaction
      if (txData.safeMainTransaction?.signer && txData.safeMainTransaction?.signature) {
        const party1Signature = new EthSafeSignature(
          txData.safeMainTransaction.signer,
          txData.safeMainTransaction.signature
        );
        console.log(
          `SafeSDKNoAPI.executeTransaction - Adding signature from ${txData.safeMainTransaction.signer}`
        );
        safeTransaction.addSignature(party1Signature);
      }

      // Add all signatures from safeConfirmations array
      if (txData.safeConfirmations && Array.isArray(txData.safeConfirmations)) {
        for (const confirmation of txData.safeConfirmations) {
          if (confirmation.signer && confirmation.data) {
            console.log(
              `SafeSDKNoAPI.executeTransaction - Adding signature from ${confirmation.signer}`
            );
            const signature = new EthSafeSignature(confirmation.signer, confirmation.data);
            safeTransaction.addSignature(signature);
          }
        }
      }

      // Verify threshold
      const threshold = await this.sdk.getThreshold();
      if (safeTransaction.signatures.size < threshold) {
        throw new Error(`Insufficient signatures: ${safeTransaction.signatures.size}/${threshold}`);
      }

      const executeTxResponse = await this.sdk.executeTransaction(safeTransaction, {
        gasLimit: 500000,
      });

      const receipt = await executeTxResponse.transactionResponse?.wait();
      return { hash: executeTxResponse.hash, receipt };
    } catch (error) {
      console.error('SafeSDKNoAPIBackend.executeTransaction - Error:', error);
      throw error;
    }
  }
}
