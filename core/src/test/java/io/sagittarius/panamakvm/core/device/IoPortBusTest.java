package io.sagittarius.panamakvm.core.device;

import io.sagittarius.panamakvm.core.VmExit;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IoPortBusTest {
    @Test
    void dispatchesRepeatedLittleEndianOutput() {
        CapturingDevice device = new CapturingDevice();
        byte[] payload = {0x34, 0x12, 0x78, 0x56};
        new IoPortBus(List.of(device)).handle(new VmExit.Io(
                VmExit.IoDirection.OUT, 0x3f8, 2, 2, MemorySegment.ofArray(payload)));
        assertArrayEquals(new long[]{0x1234, 0x5678}, device.values);
    }

    @Test
    void writesInputValueIntoMappedExitBuffer() {
        byte[] payload = new byte[2];
        IoPortDevice device = new CapturingDevice() {
            @Override
            public long read(int port, int size) {
                return 0xbeef;
            }
        };
        new IoPortBus(List.of(device)).handle(new VmExit.Io(
                VmExit.IoDirection.IN, 0x3f8, 2, 1, MemorySegment.ofArray(payload)));
        assertEquals((byte) 0xef, payload[0]);
        assertEquals((byte) 0xbe, payload[1]);
    }

    @Test
    void rejectsOverlappingPortRanges() {
        IoPortDevice first = new CapturingDevice(new IoPortRange(0x3f8, 8));
        IoPortDevice second = new CapturingDevice(new IoPortRange(0x3ff, 2));

        assertThrows(IllegalArgumentException.class, () -> new IoPortBus(List.of(first, second)));
    }

    @Test
    void acceptsAdjacentPortRanges() {
        IoPortDevice first = new CapturingDevice(new IoPortRange(0x3f8, 8));
        IoPortDevice second = new CapturingDevice(new IoPortRange(0x400, 1));

        new IoPortBus(List.of(first, second)).close();
    }

    private static class CapturingDevice implements IoPortDevice {
        private final long[] values = new long[2];
        private final List<IoPortRange> ranges;
        private int index;

        private CapturingDevice() {
            this(new IoPortRange(0x3f8, 1));
        }

        private CapturingDevice(IoPortRange range) {
            ranges = List.of(range);
        }

        @Override
        public List<IoPortRange> ranges() {
            return ranges;
        }

        @Override
        public long read(int port, int size) {
            return 0;
        }

        @Override
        public void write(int port, int size, long value) {
            values[index++] = value;
        }
    }
}
