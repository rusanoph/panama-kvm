package io.sagittarius.panamakvm.core.device;

import io.sagittarius.panamakvm.core.VmExit;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemoryMappedIoBusTest {
    @Test
    void absentDeviceReturnsAllOnes() {
        byte[] bytes = new byte[4];
        VmExit.Mmio exit = new VmExit.Mmio(0xfed0_0000L, false, MemorySegment.ofArray(bytes));
        new MemoryMappedIoBus(List.of(), new AbsentMemoryMappedDevice()).handle(exit);
        assertArrayEquals(new byte[]{-1, -1, -1, -1}, bytes);
    }

    @Test
    void dispatchesAccessInsideDeclaredRange() {
        byte[] bytes = new byte[4];
        MemoryMappedDevice device = new FillingDevice(new MemoryRange(0x1000, 0x100), (byte) 0x5a);

        new MemoryMappedIoBus(List.of(device)).handle(
                new VmExit.Mmio(0x10fc, false, MemorySegment.ofArray(bytes)));

        assertArrayEquals(new byte[]{0x5a, 0x5a, 0x5a, 0x5a}, bytes);
    }

    @Test
    void rejectsTransferCrossingRangeEnd() {
        MemoryMappedDevice device = new FillingDevice(new MemoryRange(0x1000, 0x100), (byte) 0);
        VmExit.Mmio exit = new VmExit.Mmio(0x10fe, false, MemorySegment.ofArray(new byte[4]));

        assertThrows(IllegalStateException.class, () -> new MemoryMappedIoBus(List.of(device)).handle(exit));
    }

    @Test
    void rejectsOverlappingMemoryRanges() {
        MemoryMappedDevice first = new FillingDevice(new MemoryRange(0x1000, 0x100), (byte) 1);
        MemoryMappedDevice second = new FillingDevice(new MemoryRange(0x1080, 0x100), (byte) 2);

        assertThrows(IllegalArgumentException.class,
                () -> new MemoryMappedIoBus(List.of(first, second)));
    }

    @Test
    void acceptsAdjacentMemoryRanges() {
        MemoryMappedDevice first = new FillingDevice(new MemoryRange(0x1000, 0x100), (byte) 1);
        MemoryMappedDevice second = new FillingDevice(new MemoryRange(0x1100, 0x100), (byte) 2);

        new MemoryMappedIoBus(List.of(first, second)).close();
        assertEquals(0x1100, first.ranges().getFirst().endExclusive());
    }

    private record FillingDevice(MemoryRange range, byte value) implements MemoryMappedDevice {
        @Override
        public List<MemoryRange> ranges() {
            return List.of(range);
        }

        @Override
        public void read(long address, MemorySegment target) {
            target.fill(value);
        }

        @Override
        public void write(long address, MemorySegment source) { }
    }
}
