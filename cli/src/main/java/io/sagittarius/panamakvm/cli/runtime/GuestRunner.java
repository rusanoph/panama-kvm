package io.sagittarius.panamakvm.cli.runtime;

import io.sagittarius.panamakvm.cli.options.CliOptions;
import io.sagittarius.panamakvm.cli.terminal.ConsoleSession;
import io.sagittarius.panamakvm.core.device.AbsentIoPortDevice;
import io.sagittarius.panamakvm.core.device.AbsentMemoryMappedDevice;
import io.sagittarius.panamakvm.core.device.CmosRtc;
import io.sagittarius.panamakvm.core.device.IoPortBus;
import io.sagittarius.panamakvm.core.device.MemoryMappedIoBus;
import io.sagittarius.panamakvm.core.device.Uart16550;
import io.sagittarius.panamakvm.virtualization.VirtualMachine;
import io.sagittarius.panamakvm.virtualization.VmRunPolicy;
import io.sagittarius.panamakvm.virtualization.VmRunResult;
import io.sagittarius.panamakvm.virtualization.VmRunner;
import io.sagittarius.panamakvm.virtualization.linux.LinuxBootSpec;
import io.sagittarius.panamakvm.virtualization.linux.LinuxBootableMachine;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Boots and runs either a simulated guest or a direct Linux guest. */
public final class GuestRunner {
    private GuestRunner() { }

    /**
     * Runs a guest selected by the parsed CLI options.
     *
     * @param machine selected virtualization backend's machine
     * @param options parsed command-line options
     * @throws IOException when a Linux image cannot be read
     */
    public static void run(VirtualMachine machine, CliOptions options) throws IOException {
        if (options.kernel() == null) {
            runSimulated(machine);
        } else {
            runLinux(machine, options);
        }
    }

    private static void runSimulated(VirtualMachine machine) {
        try (IoPortBus bus = new IoPortBus(List.of())) {
            VmRunResult result = new VmRunner(machine, bus, VmRunPolicy.finiteGuest()).run();
            System.out.println("Scripted guest completed: " + result);
        }
    }

    private static void runLinux(VirtualMachine machine, CliOptions options) throws IOException {
        if (!(machine instanceof LinuxBootableMachine linuxMachine)) {
            throw new UnsupportedOperationException("Selected backend cannot boot a Linux kernel directly");
        }
        LinuxBootSpec boot = new LinuxBootSpec(
                options.kernel(),
                Optional.ofNullable(options.initrd()),
                options.commandLine()
        );
        linuxMachine.bootLinux(boot);

        Uart16550 uart = new Uart16550(System.out, machine::pulseInterrupt, machine::setInterruptLine);
        try (ConsoleSession _ = ConsoleSession.open(uart, options.interactive());
             IoPortBus bus = new IoPortBus(List.of(uart, new CmosRtc()), new AbsentIoPortDevice());
             MemoryMappedIoBus mmioBus = new MemoryMappedIoBus(List.of(), new AbsentMemoryMappedDevice())
        ) {
            VmRunResult result = new VmRunner(machine, bus, mmioBus, VmRunPolicy.operatingSystem()).run();
            System.out.println(System.lineSeparator() + "Linux guest completed: " + result);
        }
    }
}
