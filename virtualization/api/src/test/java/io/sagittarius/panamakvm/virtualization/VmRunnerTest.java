package io.sagittarius.panamakvm.virtualization;

import io.sagittarius.panamakvm.core.GuestMemory;
import io.sagittarius.panamakvm.core.VmExit;
import io.sagittarius.panamakvm.core.device.IoPortBus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VmRunnerTest {
    @Test
    void finiteGuestStopsOnHalt() {
        StubMachine machine = new StubMachine(new VmExit.Halt());
        VmRunResult result = new VmRunner(machine, new IoPortBus(List.of()),
                VmRunPolicy.finiteGuest()).run();
        assertEquals(VmRunResult.HALTED, result);
    }

    @Test
    void operatingSystemResumesHaltUntilShutdown() {
        StubMachine machine = new StubMachine(new VmExit.Halt(true), new VmExit.Shutdown());
        VmRunResult result = new VmRunner(machine, new IoPortBus(List.of()),
                new VmRunPolicy(false, Duration.ZERO)).run();
        assertEquals(VmRunResult.SHUTDOWN, result);
    }

    @Test
    void mapsPortableSystemEventWithoutBackendConstants() {
        StubMachine machine = new StubMachine(new VmExit.SystemEvent(
                VmExit.SystemEventKind.RESET, 2, 0));
        VmRunResult result = new VmRunner(machine, new IoPortBus(List.of()),
                VmRunPolicy.finiteGuest()).run();
        assertEquals(VmRunResult.SYSTEM_RESET, result);
    }

    private static final class StubMachine implements VirtualMachine {
        private final Queue<VmExit> exits = new ArrayDeque<>();

        private StubMachine(VmExit... exits) {
            this.exits.addAll(List.of(exits));
        }

        @Override
        public GuestMemory memory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public VmExit runUntilExit() {
            return exits.remove();
        }

        @Override
        public void pulseInterrupt(int interrupt) { }

        @Override
        public void close() { }
    }
}
