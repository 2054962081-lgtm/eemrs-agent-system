package com.liu.eemrsserver.utils.crypto.homomorphic.boldyreva;


import com.liu.eemrsserver.utils.crypto.homomorphic.myinterface.OPECipher;
import com.sun.istack.NotNull;
//import com.sun.istack.internal.NotNull;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.security.Key;
import java.security.Security;

/**
 * Reference: <a href="http://www.cc.gatech.edu/~aboldyre/papers/bclo.pdf">
 * Boldyreva symmetric order-preserving encryption scheme</a>
 */
public class BoldyrevaCipher implements OPECipher {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final String KEY_ALGORITHM = "AES";
    private Range inRange;

    private Range outRange;

    public BoldyrevaCipher() {
        inRange = new Range(BigInteger.ZERO, BigInteger.valueOf(2).pow(15).subtract(BigInteger.ONE));
        outRange = new Range(BigInteger.ZERO, BigInteger.valueOf(2).pow(31).subtract(BigInteger.ONE));
    }

    public BoldyrevaCipher(Range inRange, Range outRange) {
        this.inRange = inRange;
        this.outRange = outRange;
    }

    @Override
    public int getBlockSize() {
        return 16;
    }

    @Override
    public byte[] generateKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(KEY_ALGORITHM);
            kg.init(256);
            return kg.generateKey().getEncoded();
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        return new byte[getBlockSize()];
    }

    @Override
    public Key toKey(byte[] key) {
        return new SecretKeySpec(key, KEY_ALGORITHM);
    }

    @Override
    public BigInteger encrypt(BigInteger plaintext, byte[] key) throws Exception {
        return encrypt(plaintext, key, new byte[getBlockSize()]);
    }

    @Override
    public BigInteger encrypt(BigInteger plaintext, byte[] key, byte[] iv) throws Exception {
        if (!inRange.contains(plaintext)) {
            throw new RuntimeException(plaintext + " out of range: " + inRange);
        }
        return encrypt(plaintext, key, iv, inRange, outRange);
    }

    private BigInteger encrypt(BigInteger plaintext, byte[] key, byte[] iv,
                               @NotNull Range inRange, @NotNull Range outRange) throws Exception {
        BigInteger inSize = inRange.size();
        BigInteger outSize = outRange.size();
        BigInteger inEdge = inRange.getMin().subtract(BigInteger.ONE);
        BigInteger outEdge = outRange.getMin().subtract(BigInteger.ONE);

        BigDecimal two = BigDecimal.valueOf(2);
        BigInteger mid = outEdge.add(new BigDecimal(outSize).divide(two, 10, RoundingMode.CEILING).toBigInteger());

        if (inRange.size().compareTo(BigInteger.ONE) == 0) {
            TapeGen coins = new TapeGen(key, plaintext, iv);
            return Utils.sampleUniform(outRange, coins);
        }

        TapeGen coins = new TapeGen(key, mid, iv);
        BigInteger x = Utils.sampleHGD(inRange, outRange, mid, coins);
        if (plaintext.compareTo(x) <= 0) {
            return encrypt(plaintext, key, iv,
                    new Range(inEdge.add(BigInteger.ONE), x),
                    new Range(outEdge.add(BigInteger.ONE), mid));
        } else {
            return encrypt(plaintext, key, iv,
                    new Range(x.add(BigInteger.ONE), inEdge.add(inSize)),
                    new Range(mid.add(BigInteger.ONE), outEdge.add(outSize)));
        }
    }

    @Override
    public BigInteger decrypted(BigInteger chipertext, byte[] key) throws Exception {
        return decrypted(chipertext, key, new byte[getBlockSize()]);
    }

    @Override
    public BigInteger decrypted(BigInteger chipertext, byte[] key, byte[] iv) throws Exception {
        if (!outRange.contains(chipertext)) {
            throw new RuntimeException(chipertext + " out of range: " + outRange);
        }

        return decrypted(chipertext, key, iv, inRange, outRange);
    }

    private BigInteger decrypted(BigInteger chipertext, byte[] key, byte[] iv,
                                 @NotNull Range inRange, @NotNull Range outRange) throws Exception {
        BigInteger inSize = inRange.size();
        BigInteger outSize = outRange.size();
        BigInteger inEdge = inRange.getMin().subtract(BigInteger.ONE);
        BigInteger outEdge = outRange.getMin().subtract(BigInteger.ONE);

        BigDecimal two = BigDecimal.valueOf(2);
        BigInteger mid = outEdge.add(new BigDecimal(outSize).divide(two, 10, RoundingMode.CEILING).toBigInteger());

        if (inRange.size().compareTo(BigInteger.ONE) == 0) {
            TapeGen coins = new TapeGen(key, inRange.getMin(), iv);
            BigInteger sampledCiphertext = Utils.sampleUniform(outRange, coins);
            if (sampledCiphertext.compareTo(chipertext) == 0) {
                return inRange.getMin();
            } else {
                throw new RuntimeException("error in decryption");
            }
        }
        TapeGen coins = new TapeGen(key, mid, iv);
        BigInteger x = Utils.sampleHGD(inRange, outRange, mid, coins);

        if (chipertext.compareTo(mid) <= 0) {
            return decrypted(chipertext, key, iv,
                    new Range(inEdge.add(BigInteger.ONE), x),
                    new Range(outEdge.add(BigInteger.ONE), mid));
        } else {
            return decrypted(chipertext, key, iv,
                    new Range(x.add(BigInteger.ONE), inEdge.add(inSize)),
                    new Range(mid.add(BigInteger.ONE), outEdge.add(outSize)));
        }
    }
}
