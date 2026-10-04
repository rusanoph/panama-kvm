package io.sagittarius.panamakvm.core.device;

import java.util.List;

/**
 * Fallback model for an absent legacy x86 device: reads return an all-ones bus
 * value and writes are ignored. Use it only as an explicit bus fallback so a
 * concrete device always wins.
 */
public final class AbsentIoPortDevice implements IoPortDevice {
    /** Creates a fallback device representing unpopulated I/O space. */
    public AbsentIoPortDevice() { }

    /**
     * Returns no specifically owned ranges because this device is only a bus fallback.
     *
     * @return empty range list
     */
    @Override
    public List<IoPortRange> ranges() {
        return List.of();
    }

    @Override
    public boolean handles(int port) {
        return port >= 0 && port <= IoPortRange.MAX_PORT;
    }

    @Override
    public long read(int port, int size) {
        return switch (size) {
            case 1 -> 0xffL;
            case 2 -> 0xffffL;
            case 4 -> 0xffff_ffffL;
            default -> throw new IllegalArgumentException("Unsupported x86 I/O width: " + size);
        };
    }

    @Override
    public void write(int port, int size, long value) {
        if (size != 1 && size != 2 && size != 4) {
            throw new IllegalArgumentException("Unsupported x86 I/O width: " + size);
        }
    }
}
