package io.sagittarius.panamakvm.virtualization;

import java.util.Objects;

/**
 * Result of probing whether an isolation backend can run on a host.
 *
 * @param available whether creation may be attempted
 * @param detail human-readable evidence or rejection reason
 */
public record BackendAvailability(boolean available, String detail) {
    /** Validates a probe result. */
    public BackendAvailability {
        Objects.requireNonNull(detail, "detail");
    }

    /**
     * Creates a successful probe result.
     *
     * @param detail supporting detail
     * @return available result
     */
    public static BackendAvailability available(String detail) {
        return new BackendAvailability(true, detail);
    }

    /**
     * Creates a failed probe result.
     *
     * @param reason rejection reason
     * @return unavailable result
     */
    public static BackendAvailability unavailable(String reason) {
        return new BackendAvailability(false, reason);
    }
}
