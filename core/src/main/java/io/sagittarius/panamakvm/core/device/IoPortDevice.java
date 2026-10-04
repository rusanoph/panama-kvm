package io.sagittarius.panamakvm.core.device;

import java.util.List;

/**
 * A device addressed through an x86 I/O-port range.
 */
public interface IoPortDevice extends AutoCloseable {
    /**
     * Returns the I/O-port ranges owned by this device.
     *
     * @return immutable or stable list of owned ranges
     */
    List<IoPortRange> ranges();

    /**
     * Returns whether this device owns a port in one declared range.
     *
     * @param port unsigned 16-bit port
     * @return {@code true} when the device handles it
     */
    default boolean handles(int port) {
        return ranges().stream().anyMatch(range -> range.contains(port));
    }

    /**
     * Reads one value from a port.
     *
     * @param port unsigned 16-bit port
     * @param size value width in bytes
     * @return unsigned value in the low bits
     */
    long read(int port, int size);

    /**
     * Writes one value to a port.
     *
     * @param port unsigned 16-bit port
     * @param size value width in bytes
     * @param value unsigned value in the low bits
     */
    void write(int port, int size, long value);

    /** Releases device resources; stateless devices need not override it. */
    @Override
    default void close() { }
}
