package io.sagittarius.panamakvm.core.device;

import java.util.List;

/**
 * Minimal MC146818-compatible CMOS real-time clock for legacy x86 guests.
 *
 * <p>The model deliberately supplies a stable calendar and reports that an
 * update is never in progress. This is sufficient for Linux probing on a
 * direct-boot VMM without emulating a wall-clock tick source.</p>
 */
public final class CmosRtc implements IoPortDevice {
    /** CMOS index port. */
    public static final int INDEX_PORT = 0x70;
    /** CMOS data port. */
    public static final int DATA_PORT = 0x71;

    private static final List<IoPortRange> PORT_RANGES = List.of(new IoPortRange(INDEX_PORT, 2));

    private static final int REGISTER_A = 0x0a;
    private static final int REGISTER_B = 0x0b;
    private static final int REGISTER_C = 0x0c;
    private static final int REGISTER_D = 0x0d;
    private static final int UPDATE_IN_PROGRESS = 0x80;

    private final int[] registers = new int[128];
    private int selectedRegister;

    /**
     * Creates a CMOS RTC with a stable, valid binary-mode calendar.
     */
    public CmosRtc() {
        registers[0x00] = 0;
        registers[0x02] = 0;
        registers[0x04] = 0;
        registers[0x06] = 1;
        registers[0x07] = 1;
        registers[0x08] = 1;
        registers[0x09] = 26;
        registers[0x32] = 20;
        registers[REGISTER_A] = 0x26;
        registers[REGISTER_B] = 0x06;
        registers[REGISTER_C] = 0;
        registers[REGISTER_D] = 0x80;
    }

    @Override
    public List<IoPortRange> ranges() {
        return PORT_RANGES;
    }

    @Override
    public synchronized long read(int port, int size) {
        requireByteAccess(port, size);
        if (port == INDEX_PORT) {
            return selectedRegister;
        }
        return selectedRegister == REGISTER_A
                ? registers[REGISTER_A] & ~UPDATE_IN_PROGRESS
                : registers[selectedRegister];
    }

    @Override
    public synchronized void write(int port, int size, long value) {
        requireByteAccess(port, size);
        int byteValue = (int) value & 0xff;
        if (port == INDEX_PORT) {
            selectedRegister = byteValue & 0x7f;
        } else if (selectedRegister == REGISTER_A) {
            registers[REGISTER_A] = byteValue & ~UPDATE_IN_PROGRESS;
        } else if (selectedRegister != REGISTER_C && selectedRegister != REGISTER_D) {
            registers[selectedRegister] = byteValue;
        }
    }

    private static void requireByteAccess(int port, int size) {
        if (port != INDEX_PORT && port != DATA_PORT) {
            throw new IllegalArgumentException("Port is outside this CMOS RTC: 0x" + Integer.toHexString(port));
        }
        if (size != 1) {
            throw new IllegalArgumentException("CMOS RTC register access must be one byte, got " + size);
        }
    }
}
