package com.upmerge.game;

/** Compact labels matching the 4.11 power-of-1024 notation. */
public final class NumberFormatter {
    private static final String[] SUFFIXES = {
            "", "K", "M", "B", "T", "Q", "Qi", "Sx", "Sp", "Oc", "No", "Dc"
    };
    private static final long[] DIVISORS = {
            1L, 1L << 10, 1L << 20, 1L << 30, 1L << 40, 1L << 50, 1L << 60
    };

    private NumberFormatter() { }

    /**
     * Values below 16,384 stay unabridged, as in 4.11. At each 1024x
     * boundary the suffix advances: 1024T is 1Q, 1024Q is 1Qi, etc.
     * The game generates powers of two, so the largest displayed mantissa
     * before a suffix transition is 512.
     */
    public static String compact(long value) {
        if (value <= 0L) return "0";
        if (value < 16384L) return Long.toString(value);
        int tier = 0;
        while (tier + 1 < SUFFIXES.length
                && tier + 1 < DIVISORS.length
                && value >= DIVISORS[tier + 1]) {
            tier++;
        }
        long divisor = DIVISORS[tier];
        long mantissa = value / divisor;
        return Long.toString(mantissa) + SUFFIXES[tier];
    }

    /** Fits a label inside a cube without changing its centered alignment. */
    public static float fitScale(String label, float baseScale, float availableWidth,
                                 float measuredWidthAtScaleOne) {
        if (label == null || label.isEmpty() || availableWidth <= 0f
                || measuredWidthAtScaleOne <= 0f) return baseScale;
        float naturalWidth = measuredWidthAtScaleOne * baseScale;
        return naturalWidth <= availableWidth ? baseScale
                : baseScale * (availableWidth / naturalWidth);
    }
}
