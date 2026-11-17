package com.upmerge.game;

/** Shared flat-color palette for board cubes, previews and merge-flight cubes. */
final class CubePalette {
    private CubePalette() { }

    static int rgb(long value) {
        switch ((int) value) {
            case 2: return 0x64D9FF;
            case 4: return 0x60E5C4;
            case 8: return 0x8FE36E;
            case 16: return 0xFFDB69;
            case 32: return 0xFF9B57;
            case 64: return 0xFF657D;
            case 128: return 0xBB86FF;
            case 256: return 0x8292FF;
            case 512: return 0xFF75C8;
            case 1024: return 0xF5F5FF;
            default:
                if (value >= 2048 && (value & (value - 1)) == 0) {
                    float hue = (Long.numberOfTrailingZeros(value) * .105f) % 1f;
                    int red = Math.round((.45f + .45f * hue) * 255f);
                    int green = Math.round((.80f - .20f * hue) * 255f);
                    int blue = Math.round(.95f * 255f);
                    return (red << 16) | (green << 8) | blue;
                }
                return 0xF5F5FF;
        }
    }
}
