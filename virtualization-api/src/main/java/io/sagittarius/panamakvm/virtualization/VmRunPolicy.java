package io.sagittarius.panamakvm.virtualization;

import java.time.Duration;
import java.util.Objects;

/**
 * Controls how the generic run loop interprets guest halt exits.
 *
 * @param stopOnHalt whether the first halt completes the run
 * @param haltBackoff host backoff before resuming a non-terminal halt
 */
public record VmRunPolicy(boolean stopOnHalt, Duration haltBackoff) {
    /** Validates a run policy. */
    public VmRunPolicy {
        Objects.requireNonNull(haltBackoff, "haltBackoff");
        if (haltBackoff.isNegative()) {
            throw new IllegalArgumentException("Halt backoff must not be negative");
        }
    }

    /**
     * Policy for finite instruction snippets where {@code HLT} means success.
     *
     * @return terminal-halt policy
     */
    public static VmRunPolicy finiteGuest() {
        return new VmRunPolicy(true, Duration.ZERO);
    }

    /**
     * Policy for operating systems where {@code HLT} is an idle instruction.
     *
     * @return resumable-halt policy
     */
    public static VmRunPolicy operatingSystem() {
        return new VmRunPolicy(false, Duration.ofMillis(1));
    }
}
