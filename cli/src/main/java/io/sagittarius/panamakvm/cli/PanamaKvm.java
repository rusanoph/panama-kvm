package io.sagittarius.panamakvm.cli;

import io.sagittarius.panamakvm.cli.options.CliOptions;
import io.sagittarius.panamakvm.cli.runtime.GuestRunner;
import io.sagittarius.panamakvm.core.ByteSize;
import io.sagittarius.panamakvm.platform.CpuArchitecture;
import io.sagittarius.panamakvm.platform.HostPlatform;
import io.sagittarius.panamakvm.virtualization.BackendAvailability;
import io.sagittarius.panamakvm.virtualization.VirtualMachine;
import io.sagittarius.panamakvm.virtualization.VirtualMachineConfiguration;
import io.sagittarius.panamakvm.virtualization.VirtualizationProvider;
import io.sagittarius.panamakvm.virtualization.VirtualizationProviders;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/** Command-line entry point for provider discovery and direct Linux boot. */
public final class PanamaKvm {
    private PanamaKvm() { }

    /**
     * Parses arguments, selects a service-loaded backend, and runs a guest.
     *
     * @param arguments command-line arguments
     * @throws Exception when provider creation, image loading, or execution fails
     */
    static void main(String[] arguments) throws Exception {
        CliOptions options = CliOptions.parse(arguments);
        if (options.help()) {
            printHelp();
            return;
        }

        Map<String, VirtualizationProvider> providers = VirtualizationProviders.discover();
        HostPlatform host = HostPlatform.current();
        if (options.listBackends()) {
            providers.values().stream()
                     .sorted(Comparator.comparing(VirtualizationProvider::id))
                     .forEach(provider -> printProvider(provider, host));
            return;
        }

        String selectedId = Optional.ofNullable(options.backend())
                .orElseGet(() -> options.kernel() != null ? "kvm" : "sim");
        VirtualizationProvider provider = Optional.ofNullable(providers.get(selectedId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown backend '" + selectedId
                        + "'; installed backends: " + providers.keySet()));
        BackendAvailability availability = provider.availability(host);
        if (!availability.available()) {
            throw new UnsupportedOperationException("Backend '" + selectedId + "' is unavailable: "
                    + availability.detail());
        }

        VirtualMachineConfiguration configuration = new VirtualMachineConfiguration(
                ByteSize.ofMiB(options.memoryMib()),
                1,
                CpuArchitecture.X86_64,
                options.labels()
        );
        try (VirtualMachine machine = provider.create(configuration)) {
            GuestRunner.run(machine, options);
        }
    }

    private static void printProvider(VirtualizationProvider provider, HostPlatform host) {
        BackendAvailability availability = provider.availability(host);
        String displayAvailability = availability.available() ? "available" : "unavailable";
        System.out.printf("%-8s %-11s %s%n", provider.id(), displayAvailability, availability.detail());
    }

    private static void printHelp() {
        System.out.println("""
                PanamaKVM - direct Linux boot with Java FFM and Linux KVM

                Usage:
                  panama-kvm --list-backends
                  panama-kvm [--backend sim]
                  panama-kvm --kernel <bzImage> [--initrd <initramfs.cpio.gz>]
                             [--memory-mib 256] [--cmdline <linux arguments>]
                             [--label key=value] [--non-interactive]

                A kernel path selects the kvm backend by default. The loader accepts modern
                x86 bzImage kernels from any distribution; it does not boot ISO/UEFI images.
                """);
    }
}
