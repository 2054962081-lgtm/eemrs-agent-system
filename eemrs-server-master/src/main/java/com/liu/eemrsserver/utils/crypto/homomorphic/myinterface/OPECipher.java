package com.liu.eemrsserver.utils.crypto.homomorphic.myinterface;

import java.math.BigInteger;

/**
 * OPE Cipher encrypt BigInteger to BigInteger
 */
public interface OPECipher extends KeyGenerable {
    BigInteger encrypt(BigInteger plaintext, byte[] key) throws Exception;

    BigInteger decrypted(BigInteger chipertext, byte[] key) throws Exception;

    BigInteger encrypt(BigInteger plaintext, byte[] key, byte[] iv) throws Exception;

    BigInteger decrypted(BigInteger chipertext, byte[] key, byte[] iv) throws Exception;
}
