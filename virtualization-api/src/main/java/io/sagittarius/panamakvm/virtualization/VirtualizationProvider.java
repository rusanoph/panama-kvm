package io.sagittarius.panamakvm.virtualization;

/** Service-provider interface for virtual-machine backends. */
public interface VirtualizationProvider extends IsolationBackendProvider {
    /**
     * Creates a stopped, configurable virtual machine.
     *
     * @param configuration resource and architecture request
     * @return new virtual machine
     */
    VirtualMachine create(VirtualMachineConfiguration configuration);

    @Override
    default IsolationKind kind() {
        return IsolationKind.VIRTUAL_MACHINE;
    }
}
