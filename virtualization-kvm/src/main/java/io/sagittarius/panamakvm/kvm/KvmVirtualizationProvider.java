package io.sagittarius.panamakvm.kvm;

import com.google.auto.service.AutoService;
import io.sagittarius.panamakvm.platform.CpuArchitecture;
import io.sagittarius.panamakvm.platform.HostPlatform;
import io.sagittarius.panamakvm.platform.OperatingSystem;
import io.sagittarius.panamakvm.virtualization.BackendAvailability;
import io.sagittarius.panamakvm.virtualization.VirtualMachine;
import io.sagittarius.panamakvm.virtualization.VirtualMachineConfiguration;
import io.sagittarius.panamakvm.virtualization.VirtualizationProvider;

import java.nio.file.Files;
import java.nio.file.Path;

/** Linux x86-64 hardware-virtualization provider backed by {@code /dev/kvm}. */
@AutoService(VirtualizationProvider.class)
public final class KvmVirtualizationProvider implements VirtualizationProvider {
    /** Stable backend identifier accepted by the CLI. */
    public static final String ID = "kvm";
    /** Conventional Linux KVM device node. */
    public static final Path DEVICE = Path.of("/dev/kvm");

    /** Creates the stateless Linux KVM provider. */
    public KvmVirtualizationProvider() { }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public BackendAvailability availability(HostPlatform host) {
        if (host.operatingSystem() != OperatingSystem.LINUX) {
            return BackendAvailability.unavailable("KVM requires a Linux host; detected " + host.displayName());
        }
        if (host.architecture() != CpuArchitecture.X86_64) {
            return BackendAvailability.unavailable("This backend currently implements x86-64 KVM only");
        }
        if (!Files.isReadable(DEVICE) || !Files.isWritable(DEVICE)) {
            return BackendAvailability.unavailable("/dev/kvm is not readable and writable by this process");
        }
        return BackendAvailability.available("Linux x86-64 with accessible /dev/kvm");
    }

    @Override
    public VirtualMachine create(VirtualMachineConfiguration configuration) {
        if (configuration.guestArchitecture() != CpuArchitecture.X86_64) {
            throw new UnsupportedOperationException("x86-64 KVM cannot virtualize "
                    + configuration.guestArchitecture());
        }
        if (configuration.virtualCpuCount() != 1) {
            throw new UnsupportedOperationException("Only one vCPU is implemented in this milestone");
        }
        return KvmVirtualMachine.create(configuration);
    }
}
