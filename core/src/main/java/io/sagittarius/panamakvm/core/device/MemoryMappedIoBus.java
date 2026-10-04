package io.sagittarius.panamakvm.core.device;

import io.sagittarius.panamakvm.core.VmExit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Dispatches KVM MMIO exits to specifically mapped devices or a fallback. */
public final class MemoryMappedIoBus implements AutoCloseable {
    private final List<MemoryMappedDevice> devices;
    private final List<Mapping> mappings;
    private final MemoryMappedDevice fallback;

    /**
     * Creates a strict bus that rejects overlapping mappings and unmapped accesses.
     *
     * @param devices mapped devices
     */
    public MemoryMappedIoBus(List<? extends MemoryMappedDevice> devices) {
        this(devices, null);
    }

    /**
     * Creates a bus that rejects overlapping mappings and uses an optional
     * absent-device fallback for otherwise unmapped accesses.
     *
     * @param devices mapped devices
     * @param fallback fallback device, or {@code null}
     */
    public MemoryMappedIoBus(List<? extends MemoryMappedDevice> devices, MemoryMappedDevice fallback) {
        this.devices = List.copyOf(devices);
        this.mappings = collectMappings(this.devices);
        this.fallback = fallback;
    }

    /**
     * Dispatches one MMIO exit.
     *
     * @param exit MMIO exit
     */
    @SuppressWarnings("resource")
    public void handle(VmExit.Mmio exit) {
        MemoryMappedDevice device = mappings.stream()
                .filter(mapping -> mapping.range().contains(exit.address(), exit.data().byteSize()))
                .map(Mapping::device)
                .findFirst()
                .orElseGet(() -> {
                    if (fallback == null || !fallback.handles(exit.address(), exit.data().byteSize())) {
                        throw new IllegalStateException("No device handles MMIO range [0x"
                                + Long.toHexString(exit.address()) + ", 0x"
                                + Long.toHexString(exit.address() + exit.data().byteSize()) + ")");
                    }
                    return fallback;
                });
        if (exit.write()) {
            device.write(exit.address(), exit.data());
        } else {
            device.read(exit.address(), exit.data());
        }
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (MemoryMappedDevice device : devices.reversed()) {
            failure = close(device, failure);
        }
        if (fallback != null) {
            failure = close(fallback, failure);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException close(MemoryMappedDevice device, RuntimeException previous) {
        try {
            device.close();
            return previous;
        } catch (RuntimeException exception) {
            if (previous == null) {
                return exception;
            }
            previous.addSuppressed(exception);
            return previous;
        }
    }

    private static List<Mapping> collectMappings(List<MemoryMappedDevice> devices) {
        List<Mapping> mappings = new ArrayList<>();
        for (MemoryMappedDevice device : devices) {
            List<MemoryRange> ranges = List.copyOf(Objects.requireNonNull(
                    device.ranges(), "MMIO device ranges"));
            for (MemoryRange range : ranges) {
                mappings.add(new Mapping(Objects.requireNonNull(range, "MMIO range"), device));
            }
        }
        mappings.sort(Comparator.comparingLong(mapping -> mapping.range().start()));
        ensureNoOverlap(mappings);
        return List.copyOf(mappings);
    }

    private static void ensureNoOverlap(List<Mapping> mappings) {
        for (int index = 1; index < mappings.size(); index++) {
            MemoryRange previous = mappings.get(index - 1).range();
            MemoryRange current = mappings.get(index).range();
            if (previous.overlaps(current)) {
                throw new IllegalArgumentException("MMIO device ranges overlap: "
                        + previous + " and " + current);
            }
        }
    }

    private record Mapping(MemoryRange range, MemoryMappedDevice device) { }
}
