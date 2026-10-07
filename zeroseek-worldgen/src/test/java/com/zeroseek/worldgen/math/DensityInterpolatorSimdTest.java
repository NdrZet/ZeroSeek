package com.zeroseek.worldgen.math;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DensityInterpolatorSimdTest {

    private static double scalarLerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    @Test
    public void testInterpolateYSimdParity() {
        Random rng = new Random(1337);
        double[] cornersY0 = new double[4];
        double[] cornersY1 = new double[4];
        double[] outSimd = new double[4];

        for (int i = 0; i < 1000; i++) {
            cornersY0[0] = rng.nextDouble() * 200 - 100;
            cornersY1[0] = rng.nextDouble() * 200 - 100;
            cornersY0[1] = rng.nextDouble() * 200 - 100;
            cornersY1[1] = rng.nextDouble() * 200 - 100;
            cornersY0[2] = rng.nextDouble() * 200 - 100;
            cornersY1[2] = rng.nextDouble() * 200 - 100;
            cornersY0[3] = rng.nextDouble() * 200 - 100;
            cornersY1[3] = rng.nextDouble() * 200 - 100;
            double deltaY = rng.nextDouble();

            DensityInterpolatorSimd.interpolateYSimd(
                    cornersY0, cornersY1,
                    deltaY,
                    outSimd
            );

            double expected0 = scalarLerp(deltaY, cornersY0[0], cornersY1[0]);
            double expected1 = scalarLerp(deltaY, cornersY0[1], cornersY1[1]);
            double expected2 = scalarLerp(deltaY, cornersY0[2], cornersY1[2]);
            double expected3 = scalarLerp(deltaY, cornersY0[3], cornersY1[3]);

            assertEquals(expected0, outSimd[0], 1e-12, "Y lerp 0 mismatch");
            assertEquals(expected1, outSimd[1], 1e-12, "Y lerp 1 mismatch");
            assertEquals(expected2, outSimd[2], 1e-12, "Y lerp 2 mismatch");
            assertEquals(expected3, outSimd[3], 1e-12, "Y lerp 3 mismatch");
        }
    }

    @Test
    public void testPrecomputeZSimdParity() {
        Random rng = new Random(42);
        double[] outZSimd = new double[4];

        for (int i = 0; i < 1000; i++) {
            double z0 = rng.nextDouble() * 200 - 100;
            double z1 = rng.nextDouble() * 200 - 100;

            DensityInterpolatorSimd.precomputeZSimd(z0, z1, outZSimd);

            double expZ0 = scalarLerp(0.0, z0, z1);
            double expZ1 = scalarLerp(0.25, z0, z1);
            double expZ2 = scalarLerp(0.50, z0, z1);
            double expZ3 = scalarLerp(0.75, z0, z1);

            assertEquals(expZ0, outZSimd[0], 1e-12, "Z slot 0 mismatch");
            assertEquals(expZ1, outZSimd[1], 1e-12, "Z slot 1 mismatch");
            assertEquals(expZ2, outZSimd[2], 1e-12, "Z slot 2 mismatch");
            assertEquals(expZ3, outZSimd[3], 1e-12, "Z slot 3 mismatch");
        }
    }
}
