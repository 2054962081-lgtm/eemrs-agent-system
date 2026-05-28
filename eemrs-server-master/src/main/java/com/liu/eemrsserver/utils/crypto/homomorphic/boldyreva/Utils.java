package com.liu.eemrsserver.utils.crypto.homomorphic.boldyreva;


import java.math.BigInteger;
import java.util.stream.IntStream;

class Utils {

    static IntStream fromByte(byte b) {
        String binaryStr = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
        return binaryStr.chars().map(i -> i - '0');
    }

    private static String toBigIntegerStr(double d) {
        return String.format("%.0f", d);
    }


    static BigInteger sampleHGD(Range inRange, Range outRange, BigInteger nsample, TapeGen coins) {
        BigInteger inSize = inRange.size();
        BigInteger outSize = outRange.size();

        BigInteger nsampleIndex = nsample.subtract(outRange.getMin()).add(BigInteger.ONE);
        if (inSize.compareTo(outSize) == 0) {
            return inRange.getMin().add(nsampleIndex).subtract(BigInteger.ONE);
        }
        double inSampleNum = HGD.rhyper(nsampleIndex.doubleValue(),
                inSize.doubleValue(), outSize.subtract(inSize).doubleValue(), coins);
        if (inSampleNum == 0) {
            return inRange.getMin();
        } else {
            BigInteger inSample = inRange.getMin()
                    .add(new BigInteger(toBigIntegerStr(inSampleNum))).subtract(BigInteger.ONE);
            if (!inRange.contains(inSample)) {
                throw new RuntimeException("out of range");
            }
            return inSample;
        }
    }

    static BigInteger sampleUniform(Range inRange, TapeGen coins) {
        HGD.PRNG rng = new HGD.PRNG(coins);
        Range cur = new Range(inRange.getMin(), inRange.getMax());
        while (cur.size().compareTo(BigInteger.ONE) > 0) {
            BigInteger mid = inRange.getMax().add(inRange.getMin()).divide(BigInteger.valueOf(2));
            if (rng.nextBoolean()) {
                cur.setMin(mid.add(BigInteger.ONE));
            } else {
                cur.setMax(mid);
            }
        }
        return cur.getMin();
    }

}
