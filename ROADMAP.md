# Roadmap

This roadmap describes direction rather than release promises. Items move to
"implemented" only when the repository contains code and proportionate tests;
article drafts and design notes are not completion evidence.

## Implemented baseline

- Java 25 multi-module build with reproducible, licensed JARs;
- Host-OS and virtualization provider SPIs with generated ServiceLoader metadata;
- Deterministic simulator for portable development;
- Linux x86-64 KVM access through FFM without project JNI glue;
- One-vCPU VM creation, contiguous guest RAM, CPUID, IRQ chip, and PIT;
- Modern x86 `bzImage` validation and direct boot;
- Linux `boot_params`, E820, command line, kernel, and initramfs placement;
- Typed PIO, MMIO, halt, shutdown, and system-event exits;
- Overlap-safe PIO/MMIO buses with explicit absent-device behavior;
- Minimal 16550A UART, serial input/output, level receive IRQ, and CMOS RTC;
- Finite smoke guest and interactive BusyBox guest from one pinned Alpine kernel profile;
- Host TTY ownership, prompt flushing, cursor-response filtering, and guest terminal control forwarding;
- Portable unit tests, opt-in real-KVM smoke test, JaCoCo, Javadoc, and JMH.

## Next: harden the current vertical slice

Before expanding the virtual hardware surface, make the existing boot and
console path difficult to regress:

1. Add an automated Linux PTY/KVM test for immediate prompt display, command
   echo, terminal controls (e.g., Ctrl+C) of a guest foreground process, subsequent
   shell usability, and clean `exit`.
2. Add boundary tests for Linux boot layout and `kvm_run` decoding.
3. Expand UART receive-queue and IRQ transition tests.
4. Add CLI, simulator, provider-discovery, and terminal restoration tests.
5. Run guest construction and real-KVM tests in a dedicated environment with
   guaranteed `/dev/kvm` access.
6. Define lifecycle semantics for halt, poweroff, reset, reboot, cancellation,
   and host-initiated stop.
7. Add structured diagnostics for VM creation, boot layout, exits, and devices
   without turning tracing into an always-on hot-path cost.

## Milestone: minimal persistent microVM

1. Implement a small `virtio-mmio` transport and feature negotiation.
2. Implement and fuzz-test split virtqueues and descriptor validation.
3. Add a read-only block backend before writable storage.
4. Add writable raw storage with explicit flush and durability semantics.
5. Boot a documented Linux root filesystem from the virtual block device.

Completion means a restart can preserve a file in the guest filesystem and
corrupt descriptors cannot escape guest-memory bounds.

## Milestone: networked guest

1. Add a virtio network device over the same tested transport.
2. Add a narrow Linux host adapter, initially TAP-based.
3. Define explicit MAC, MTU, address, routing, and cleanup behavior.
4. Test packet bounds, queue exhaustion, cancellation, and host resource
   cleanup.
5. Demonstrate guest DNS and an outbound HTTP request in a reproducible setup.

Package managers such as `apk` or `apt` become useful only after the guest has
a suitable root filesystem, DNS, routing, and working virtio networking. They
are guest userspace features, not commands implemented by the VMM.

## Milestone: operational lifecycle

- Multiple named VM configurations and persisted metadata;
- Detached execution and explicit attach/stop/status operations;
- Resource limits and observable lifecycle events;
- Graceful cancellation of active execution;
- Reset/reboot implemented as a deliberate new-boot lifecycle rather than
  treating every guest reset as process exit;
- Failure injection and deterministic cleanup tests;
- Measured idle behavior instead of a polling loop that wastes host CPU.

## Compatibility and platform tracks

- Certify representative Alpine, Debian/Ubuntu, and Fedora kernel/initramfs
  combinations without claiming universal Linux compatibility;
- Add AArch64 only with an AArch64 host/KVM design and its own boot protocol;
- Consider WHPX in a separate Windows platform/backend module;
- Consider Firecracker as an external-process provider when operational
  maturity matters more than the in-process research path;
- Keep OpenVZ or another container runtime behind `IsolationBackendProvider`.

## Performance work

- Parameterize PIO/MMIO dispatch by mapping count and hit position;
- Measure UART transmit, receive, and interrupt transitions;
- Measure Linux boot-layout construction separately from image parsing;
- Add a controlled real-KVM VM-exit round-trip benchmark;
- Add a cold-boot scenario benchmark with recorded system metadata;
- Investigate current allocation in the I/O dispatch benchmark before making
  optimization claims.

Microbenchmarks and end-to-end KVM measurements must remain separate because
they answer different questions. See [benchmarks/README.md](benchmarks/README.md).

## Deliberate non-goals

The core project does not currently aim to provide:

- General PC, firmware, PCI, USB, audio, or graphics emulation;
- Arbitrary bootable ISO compatibility;
- x86-on-Arm or Arm-on-x86 CPU emulation;
- A cloud scheduler, image registry, or orchestration platform;
- Live migration or desktop-VM compatibility;
- A production security boundary before a threat model and hardening work
  exist.
