package io.sagittarius.panamakvm.kvm.boot;

import io.sagittarius.panamakvm.core.GuestMemory;

import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;

/** Builds Linux {@code boot_params}, E820, command line, kernel, and initramfs in guest RAM. */
public final class LinuxBootLoader {
    /** {@code boot_params.alt_mem_k} offset. */
    public static final long ALTERNATE_MEMORY_KIB_OFFSET = 0x1e0;
    /** {@code boot_params.e820_entries} offset. */
    public static final long E820_ENTRY_COUNT_OFFSET = 0x1e8;
    /** First {@code boot_e820_entry} offset. */
    public static final long E820_TABLE_OFFSET = 0x2d0;
    /** Packed {@code boot_e820_entry} byte size. */
    public static final long E820_ENTRY_SIZE = 20;
    /** {@code setup_header.type_of_loader} offset. */
    public static final long LOADER_TYPE_OFFSET = 0x210;
    /** {@code setup_header.ramdisk_image} offset. */
    public static final long RAMDISK_IMAGE_OFFSET = 0x218;
    /** {@code setup_header.ramdisk_size} offset. */
    public static final long RAMDISK_SIZE_OFFSET = 0x21c;
    /** {@code setup_header.cmd_line_ptr} offset. */
    public static final long COMMAND_LINE_POINTER_OFFSET = 0x228;
    /** Unassigned boot-loader ID, valid for private loaders. */
    public static final int UNASSIGNED_LOADER_ID = 0xff;
    /** E820 type 1: usable RAM. */
    public static final int E820_RAM = 1;
    /** E820 type 2: reserved address range. */
    public static final int E820_RESERVED = 2;
    /** End of conventional RAM below the EBDA/VGA hole. */
    public static final long LOW_MEMORY_END = 0x0009_fc00L;
    /** End of the conventional x86 firmware/VGA hole. */
    public static final long HIGH_MEMORY_START = 0x0010_0000L;

    /** Creates a direct Linux boot-protocol loader. */
    public LinuxBootLoader() { }

    /**
     * Loads all direct-boot artifacts into guest memory.
     *
     * @param memory zeroed guest RAM
     * @param kernel parsed bzImage
     * @param initrd optional initramfs bytes; {@code null} means absent
     * @param commandLine kernel command line
     * @return selected physical layout
     */
    public LinuxBootLayout load(
            GuestMemory memory,
            LinuxKernelImage kernel,
            byte[] initrd,
            String commandLine
    ) {
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(commandLine, "commandLine");
        if (memory.byteSize() <= HIGH_MEMORY_START) {
            throw new IllegalArgumentException("Guest memory must extend above 1 MiB");
        }

        byte[] commandLineBytes = commandLine.getBytes(StandardCharsets.UTF_8);
        long advertisedMaximum = kernel.commandLineSize() == 0 ? 255 : kernel.commandLineSize();
        if (commandLineBytes.length + 1 > advertisedMaximum) {
            throw new IllegalArgumentException("Kernel command line needs " + (commandLineBytes.length + 1)
                    + " bytes but bzImage advertises " + advertisedMaximum);
        }

        long requiredKernelBytes = Math.max(kernel.protectedPayloadSize(), kernel.initializationSize());
        long kernelEnd = Math.addExact(LinuxBootLayout.KERNEL_LOAD_ADDRESS, requiredKernelBytes);
        if (kernelEnd > memory.byteSize()) {
            throw new IllegalArgumentException("Guest RAM is too small for kernel initialization: need 0x"
                    + Long.toHexString(kernelEnd));
        }

        long initrdSize = initrd == null ? 0 : initrd.length;
        long initrdAddress = selectInitrdAddress(memory.byteSize(), kernel.initrdAddressMax(), initrdSize, kernelEnd);
        LinuxBootLayout layout = new LinuxBootLayout(
                LinuxBootLayout.BOOT_PARAMETERS_ADDRESS,
                LinuxBootLayout.COMMAND_LINE_ADDRESS,
                LinuxBootLayout.KERNEL_LOAD_ADDRESS,
                initrdAddress,
                initrdSize
        );

        MemorySegment zeroPage = memory.slice(layout.bootParameters(), LinuxBootLayout.BOOT_PARAMETERS_SIZE);
        zeroPage.fill((byte) 0);
        kernel.copySetupHeader(zeroPage);
        zeroPage.set(JAVA_BYTE, LOADER_TYPE_OFFSET, (byte) UNASSIGNED_LOADER_ID);
        zeroPage.set(JAVA_INT_UNALIGNED, COMMAND_LINE_POINTER_OFFSET, Math.toIntExact(layout.commandLine()));
        zeroPage.set(JAVA_INT_UNALIGNED, ALTERNATE_MEMORY_KIB_OFFSET,
                Math.toIntExact((memory.byteSize() - HIGH_MEMORY_START) / 1024));

        writeMemoryMap(zeroPage, memory.byteSize());
        if (initrdSize != 0) {
            zeroPage.set(JAVA_INT_UNALIGNED, RAMDISK_IMAGE_OFFSET, Math.toIntExact(initrdAddress));
            zeroPage.set(JAVA_INT_UNALIGNED, RAMDISK_SIZE_OFFSET, Math.toIntExact(initrdSize));
            memory.write(initrdAddress, initrd);
        }

        memory.write(layout.commandLine(), commandLineBytes);
        memory.slice(layout.commandLine() + commandLineBytes.length, 1).set(JAVA_BYTE, 0, (byte) 0);
        kernel.loadProtectedPayload(memory, layout.kernel());
        return layout;
    }

    private static long selectInitrdAddress(long memorySize, long imageMaximum, long initrdSize, long kernelEnd) {
        if (initrdSize == 0) {
            return 0;
        }
        long maximumExclusive = Math.min(memorySize, Math.addExact(imageMaximum, 1));
        long candidate = alignDown(Math.subtractExact(maximumExclusive, initrdSize), LinuxBootLayout.INITRD_ALIGNMENT);
        if (candidate < kernelEnd) {
            throw new IllegalArgumentException("Guest RAM has no non-overlapping initrd range below 0x"
                    + Long.toHexString(imageMaximum));
        }
        return candidate;
    }

    private static void writeMemoryMap(MemorySegment zeroPage, long memorySize) {
        zeroPage.set(JAVA_BYTE, E820_ENTRY_COUNT_OFFSET, (byte) 3);
        writeE820(zeroPage, 0, 0, LOW_MEMORY_END, E820_RAM);
        writeE820(zeroPage, 1, LOW_MEMORY_END, HIGH_MEMORY_START - LOW_MEMORY_END, E820_RESERVED);
        writeE820(zeroPage, 2, HIGH_MEMORY_START, memorySize - HIGH_MEMORY_START, E820_RAM);
    }

    private static void writeE820(MemorySegment page, int index, long address, long size, int type) {
        long offset = E820_TABLE_OFFSET + Math.multiplyExact(index, E820_ENTRY_SIZE);
        page.set(JAVA_LONG_UNALIGNED, offset, address);
        page.set(JAVA_LONG_UNALIGNED, offset + Long.BYTES, size);
        page.set(JAVA_INT_UNALIGNED, offset + 2L * Long.BYTES, type);
    }

    private static long alignDown(long value, long alignment) {
        return value & -alignment;
    }
}
