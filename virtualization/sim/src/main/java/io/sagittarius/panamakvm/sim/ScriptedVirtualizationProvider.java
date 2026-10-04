package io.sagittarius.panamakvm.sim;

import com.google.auto.service.AutoService;
import io.sagittarius.panamakvm.core.VmExit;
import io.sagittarius.panamakvm.platform.HostPlatform;
import io.sagittarius.panamakvm.virtualization.BackendAvailability;
import io.sagittarius.panamakvm.virtualization.VirtualMachine;
import io.sagittarius.panamakvm.virtualization.VirtualMachineConfiguration;
import io.sagittarius.panamakvm.virtualization.VirtualizationProvider;

import java.util.List;

/** Provider for the portable deterministic scripted backend. */
@AutoService(VirtualizationProvider.class)
public final class ScriptedVirtualizationProvider implements VirtualizationProvider {
    /** Stable backend identifier accepted by the CLI. */
    public static final String ID = "sim";

    /** Creates the stateless simulated-backend provider. */
    public ScriptedVirtualizationProvider() { }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public BackendAvailability availability(HostPlatform host) {
        return BackendAvailability.available("portable deterministic test double");
    }

    @Override
    public VirtualMachine create(VirtualMachineConfiguration configuration) {
        return new ScriptedVirtualMachine(configuration.memory().bytes(), List.of(new VmExit.Halt()));
    }
}
