package io.sagittarius.panamakvm.core.device;

import io.sagittarius.panamakvm.core.VmExit;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

/**
 * Dispatches KVM port-I/O exits to non-overlapping emulated devices.
 */
public final class IoPortBus implements AutoCloseable {
    private final List<IoPortDevice> devices;
    private final List<Mapping> mappings;
    private final IoPortDevice fallback;

    /**
     * Creates a bus and rejects overlapping device ranges eagerly.
     *
     * @param devices attached devices
     */
    public IoPortBus(List<? extends IoPortDevice> devices) {
        this(devices, null);
    }

    /**
     * Creates a bus with a fallback used for otherwise unmapped ports.
     * The fallback is intentionally excluded from overlap checks.
     *
     * @param devices attached specifically addressed devices
     * @param fallback fallback device, or {@code null} to fail on unmapped I/O
     */
    public IoPortBus(List<? extends IoPortDevice> devices, IoPortDevice fallback) {
        this.devices = List.copyOf(devices);
        this.mappings = collectMappings(this.devices);
        this.fallback = fallback;
    }

    /**
     * Handles all elements in an I/O exit.
     *
     * @param exit port-I/O exit
     */
    @SuppressWarnings("resource")
    public void handle(VmExit.Io exit) {
        IoPortDevice device = mappings.stream()
                .filter(mapping -> mapping.range().contains(exit.port()))
                .map(Mapping::device)
                .findFirst()
                .orElseGet(() -> {
                    if (fallback == null || !fallback.handles(exit.port())) {
                        throw new IllegalStateException(
                                "No device handles I/O port 0x" + Integer.toHexString(exit.port()));
                    }
                    return fallback;
                });

        for (long index = 0; index < exit.count(); index++) {
            long offset = Math.multiplyExact(index, exit.elementSize());
            if (exit.direction() == VmExit.IoDirection.OUT) {
                device.write(exit.port(), exit.elementSize(), readLittleEndian(exit.data(), offset, exit.elementSize()));
            } else {
                writeLittleEndian(exit.data(), offset, exit.elementSize(), device.read(exit.port(), exit.elementSize()));
            }
        }
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (IoPortDevice device : devices.reversed()) {
            try {
                device.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (fallback != null) {
            try {
                fallback.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static long readLittleEndian(MemorySegment data, long offset, int size) {
        long value = 0;
        for (int byteIndex = 0; byteIndex < size; byteIndex++) {
            value |= (long) Byte.toUnsignedInt(data.get(JAVA_BYTE, offset + byteIndex)) << (Byte.SIZE * byteIndex);
        }
        return value;
    }

    private static void writeLittleEndian(MemorySegment data, long offset, int size, long value) {
        for (int byteIndex = 0; byteIndex < size; byteIndex++) {
            data.set(JAVA_BYTE, offset + byteIndex, (byte) (value >>> (Byte.SIZE * byteIndex)));
        }
    }

    private static List<Mapping> collectMappings(List<IoPortDevice> devices) {
        List<Mapping> mappings = new ArrayList<>();
        for (IoPortDevice device : devices) {
            List<IoPortRange> ranges = List.copyOf(Objects.requireNonNull(device.ranges(), "I/O-port device ranges"));
            for (IoPortRange range : ranges) {
                mappings.add(new Mapping(Objects.requireNonNull(range, "I/O-port range"), device));
            }
        }
        mappings.sort(Comparator.comparingInt(mapping -> mapping.range().start()));
        ensureNoOverlap(mappings);
        return List.copyOf(mappings);
    }

    private static void ensureNoOverlap(List<Mapping> mappings) {
        for (int index = 1; index < mappings.size(); index++) {
            IoPortRange previous = mappings.get(index - 1).range();
            IoPortRange current = mappings.get(index).range();
            if (previous.overlaps(current)) {
                throw new IllegalArgumentException("I/O-port device ranges overlap: "
                        + previous + " and " + current);
            }
        }
    }

    private record Mapping(IoPortRange range, IoPortDevice device) { }
}
