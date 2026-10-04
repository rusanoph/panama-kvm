package io.sagittarius.panamakvm.core.device;

/**
 * Contiguous non-empty range in the unsigned 16-bit x86 I/O-port space.
 *
 * @param start first port in the range
 * @param count number of ports in the range
 */
public record IoPortRange(int start, int count) {
    /** Highest valid x86 I/O-port number. */
    public static final int MAX_PORT = 0xffff;

    /** Validates that the complete range lies in the I/O-port space. */
    public IoPortRange {
        if (start < 0 || start > MAX_PORT) {
            throw new IllegalArgumentException("I/O-port range start must be an unsigned 16-bit port");
        }
        if (count <= 0 || count > MAX_PORT + 1 - start) {
            throw new IllegalArgumentException("I/O-port range must be non-empty and remain in 16-bit space");
        }
    }

    /**
     * Returns the first port after this range.
     *
     * @return exclusive end port, possibly {@code 0x10000}
     */
    public int endExclusive() {
        return start + count;
    }

    /**
     * Tests whether a port belongs to this range.
     *
     * @param port port number
     * @return whether the port is contained
     */
    public boolean contains(int port) {
        return port >= start && port < endExclusive();
    }

    /**
     * Tests whether this range and another range share at least one port.
     *
     * @param other range to compare
     * @return whether the ranges overlap
     */
    public boolean overlaps(IoPortRange other) {
        return start < other.endExclusive() && other.start < endExclusive();
    }

    @Override
    public String toString() {
        return "[0x" + Integer.toHexString(start) + ", 0x" + Integer.toHexString(endExclusive()) + ")";
    }
}
