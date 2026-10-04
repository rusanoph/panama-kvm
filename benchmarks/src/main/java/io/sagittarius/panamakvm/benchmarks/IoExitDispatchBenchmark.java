package io.sagittarius.panamakvm.benchmarks;

import io.sagittarius.panamakvm.core.VmExit;
import io.sagittarius.panamakvm.core.device.IoPortBus;
import io.sagittarius.panamakvm.core.device.Uart16550;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.OutputStream;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Measures the userspace I/O-exit dispatch hot path without requiring KVM. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Fork(value = 5)
@Measurement(iterations = 8, time = 1)
@State(Scope.Thread)
public class IoExitDispatchBenchmark {
    private IoPortBus bus;
    private VmExit.Io exit;

    /** Creates an instance managed by the JMH harness. */
    public IoExitDispatchBenchmark() { }

    /** Initializes one stable UART exit outside measured methods. */
    @Setup(Level.Trial)
    public void setUp() {
        Uart16550 uart = new Uart16550(OutputStream.nullOutputStream(), ignored -> { });
        bus = new IoPortBus(List.of(uart));
        exit = new VmExit.Io(
                VmExit.IoDirection.OUT, Uart16550.COM1_BASE,
                1, 1,
                MemorySegment.ofArray(new byte[]{'x'})
        );
    }

    /** Dispatches one single-byte UART output VM exit. */
    @Benchmark
    public void dispatchUartOutput() {
        bus.handle(exit);
    }
}
