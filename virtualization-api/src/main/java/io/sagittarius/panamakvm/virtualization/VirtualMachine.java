package io.sagittarius.panamakvm.virtualization;

import io.sagittarius.panamakvm.core.GuestMemory;
import io.sagittarius.panamakvm.core.VmExit;

/** A running or runnable virtual machine created by a backend provider. */
public interface VirtualMachine extends AutoCloseable {
    /**
     * Returns the guest-physical memory accessor.
     *
     * @return guest memory
     */
    GuestMemory memory();

    /**
     * Runs the bootstrap vCPU until control returns to userspace.
     *
     * @return typed exit
     */
    VmExit runUntilExit();

    /**
     * Pulses a legacy interrupt input (assert followed by deassert).
     *
     * @param interrupt legacy IRQ/GSI number
     */
    void pulseInterrupt(int interrupt);

    /**
     * Sets a legacy interrupt input level.
     *
     * <p>Backends without level-triggered interrupt support preserve the old
     * pulse behavior when a line becomes asserted.</p>
     *
     * @param interrupt legacy IRQ/GSI number
     * @param asserted whether the line is asserted
     */
    default void setInterruptLine(int interrupt, boolean asserted) {
        if (asserted) {
            pulseInterrupt(interrupt);
        }
    }

    /** Releases native and backend resources. */
    @Override
    void close();
}
