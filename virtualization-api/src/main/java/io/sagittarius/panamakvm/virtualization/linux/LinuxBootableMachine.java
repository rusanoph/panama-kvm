package io.sagittarius.panamakvm.virtualization.linux;

import io.sagittarius.panamakvm.virtualization.VirtualMachine;

import java.io.IOException;

/** A virtual machine capable of booting Linux directly without BIOS or UEFI. */
public interface LinuxBootableMachine extends VirtualMachine {
    /**
     * Loads and configures the machine for the Linux x86 boot protocol.
     * This method may be called exactly once before the first run.
     *
     * @param boot boot inputs
     * @throws IOException when an image cannot be read
     */
    void bootLinux(LinuxBootSpec boot) throws IOException;
}
