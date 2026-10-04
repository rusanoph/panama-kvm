package io.sagittarius.panamakvm.benchmarks;

import io.sagittarius.panamakvm.kvm.boot.LinuxKernelImage;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

/** Measures validation and defensive copying of a representative bzImage. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Fork(value = 5)
@Measurement(iterations = 5, time = 1)
@State(Scope.Thread)
public class LinuxKernelParserBenchmark {
    private byte[] image;

    /** Creates an instance managed by the JMH harness. */
    public LinuxKernelParserBenchmark() { }

    /** Creates a synthetic 16 MiB modern bzImage-shaped input. */
    @Setup
    public void setUp() {
        image = new byte[16 << 20];
        image[LinuxKernelImage.SETUP_SECTORS_OFFSET] = 4;
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
        header.putInt(LinuxKernelImage.INITIALIZATION_SIZE_OFFSET, 32 << 20);
    }

    /**
     * Parses and validates the synthetic image.
     *
     * @return validated image model
     */
    @Benchmark
    public LinuxKernelImage parseImage() {
        return LinuxKernelImage.parse(image);
    }
}
