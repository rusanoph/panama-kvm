package io.sagittarius.panamakvm.kvm.boot;

import io.sagittarius.panamakvm.core.GuestMemory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/**
 * Validated modern x86 Linux bzImage metadata and payload.
 * The parser follows the documented Linux/x86 boot protocol rather than a
 * distribution-specific image convention.
 */
public final class LinuxKernelImage {
    /** Legacy Linux boot signature {@code 0xAA55} offset. */
    public static final int BOOT_FLAG_OFFSET = 0x1fe;
    /** Linux protocol signature {@code HdrS} offset. */
    public static final int HEADER_MAGIC_OFFSET = 0x202;
    /** Little-endian integer representation of ASCII {@code HdrS}. */
    public static final int HEADER_MAGIC = 0x5372_6448;
    /** Minimum protocol supported by this direct 32-bit loader (2.06). */
    public static final int MINIMUM_PROTOCOL = 0x0206;
    /** {@code setup_sects} byte offset. */
    public static final int SETUP_SECTORS_OFFSET = 0x1f1;
    /** {@code version} field offset. */
    public static final int PROTOCOL_VERSION_OFFSET = 0x206;
    /** {@code loadflags} field offset. */
    public static final int LOAD_FLAGS_OFFSET = 0x211;
    /** {@code initrd_addr_max} field offset. */
    public static final int INITRD_ADDRESS_MAX_OFFSET = 0x22c;
    /** {@code kernel_alignment} field offset. */
    public static final int KERNEL_ALIGNMENT_OFFSET = 0x230;
    /** {@code relocatable_kernel} field offset. */
    public static final int RELOCATABLE_OFFSET = 0x234;
    /** {@code cmdline_size} field offset. */
    public static final int COMMAND_LINE_SIZE_OFFSET = 0x238;
    /** {@code init_size} field offset. */
    public static final int INITIALIZATION_SIZE_OFFSET = 0x260;
    /** {@code LOADED_HIGH}: protected payload belongs at 1 MiB. */
    public static final int LOADED_HIGH = 1;
    /** Bytes in one Linux boot-protocol sector. */
    public static final int BOOT_SECTOR_SIZE = 512;

    private final byte[] image;
    private final int setupSectors;
    private final int protocolVersion;
    private final int loadFlags;
    private final int protectedPayloadOffset;
    private final long initrdAddressMax;
    private final long commandLineSize;
    private final long kernelAlignment;
    private final boolean relocatable;
    private final long initializationSize;
    private final int setupHeaderEnd;

    private LinuxKernelImage(byte[] image) {
        this.image = image;
        ByteBuffer header = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
        this.setupSectors = Byte.toUnsignedInt(image[SETUP_SECTORS_OFFSET]) == 0
                ? 4 : Byte.toUnsignedInt(image[SETUP_SECTORS_OFFSET]);
        this.protocolVersion = Short.toUnsignedInt(header.getShort(PROTOCOL_VERSION_OFFSET));
        this.loadFlags = Byte.toUnsignedInt(image[LOAD_FLAGS_OFFSET]);
        this.protectedPayloadOffset = Math.multiplyExact(setupSectors + 1, BOOT_SECTOR_SIZE);
        this.initrdAddressMax = Integer.toUnsignedLong(header.getInt(INITRD_ADDRESS_MAX_OFFSET));
        this.kernelAlignment = Integer.toUnsignedLong(header.getInt(KERNEL_ALIGNMENT_OFFSET));
        this.relocatable = image[RELOCATABLE_OFFSET] != 0;
        this.commandLineSize = Integer.toUnsignedLong(header.getInt(COMMAND_LINE_SIZE_OFFSET));
        this.initializationSize = Integer.toUnsignedLong(header.getInt(INITIALIZATION_SIZE_OFFSET));
        this.setupHeaderEnd = 0x202 + Byte.toUnsignedInt(image[0x201]);
    }

    /**
     * Reads and parses an x86 Linux kernel image.
     *
     * @param path bzImage path
     * @return validated image
     * @throws IOException when the image cannot be read
     */
    public static LinuxKernelImage read(Path path) throws IOException {
        return parse(Files.readAllBytes(Objects.requireNonNull(path, "path")));
    }

    /**
     * Parses an in-memory x86 Linux kernel image.
     *
     * @param image complete bzImage bytes
     * @return validated image
     */
    public static LinuxKernelImage parse(byte[] image) {
        Objects.requireNonNull(image, "image");
        if (image.length < 0x268) {
            throw new IllegalArgumentException("Linux kernel image is too short for a modern setup header");
        }
        ByteBuffer header = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
        int bootFlag = Short.toUnsignedInt(header.getShort(BOOT_FLAG_OFFSET));
        if (bootFlag != 0xaa55) {
            throw new IllegalArgumentException("Linux boot flag mismatch: expected 0xAA55, got 0x"
                    + Integer.toHexString(bootFlag));
        }
        int magic = header.getInt(HEADER_MAGIC_OFFSET);
        if (magic != HEADER_MAGIC) {
            throw new IllegalArgumentException("Not a Linux bzImage: HdrS signature is missing");
        }
        int protocol = Short.toUnsignedInt(header.getShort(PROTOCOL_VERSION_OFFSET));
        if (protocol < MINIMUM_PROTOCOL) {
            throw new IllegalArgumentException("Linux boot protocol 0x" + Integer.toHexString(protocol)
                    + " is older than supported version 0x" + Integer.toHexString(MINIMUM_PROTOCOL));
        }
        int loadFlags = Byte.toUnsignedInt(image[LOAD_FLAGS_OFFSET]);
        if ((loadFlags & LOADED_HIGH) == 0) {
            throw new IllegalArgumentException("zImage is not supported; a bzImage with LOADED_HIGH is required");
        }

        LinuxKernelImage parsed = new LinuxKernelImage(Arrays.copyOf(image, image.length));
        if (parsed.protectedPayloadOffset >= image.length) {
            throw new IllegalArgumentException("Kernel setup sectors consume the entire image");
        }
        if (parsed.setupHeaderEnd < 0x202 || parsed.setupHeaderEnd > image.length) {
            throw new IllegalArgumentException("Linux setup-header boundary lies outside the image");
        }
        return parsed;
    }

    /**
     * Copies the protected-mode payload into guest memory.
     *
     * @param memory target guest memory
     * @param address load address
     */
    public void loadProtectedPayload(GuestMemory memory, long address) {
        memory.write(address, Arrays.copyOfRange(image, protectedPayloadOffset, image.length));
    }

    /**
     * Copies the protocol setup header into a zeroed boot-parameters page.
     *
     * @param bootParameters 4 KiB boot-parameters segment
     */
    public void copySetupHeader(java.lang.foreign.MemorySegment bootParameters) {
        int first = SETUP_SECTORS_OFFSET;
        byte[] header = Arrays.copyOfRange(image, first, setupHeaderEnd);
        bootParameters.asSlice(first, header.length).copyFrom(java.lang.foreign.MemorySegment.ofArray(header));
    }

    /**
     * Returns the Linux boot-protocol version declared by the image.
     *
     * @return Linux boot-protocol version
     */
    public int protocolVersion() {
        return protocolVersion;
    }

    /**
     * Returns the normalized real-mode setup-sector count.
     *
     * @return setup sector count after applying the protocol's zero-means-four rule
     */
    public int setupSectors() {
        return setupSectors;
    }

    /**
     * Returns the number of protected-mode kernel payload bytes.
     *
     * @return protected-mode payload size
     */
    public long protectedPayloadSize() {
        return image.length - (long) protectedPayloadOffset;
    }

    /**
     * Returns the highest physical address allowed for the initramfs.
     *
     * @return maximum address at which the kernel accepts an initrd
     */
    public long initrdAddressMax() {
        return initrdAddressMax;
    }

    /**
     * Returns the kernel-advertised command-line capacity.
     *
     * @return maximum command-line bytes advertised by the kernel
     */
    public long commandLineSize() {
        return commandLineSize;
    }

    /**
     * Returns the required alignment of a relocatable kernel payload.
     *
     * @return required physical kernel alignment
     */
    public long kernelAlignment() {
        return kernelAlignment;
    }

    /**
     * Reports whether the protected-mode payload supports relocation.
     *
     * @return whether the protected kernel may be relocated
     */
    public boolean relocatable() {
        return relocatable;
    }

    /**
     * Returns the temporary RAM footprint requested by the decompressor.
     *
     * @return RAM required by the kernel during decompression and initialization
     */
    public long initializationSize() {
        return initializationSize;
    }

    /**
     * Returns the unmodified boot-header load flags.
     *
     * @return original load flags supplied by the kernel image
     */
    public int loadFlags() {
        return loadFlags;
    }
}
