package io.sagittarius.panamakvm.virtualization;

import io.sagittarius.panamakvm.core.ByteSize;
import io.sagittarius.panamakvm.platform.CpuArchitecture;

import java.util.Objects;
import java.util.Map;

/**
 * Backend-neutral virtual-machine resources.
 *
 * @param memory guest RAM size
 * @param virtualCpuCount number of virtual CPUs
 * @param guestArchitecture guest instruction-set architecture
 * @param labels immutable experiment/ownership metadata passed through to drivers
 */
public record VirtualMachineConfiguration(
        ByteSize memory,
        int virtualCpuCount,
        CpuArchitecture guestArchitecture,
        Map<String, String> labels
) {
    /** Validates the resource request. */
    public VirtualMachineConfiguration {
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(guestArchitecture, "guestArchitecture");
        labels = Map.copyOf(Objects.requireNonNull(labels, "labels"));
        if (memory.bytes() == 0) {
            throw new IllegalArgumentException("Guest memory must not be empty");
        }
        if (virtualCpuCount <= 0) {
            throw new IllegalArgumentException("Virtual CPU count must be positive");
        }
    }

    /**
     * Creates a single-vCPU x86-64 configuration.
     *
     * @param memory guest RAM
     * @return x86-64 configuration
     */
    public static VirtualMachineConfiguration x86_64(ByteSize memory) {
        return new VirtualMachineConfiguration(memory, 1, CpuArchitecture.X86_64, Map.of());
    }
}
