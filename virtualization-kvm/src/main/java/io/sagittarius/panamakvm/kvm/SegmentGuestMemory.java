package io.sagittarius.panamakvm.kvm;

import io.sagittarius.panamakvm.core.GuestMemory;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

/**
 * Guest-memory view backed by one native contiguous memory segment.
 */
record SegmentGuestMemory(MemorySegment segment) implements GuestMemory {
    SegmentGuestMemory(MemorySegment segment) {
        this.segment = Objects.requireNonNull(segment, "segment");
    }

    @Override
    public long byteSize() {
        return segment.byteSize();
    }

    @Override
    public MemorySegment slice(long guestPhysicalAddress, long byteSize) {
        return segment.asSlice(guestPhysicalAddress, byteSize);
    }
}
