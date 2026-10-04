package io.sagittarius.panamakvm.sim;

import io.sagittarius.panamakvm.core.GuestMemory;
import io.sagittarius.panamakvm.core.VmExit;
import io.sagittarius.panamakvm.virtualization.VirtualMachine;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.Arena;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Objects;
import java.util.Queue;

/**
 * Deterministic test double that replays explicit exits. It does not emulate a
 * CPU, {@code /dev/kvm}, memory mapping, or hardware virtualization.
 */
public final class ScriptedVirtualMachine implements VirtualMachine {
    private final Arena arena;
    private final MemorySegment storage;
    private final GuestMemory memory;
    private final Queue<VmExit> exits;
    private boolean closed;

    /**
     * Creates a scripted machine.
     *
     * @param memoryBytes guest-memory size
     * @param exits ordered exit script
     */
    public ScriptedVirtualMachine(long memoryBytes, Collection<? extends VmExit> exits) {
        if (memoryBytes <= 0) {
            throw new IllegalArgumentException("Memory size must be positive");
        }
        this.arena = Arena.ofShared();
        this.storage = arena.allocate(memoryBytes, 1);
        MemorySegment segment = storage;
        this.memory = new GuestMemory() {
            @Override
            public long byteSize() {
                return storage.byteSize();
            }

            @Override
            public MemorySegment slice(long guestPhysicalAddress, long byteSize) {
                return segment.asSlice(guestPhysicalAddress, byteSize);
            }
        };
        this.exits = new ArrayDeque<>(Objects.requireNonNull(exits, "exits"));
    }

    @Override
    public GuestMemory memory() {
        ensureOpen();
        return memory;
    }

    @Override
    public VmExit runUntilExit() {
        ensureOpen();
        VmExit exit = exits.poll();
        if (exit == null) {
            throw new IllegalStateException("Scripted virtual machine has no exit left");
        }
        return exit;
    }

    @Override
    public void pulseInterrupt(int interrupt) {
        ensureOpen();
        if (interrupt < 0) {
            throw new IllegalArgumentException("Interrupt must not be negative");
        }
    }

    @Override
    public void close() {
        closed = true;
        exits.clear();
        arena.close();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Scripted virtual machine is closed");
        }
    }
}
