package io.sagittarius.panamakvm.core.device;

import java.lang.foreign.MemorySegment;
import java.util.List;

/** A device exposed through one or more guest-physical MMIO ranges. */
public interface MemoryMappedDevice extends AutoCloseable {
    /**
     * Returns the guest-physical ranges owned by this device.
     *
     * @return immutable or stable list of owned ranges
     */
    List<MemoryRange> ranges();

    /**
     * Returns whether the complete transfer belongs to one declared range.
     *
     * @param address first guest-physical byte
     * @param length transfer length
     * @return whether this device handles the transfer
     */
    default boolean handles(long address, long length) {
        return ranges().stream().anyMatch(range -> range.contains(address, length));
    }

    /**
     * Reads device state into the KVM transfer buffer.
     *
     * @param address guest-physical address
     * @param target writable transfer buffer
     */
    void read(long address, MemorySegment target);

    /**
     * Writes guest-provided bytes into the device.
     *
     * @param address guest-physical address
     * @param source transfer buffer
     */
    void write(long address, MemorySegment source);

    /** Releases device resources; stateless devices need not override it. */
    @Override
    default void close() { }
}
