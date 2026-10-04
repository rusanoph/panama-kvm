package io.sagittarius.panamakvm.kvm.boot;

import io.sagittarius.panamakvm.core.GuestMemory;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinuxBootLoaderTest {
    @Test
    void buildsZeroPageMemoryMapCommandLineAndInitrd() {
        byte[] storage = new byte[16 << 20];
        GuestMemory memory = heapMemory(storage);
        LinuxKernelImage kernel = LinuxKernelImage.parse(LinuxKernelImageTest.kernelImage());
        byte[] initrd = {1, 2, 3, 4};

        LinuxBootLayout layout = new LinuxBootLoader().load(memory, kernel, initrd,
                "console=ttyS0 rdinit=/init");

        MemorySegment zeroPage = memory.slice(layout.bootParameters(), LinuxBootLayout.BOOT_PARAMETERS_SIZE);
        assertEquals(3, Byte.toUnsignedInt(zeroPage.get(JAVA_BYTE, LinuxBootLoader.E820_ENTRY_COUNT_OFFSET)));
        assertEquals(LinuxBootLoader.E820_RAM,
                zeroPage.get(JAVA_INT_UNALIGNED, LinuxBootLoader.E820_TABLE_OFFSET + 16));
        assertEquals(LinuxBootLoader.HIGH_MEMORY_START,
                zeroPage.get(JAVA_LONG_UNALIGNED,
                        LinuxBootLoader.E820_TABLE_OFFSET + 2 * LinuxBootLoader.E820_ENTRY_SIZE));
        assertEquals(layout.commandLine(), Integer.toUnsignedLong(
                zeroPage.get(JAVA_INT_UNALIGNED, LinuxBootLoader.COMMAND_LINE_POINTER_OFFSET)));
        assertEquals(1, storage[Math.toIntExact(layout.initrd())]);
        assertTrue(layout.initrd() > LinuxBootLayout.KERNEL_LOAD_ADDRESS + kernel.initializationSize());
    }

    private static GuestMemory heapMemory(byte[] storage) {
        MemorySegment segment = MemorySegment.ofArray(storage);
        return new GuestMemory() {
            @Override
            public long byteSize() {
                return storage.length;
            }

            @Override
            public MemorySegment slice(long guestPhysicalAddress, long byteSize) {
                return segment.asSlice(guestPhysicalAddress, byteSize);
            }
        };
    }
}
