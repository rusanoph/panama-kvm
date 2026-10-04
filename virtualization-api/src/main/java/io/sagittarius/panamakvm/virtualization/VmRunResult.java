package io.sagittarius.panamakvm.virtualization;

/** Terminal result returned by the generic virtual-machine run loop. */
public enum VmRunResult {
    /** A finite guest halted and the active policy treats halt as terminal. */
    HALTED,
    /** KVM reported a shutdown condition. */
    SHUTDOWN,
    /** Guest emitted a KVM system shutdown event. */
    SYSTEM_SHUTDOWN,
    /** Guest emitted a KVM reset event. */
    SYSTEM_RESET,
    /** Guest emitted a KVM crash event. */
    SYSTEM_CRASH
}
