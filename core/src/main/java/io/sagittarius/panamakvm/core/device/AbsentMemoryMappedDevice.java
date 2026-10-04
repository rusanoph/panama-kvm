package io.sagittarius.panamakvm.core.device;

import java.lang.foreign.MemorySegment;
import java.util.List;

/**
 * Fallback for absent MMIO hardware. Reads observe an all-ones bus and writes
 * have no effect, matching normal device-probing expectations.
 */
public final class AbsentMemoryMappedDevice implements MemoryMappedDevice {
    /** Creates a fallback device representing unpopulated MMIO space. */
    public AbsentMemoryMappedDevice() { }

    /**
     * Returns no specifically owned ranges because this device is only a bus fallback.
     *
     * @return empty range list
     */
    @Override
    public List<MemoryRange> ranges() {
        return List.of();
    }

    @Override
    public boolean handles(long address, long length) {
        return address >= 0 && length > 0 && length <= Long.MAX_VALUE - address;
    }

    @Override
    public void read(long address, MemorySegment target) {
        target.fill((byte) 0xff);
    }

    @Override
    public void write(long address, MemorySegment source) { }
}
