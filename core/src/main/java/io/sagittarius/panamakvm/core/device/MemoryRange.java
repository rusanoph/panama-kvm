package io.sagittarius.panamakvm.core.device;

/**
 * Contiguous non-empty range in the non-negative guest-physical address space.
 *
 * @param start first address in the range
 * @param length number of bytes in the range
 */
public record MemoryRange(long start, long length) {
    /** Validates the range and rejects an overflowing end address. */
    public MemoryRange {
        if (start < 0) {
            throw new IllegalArgumentException("Memory range start must not be negative");
        }
        if (length <= 0) {
            throw new IllegalArgumentException("Memory range length must be positive");
        }
        try {
            Math.addExact(start, length);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Memory range end overflows signed 64-bit address space", exception);
        }
    }

    /**
     * Returns the first address after this range.
     *
     * @return exclusive end address
     */
    public long endExclusive() {
        return start + length;
    }

    /**
     * Tests whether a complete transfer lies inside this range.
     *
     * @param address first transfer address
     * @param transferLength transfer size in bytes
     * @return whether the complete transfer is contained
     */
    public boolean contains(long address, long transferLength) {
        if (address < start || transferLength <= 0 || address >= endExclusive()) {
            return false;
        }
        return transferLength <= endExclusive() - address;
    }

    /**
     * Tests whether this range and another range share at least one address.
     *
     * @param other range to compare
     * @return whether the ranges overlap
     */
    public boolean overlaps(MemoryRange other) {
        return start < other.endExclusive() && other.start < endExclusive();
    }

    @Override
    public String toString() {
        return "[0x" + Long.toHexString(start) + ", 0x" + Long.toHexString(endExclusive()) + ")";
    }
}
