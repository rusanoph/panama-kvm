package io.sagittarius.panamakvm.kvm.boot;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LinuxKernelImageTest {
    @Test
    void parsesModernBzImageHeader() {
        LinuxKernelImage image = LinuxKernelImage.parse(kernelImage());
        assertEquals(0x020f, image.protocolVersion());
        assertEquals(1, image.setupSectors());
        assertEquals(3072, image.protectedPayloadSize());
        assertEquals(2048, image.commandLineSize());
        assertEquals(2L << 20, image.initializationSize());
    }

    @Test
    void rejectsImageWithoutLinuxSignature() {
        byte[] bytes = kernelImage();
        bytes[LinuxKernelImage.HEADER_MAGIC_OFFSET] = 0;
        assertThrows(IllegalArgumentException.class, () -> LinuxKernelImage.parse(bytes));
    }

    static byte[] kernelImage() {
        byte[] image = new byte[4096];
        image[LinuxKernelImage.SETUP_SECTORS_OFFSET] = 1;
        image[0x201] = 0x66;
        image[LinuxKernelImage.LOAD_FLAGS_OFFSET] = LinuxKernelImage.LOADED_HIGH;
        image[LinuxKernelImage.RELOCATABLE_OFFSET] = 1;

        ByteBuffer header = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort(LinuxKernelImage.BOOT_FLAG_OFFSET, (short) 0xaa55);
        header.putInt(LinuxKernelImage.HEADER_MAGIC_OFFSET, LinuxKernelImage.HEADER_MAGIC);
        header.putShort(LinuxKernelImage.PROTOCOL_VERSION_OFFSET, (short) 0x020f);

        header.putInt(LinuxKernelImage.INITRD_ADDRESS_MAX_OFFSET, 0x37ff_ffff);
        header.putInt(LinuxKernelImage.KERNEL_ALIGNMENT_OFFSET, 2 << 20);

        header.putInt(LinuxKernelImage.COMMAND_LINE_SIZE_OFFSET, 2048);
        header.putInt(LinuxKernelImage.INITIALIZATION_SIZE_OFFSET, 2 << 20);

        return image;
    }
}
