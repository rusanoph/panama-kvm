package io.sagittarius.panamakvm.platform;

/** Host or guest instruction-set architectures known to PanamaKVM. */
public enum CpuArchitecture {
    /** AMD64 / Intel 64. */
    X86_64,
    /** 64-bit Arm architecture. */
    AARCH64,
    /** An architecture not recognized by this version. */
    OTHER
}
