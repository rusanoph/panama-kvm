package io.sagittarius.panamakvm.core;

import java.lang.foreign.MemorySegment;

/**
 * Bounds-checked access to contiguous guest-physical memory.
 * Implementations own the storage; slices remain valid only while their
 * owning virtual machine remains open.
 */
public interface GuestMemory {
    /**
     * Returns the guest-physical address space size.
     *
     * @return size in bytes
     */
    long byteSize();

    /**
     * Returns a zero-copy slice of guest memory.
     *
     * @param guestPhysicalAddress first guest-physical byte
     * @param byteSize slice size
     * @return bounded memory slice
     */
    MemorySegment slice(long guestPhysicalAddress, long byteSize);

    /**
     * Copies bytes into guest memory.
     *
     * @param guestPhysicalAddress destination guest-physical address
     * @param bytes source bytes
     */
    default void write(long guestPhysicalAddress, byte[] bytes) {
        slice(guestPhysicalAddress, bytes.length).copyFrom(MemorySegment.ofArray(bytes));
    }

    /**
     * Fills a guest-memory range with a byte value.
     *
     * @param guestPhysicalAddress first guest-physical byte
     * @param byteSize number of bytes
     * @param value fill value
     */
    default void fill(long guestPhysicalAddress, long byteSize, byte value) {
        slice(guestPhysicalAddress, byteSize).fill(value);
    }
}
