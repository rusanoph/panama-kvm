package io.sagittarius.panamakvm.virtualization;

import io.sagittarius.panamakvm.core.VmExit;
import io.sagittarius.panamakvm.core.device.IoPortBus;
import io.sagittarius.panamakvm.core.device.MemoryMappedIoBus;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

/** Backend-neutral vCPU run loop and typed VM-exit dispatcher. */
public final class VmRunner {
    private final VirtualMachine machine;
    private final IoPortBus ioBus;
    private final MemoryMappedIoBus mmioBus;
    private final VmRunPolicy policy;

    /**
     * Creates a run loop.
     *
     * @param machine target machine
     * @param ioBus I/O device bus
     * @param policy halt behavior
     */
    public VmRunner(VirtualMachine machine, IoPortBus ioBus, VmRunPolicy policy) {
        this(machine, ioBus, new MemoryMappedIoBus(List.of()), policy);
    }

    /**
     * Creates a run loop with both port-I/O and MMIO device buses.
     *
     * @param machine target machine
     * @param ioBus port-I/O device bus
     * @param mmioBus memory-mapped device bus
     * @param policy halt behavior
     */
    public VmRunner(
            VirtualMachine machine,
            IoPortBus ioBus,
            MemoryMappedIoBus mmioBus,
            VmRunPolicy policy
    ) {
        this.machine = Objects.requireNonNull(machine, "machine");
        this.ioBus = Objects.requireNonNull(ioBus, "ioBus");
        this.mmioBus = Objects.requireNonNull(mmioBus, "mmioBus");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * Runs until a terminal VM exit occurs.
     *
     * @return terminal run result
     */
    public VmRunResult run() {
        while (true) {
            switch (machine.runUntilExit()) {
                case VmExit.Io io -> ioBus.handle(io);
                case VmExit.Mmio mmio -> mmioBus.handle(mmio);
                case VmExit.Halt halt -> {
                    if (policy.stopOnHalt() || !halt.interruptsEnabled()) {
                        return VmRunResult.HALTED;
                    }
                    LockSupport.parkNanos(policy.haltBackoff().toNanos());
                }
                case VmExit.Shutdown ignored -> {
                    return VmRunResult.SHUTDOWN;
                }
                case VmExit.SystemEvent event -> {
                    return switch (event.kind()) {
                        case SHUTDOWN -> VmRunResult.SYSTEM_SHUTDOWN;
                        case RESET -> VmRunResult.SYSTEM_RESET;
                        case CRASH -> VmRunResult.SYSTEM_CRASH;
                        case UNKNOWN -> throw new IllegalStateException(
                                "Unknown system event code: " + event.backendCode());
                    };
                }
                case VmExit.Unknown unknown -> throw new IllegalStateException(
                        "Unhandled backend exit reason: " + unknown.reason());
            }
        }
    }
}
