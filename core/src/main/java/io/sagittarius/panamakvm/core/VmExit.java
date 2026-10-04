package io.sagittarius.panamakvm.core;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

/**
 * A typed reason why guest execution returned to the host VMM.
 */
public sealed interface VmExit
        permits VmExit.Io, VmExit.Mmio, VmExit.Halt, VmExit.Shutdown, VmExit.SystemEvent, VmExit.Unknown {

    /** Direction of an x86 port-I/O operation from the guest's perspective. */
    enum IoDirection {
        /** Guest reads from a host-emulated port. */
        IN,
        /** Guest writes to a host-emulated port. */
        OUT
    }

    /** Backend-neutral classification of a guest lifecycle system event. */
    enum SystemEventKind {
        /** Guest requested an orderly shutdown. */
        SHUTDOWN,
        /** Guest requested a platform reset. */
        RESET,
        /** Guest reported an unrecoverable crash. */
        CRASH,
        /** Backend supplied a lifecycle event unknown to the common API. */
        UNKNOWN
    }

    /**
     * A memory-mapped I/O exit. Read handlers write a response into
     * {@code data}; write handlers consume its guest-provided bytes.
     *
     * @param address guest-physical MMIO address
     * @param write whether the guest is writing
     * @param data one to eight transfer bytes
     */
    record Mmio(long address, boolean write, MemorySegment data) implements VmExit {
        /** Validates KVM's fixed-size MMIO payload contract. */
        public Mmio {
            Objects.requireNonNull(data, "data");
            if (address < 0) {
                throw new IllegalArgumentException("MMIO address must not be negative: " + address);
            }
            if (data.byteSize() < 1 || data.byteSize() > Long.BYTES) {
                throw new IllegalArgumentException("KVM MMIO transfer size must be in [1, 8]");
            }
        }
    }

    /**
     * A port-I/O exit. For {@link IoDirection#IN}, the handler writes response
     * values into {@code data}; for {@link IoDirection#OUT}, it reads them.
     *
     * @param direction I/O direction
     * @param port unsigned 16-bit I/O port
     * @param elementSize x86 element width: 1, 2, or 4 bytes
     * @param count number of consecutive elements
     * @param data mapped KVM run-buffer payload
     */
    record Io(IoDirection direction, int port, int elementSize, long count, MemorySegment data)
            implements VmExit {
        /** Validates the exit metadata against the supplied data segment. */
        public Io {
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(data, "data");
            if (port < 0 || port > 0xffff) {
                throw new IllegalArgumentException("I/O port outside unsigned 16-bit range: " + port);
            }
            if (elementSize != 1 && elementSize != 2 && elementSize != 4) {
                throw new IllegalArgumentException("Unsupported I/O element size: " + elementSize);
            }
            if (count < 0) {
                throw new IllegalArgumentException("I/O count must not be negative: " + count);
            }
            if (Math.multiplyExact(elementSize, count) != data.byteSize()) {
                throw new IllegalArgumentException("I/O metadata does not match data segment size");
            }
        }

        /**
         * Returns the full transfer size.
         *
         * @return byte count
         */
        public long byteCount() {
            return data.byteSize();
        }
    }

    /**
     * Guest executed a halt instruction.
     *
     * @param interruptsEnabled whether maskable interrupts can wake the vCPU
     */
    record Halt(boolean interruptsEnabled) implements VmExit {
        /** Creates a terminal halt with interrupts disabled. */
        public Halt() {
            this(false);
        }
    }

    /** Guest requested shutdown or encountered a KVM shutdown condition. */
    record Shutdown() implements VmExit { }

    /**
     * A backend lifecycle event such as shutdown, reset, or crash.
     *
     * @param kind portable event classification
     * @param backendCode original backend-specific event code
     * @param flags backend-specific event flags
     */
    record SystemEvent(SystemEventKind kind, int backendCode, long flags) implements VmExit {
        /** Validates that the portable event classification is present. */
        public SystemEvent {
            Objects.requireNonNull(kind, "kind");
        }
    }

    /**
     * An exit reason not understood by the active backend.
     *
     * @param reason backend-specific numeric reason
     */
    record Unknown(int reason) implements VmExit { }
}
