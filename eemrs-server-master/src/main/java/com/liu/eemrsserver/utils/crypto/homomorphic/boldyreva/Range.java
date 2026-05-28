package com.liu.eemrsserver.utils.crypto.homomorphic.boldyreva;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

/**
 * Integer range for [min, max]
 */
public class Range {

    @Getter
    @Setter
    private BigInteger min;
    @Getter
    @Setter
    private BigInteger max;


    public Range(@NotNull BigInteger min, @NotNull BigInteger max) {
        if (min.compareTo(max) > 0) {
            throw new RuntimeException(min + " should be smaller than " + max);
        } else {
            this.min = min;
            this.max = max;
        }
    }

    public BigInteger size() {
        return max.subtract(min).add(BigInteger.ONE);
    }

    /**
     * How many bit need to encode all values in the range
     */
    public long bitSizeToEncodeAll() {
        return (long) Math.ceil(Math.log(size().longValueExact()) / Math.log(2));
    }

    public boolean contains(@NotNull BigInteger val) {
        return val.compareTo(min) >= 0 && val.compareTo(max) <= 0;
    }

    @Override
    public String toString() {
        return "[" + min + ", " + max + "]";
    }
}
