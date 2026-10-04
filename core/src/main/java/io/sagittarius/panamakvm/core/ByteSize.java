package io.sagittarius.panamakvm.core;

/**
 * An exact non-negative number of bytes used at API boundaries where a raw
 * {@code long} would hide the unit.
 *
 * @param bytes number of bytes
 */
public record ByteSize(long bytes) implements Comparable<ByteSize> {
    /** Number of bytes in one kibibyte. */
    public static final long KIBIBYTE = 1L << 10;
    /** Number of bytes in one mebibyte. */
    public static final long MEBIBYTE = 1L << 20;
    /** Number of bytes in one gibibyte. */
    public static final long GIBIBYTE = 1L << 30;

    /**
     * Validates the represented size.
     */
    public ByteSize {
        if (bytes < 0) {
            throw new IllegalArgumentException("Byte size must not be negative: " + bytes);
        }
    }

    /**
     * Creates a size expressed in bytes.
     *
     * @param bytes byte count
     * @return validated size
     */
    public static ByteSize ofBytes(long bytes) {
        return new ByteSize(bytes);
    }

    /**
     * Creates a size expressed in mebibytes (2^20 bytes).
     *
     * @param mebibytes mebibyte count
     * @return exact size
     */
    public static ByteSize ofMiB(long mebibytes) {
        return new ByteSize(Math.multiplyExact(mebibytes, MEBIBYTE));
    }

    /**
     * Aligns this size upward to a power-of-two boundary.
     *
     * @param alignment power-of-two alignment in bytes
     * @return aligned size
     */
    public ByteSize alignUp(long alignment) {
        if (alignment <= 0 || (alignment & (alignment - 1)) != 0) {
            throw new IllegalArgumentException("Alignment must be a positive power of two: " + alignment);
        }
        return new ByteSize(Math.addExact(bytes, alignment - 1) & -alignment);
    }

    @Override
    public int compareTo(ByteSize other) {
        return Long.compare(bytes, other.bytes);
    }
}
