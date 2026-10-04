package io.sagittarius.panamakvm.virtualization;

/** Isolation mechanisms supported by the common provider discovery layer. */
public enum IsolationKind {
    /** Hardware-assisted or emulated virtual machine with a guest kernel. */
    VIRTUAL_MACHINE,
    /** Host-kernel container such as OpenVZ, namespaces, or Windows containers. */
    CONTAINER
}
