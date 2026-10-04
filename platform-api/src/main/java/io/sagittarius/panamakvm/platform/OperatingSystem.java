package io.sagittarius.panamakvm.platform;

/** Host operating-system families understood by PanamaKVM providers. */
public enum OperatingSystem {
    /** GNU/Linux and compatible Linux userlands. */
    LINUX,
    /** Microsoft Windows NT family. */
    WINDOWS,
    /** Apple macOS. */
    MACOS,
    /** An operating system not recognized by this version. */
    OTHER
}
