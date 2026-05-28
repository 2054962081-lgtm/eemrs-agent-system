package com.liu.eemrsserver.utils.crypto.homomorphic.boldyreva;


import java.math.BigInteger;
import java.util.stream.IntStream;


/**
 * Random variates from the hypergeometric distribution.
 * Returns the number of white balls drawn when kk balls
 * are drawn at random from an urn containing nn1 white
 * and nn2 black balls.
 */
class HGD {
    static class PRNG {
        private TapeGen coins;
        private int idx;
        private int[] cur;

        PRNG(TapeGen coins) {
            this.coins = coins;
            idx = 0;
            cur = coins.nextCoins();
        }

        double nextDouble() {
            BigInteger out = BigInteger.ZERO;
            for (int i : cur) {
                out = out.shiftLeft(1).add(BigInteger.valueOf(i));
            }
            cur = coins.nextCoins();
            return out.doubleValue() / (Math.pow(2, 32) - 1);
        }

        boolean nextBoolean() {
            boolean res = cur[idx++] == 1;
            if (idx >= 32) {
                idx = 0;
                cur = coins.nextCoins();
            }
            return res;
        }
    }

    static double rhyper(double sample, double good, double bad, TapeGen coins) {
        PRNG rng = new PRNG(coins);
        if (sample > 10) {
            return hypergeometricHrua(rng, good, bad, sample);
        } else {
            return hypergeometricHyp(rng, good, bad, sample);
        }
    }

    private static double hypergeometricHyp(PRNG rng, double good, double bad, double sample) {
        double d1 = good + bad - sample;
        double d2 = Math.min(bad, good);

        double Y = d2;
        double K = sample;
        while (Y > 0) {
            double U = rng.nextDouble();
            Y -= Math.floor(U + Y / (d1 + K));
            K -= 1;
            if (K == 0) break;
        }
        double Z = d2 - Y;
        if (good > bad) {
            Z = sample - Z;
        }
        return Z;
    }

    private static double hypergeometricHrua(PRNG rng, double good, double bad, double sample) {
        double D1 = 1.7155277699214135;
        double D2 = 0.8989161620588988;

        double minGoodBad = Math.min(good, bad);
        double popsize = good + bad;
        double maxGoodBad = Math.max(good, bad);
        double m = Math.min(popsize, popsize - sample);
        double d4 = minGoodBad / popsize;
        double d5 = 1 - d4;
        double d6 = m * d4 + 0.5;
        double d7 = Math.sqrt((popsize - m) * sample * d4 * d5 / (popsize - 1) + 0.5);
        double d8 = D1 * d7 + D2;
        double d9 = Math.floor((m + 1) * (minGoodBad + 1) / (popsize + 2));
        double d10 = loggam(d9 + 1) + loggam(minGoodBad - d9 + 1) + loggam(m - d9 + 1)
                + loggam(maxGoodBad - m + d9 + 1);
        double d11 = Math.min(Math.min(m, minGoodBad) + 1, Math.floor(d6 + 16 * d7));

        double Z;
        while (true) {
            double X = rng.nextDouble();
            double Y = rng.nextDouble();
            double W = d6 + d8 * (Y - 0.5) / X;

            if (W < 0 || W >= d11) continue;

            Z = Math.floor(W);
            double T = d10 - (loggam(Z + 1) + loggam(minGoodBad - Z + 1) + loggam(m - Z + 1)
                    + loggam(maxGoodBad - m + Z + 1));

            if ((X * (4 - X) - 3) <= T) break;

            if (X * (X - T) >= 1) continue;

            if (2 * Math.log(X) <= T) break;
        }
        if (good > bad) {
            Z = m - Z;
        }

        if (m < sample) {
            Z = good - Z;
        }

        return Z;
    }

    private static double loggam(double x) {
        double[] a = {
                8.333333333333333e-02, -2.777777777777778e-03,
                7.936507936507937e-04, -5.952380952380952e-04,
                8.417508417508418e-04, -1.917526917526918e-03,
                6.410256410256410e-03, -2.955065359477124e-02,
                1.796443723688307e-01, -1.39243221690590e+00
        };
        double x0 = x;
        int n = 0;
        if (x == 1 || x == 2) {
            return 0;
        } else if (x <= 7) {
            n = (int) (7 - x);
            x0 = x + n;
        }

        double x2 = 1 / (x0 * x0);
        double xp = 2 * Math.PI;
        double g10 = a[9];
        int[] range = {8, 7, 6, 5, 4, 3, 2, 1, 0};
        for (int i : range) {
            g10 *= x2;
            g10 += a[i];
        }
        double g1 = g10 / x0 + 0.5 * Math.log(xp) + (x0 - 0.5) * Math.log(x0) - x0;
        if (x <= 7) {
            for (int i : IntStream.range(1, n + 1).toArray()) {
                g1 -= Math.log(x0 - 1);
                x0 -= 1;
            }
        }
        return g1;
    }
}
