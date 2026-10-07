package com.zeroseek.worldgen.math;

/**
 * AVX2-accelerated trilinear density interpolator for Minecraft 1.21.11 world generation.
 * Vectorizes horizontal and vertical cell interpolations across 4 double lanes using Java 22 Vector API.
 * Employs isolated classloading guards for 100% safe fallback across diverse runtime launchers.
 */
public final class DensityInterpolatorSimd {

    private static final boolean VECTOR_API_AVAILABLE;

    static {
        boolean available = false;
        try {
            Class.forName("jdk.incubator.vector.DoubleVector");
            available = true;
        } catch (Throwable ignored) {
        }
        VECTOR_API_AVAILABLE = available;
    }

    private static final class VectorOps {
        private static final jdk.incubator.vector.VectorSpecies<Double> SPECIES =
                jdk.incubator.vector.DoubleVector.SPECIES_256;
        private static final jdk.incubator.vector.DoubleVector DELTA_Z_WEIGHTS =
                jdk.incubator.vector.DoubleVector.fromArray(SPECIES, new double[]{0.0, 0.25, 0.50, 0.75}, 0);

        static void interpolateYSimd(double[] cornersY0, double[] cornersY1, double deltaY, double[] outXZ) {
            var v0 = jdk.incubator.vector.DoubleVector.fromArray(SPECIES, cornersY0, 0);
            var v1 = jdk.incubator.vector.DoubleVector.fromArray(SPECIES, cornersY1, 0);
            var vDeltaY = jdk.incubator.vector.DoubleVector.broadcast(SPECIES, deltaY);
            var result = v0.add(v1.sub(v0).mul(vDeltaY));
            result.intoArray(outXZ, 0);
        }

        static void precomputeZSimd(double z0, double z1, double[] outZ4) {
            var vz0 = jdk.incubator.vector.DoubleVector.broadcast(SPECIES, z0);
            var vDiff = jdk.incubator.vector.DoubleVector.broadcast(SPECIES, z1 - z0);
            var vz = vz0.add(vDiff.mul(DELTA_Z_WEIGHTS));
            vz.intoArray(outZ4, 0);
        }
    }

    public static boolean isVectorApiAvailable() {
        return VECTOR_API_AVAILABLE;
    }

    /**
     * Vectorized Y-interpolation across corner vectors using 256-bit AVX2 FMA.
     */
    public static void interpolateYSimd(double[] cornersY0, double[] cornersY1, double deltaY, double[] outXZ) {
        if (VECTOR_API_AVAILABLE) {
            VectorOps.interpolateYSimd(cornersY0, cornersY1, deltaY, outXZ);
        } else {
            outXZ[0] = cornersY0[0] + deltaY * (cornersY1[0] - cornersY0[0]);
            outXZ[1] = cornersY0[1] + deltaY * (cornersY1[1] - cornersY0[1]);
            outXZ[2] = cornersY0[2] + deltaY * (cornersY1[2] - cornersY0[2]);
            outXZ[3] = cornersY0[3] + deltaY * (cornersY1[3] - cornersY0[3]);
        }
    }

    /**
     * Vectorized simultaneous 4-way Z slot precomputation using AVX2.
     */
    public static void precomputeZSimd(double z0, double z1, double[] outZ4) {
        if (VECTOR_API_AVAILABLE) {
            VectorOps.precomputeZSimd(z0, z1, outZ4);
        } else {
            precomputeZScalar(z0, z1, outZ4);
        }
    }

    /**
     * Scalar FMA fallback for precomputing 4 Z slots.
     */
    public static void precomputeZScalar(double z0, double z1, double[] outZ4) {
        double diff = z1 - z0;
        outZ4[0] = z0;
        outZ4[1] = z0 + diff * 0.25;
        outZ4[2] = z0 + diff * 0.50;
        outZ4[3] = z0 + diff * 0.75;
    }
}
