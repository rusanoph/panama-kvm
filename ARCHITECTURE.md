# Architecture

PanamaKVM is a small Java control plane around hardware-assisted virtualization.
It is intentionally designed as a readable microVM foundation, not as a
general-purpose PC emulator.

## Design goals

- Keep Linux/KVM and native ABI details behind explicit module boundaries;
- Use Java 25 FFM instead of a project-specific JNI library;
- Direct-boot Linux without BIOS, UEFI, GRUB, or a device compatibility zoo;
- Represent VM exits as backend-neutral values;
- Keep device state machines testable without `/dev/kvm`;
- Make backend and host integrations discoverable rather than hard-coded;
- Own native memory and file descriptors through scoped resources.

Production sandboxing, arbitrary guest compatibility, and full-system emulator and type 2 hypervisor (e.g., QEMU) 
replacement are not current goals.

## Modules and dependency direction

An arrow means "depends on".

```text
cli ───────────────────────────────► virtualization-api
 │                                       │          │
 │ runtime providers                     │          └──► platform-api
 ├────────► virtualization-sim ──────────┤
 └────────► virtualization-kvm ──────────┤
                    │                    └──────────────► core
                    └──► platform-linux ───────────────► platform-api

benchmarks ─────────► core + virtualization-kvm
```

| Module               | Owns                                                                                 |
|----------------------|--------------------------------------------------------------------------------------|
| `core`               | Guest memory contract, typed VM exits, address ranges, PIO/MMIO buses, UART, and RTC |
| `platform-api`       | Normalized host platform and host-OS provider SPI                                    |
| `platform-linux`     | FFM bindings for Linux file descriptors, `ioctl`, `mmap`, and error handling         |
| `virtualization-api` | VM lifecycle, provider SPI, run policy, exit loop, and Linux boot capability         |
| `virtualization-sim` | Deterministic scripted implementation of the VM contract                             |
| `virtualization-kvm` | KVM UAPI layouts, native VM lifecycle, x86 bootstrap, and Linux loader               |
| `cli`                | Argument parsing, provider selection, terminal ownership, and device wiring          |
| `benchmarks`         | JMH-only measurement code; never a runtime dependency                                |

`core` knows nothing about KVM, FFM, Linux, WSL, or a Linux distribution.
Native Linux operations remain in `platform-linux`; KVM policy remains in
`virtualization-kvm`.

## Provider discovery

Two independent service-provider interfaces are used:

- `HostOperatingSystemProvider` supplies host-native operations;
- `VirtualizationProvider` supplies VM backends.

`HostOperatingSystemProviders` and `VirtualizationProviders` are the only
support classes that use `ServiceLoader` directly. Implementations use
`@AutoService`; the `panamakvm.auto-service` convention plugin configures the
annotation and processor and generates `META-INF/services` descriptors.
Handwritten service descriptor files are therefore not part of the design.

The current providers are:

- `LinuxOperatingSystemProvider` for Linux FFM operations;
- `ScriptedVirtualizationProvider` for portable deterministic execution;
- `KvmVirtualizationProvider` for Linux x86-64 KVM.

## Runtime flow

```text
CLI options
    │
    ▼
VirtualizationProviders.discover()
    │
    ▼
provider.create(configuration)
    │
    ├── simulator: scripted VM exits
    │
    └── KVM: /dev/kvm → VM fd → vCPU fd → shared kvm_run mapping
                         │
                         ▼
                  bootLinux(spec)
                         │
                         ├── parse and validate bzImage
                         ├── place boot data in guest RAM
                         └── initialize x86 CPU state
                         │
                         ▼
                    VmRunner.run()
                         │
                         ├── KVM_RUN
                         ├── decode one VM exit
                         ├── dispatch PIO/MMIO
                         └── repeat or finish
```

### Machine creation

`KvmVirtualMachine.create()`:

1. Resolves the Linux host service and validates Linux x86-64;
2. Opens `/dev/kvm` and checks the KVM API and user-memory capability;
3. Creates a VM, in-kernel IRQ chip, and PIT;
4. Allocates page-aligned native memory and registers it as guest physical RAM;
5. Creates one vCPU and installs supported CPUID entries;
6. Maps the vCPU's shared `struct kvm_run` state.

The three native descriptors have distinct roles:

```text
/dev/kvm fd   KVM subsystem and VM factory
VM fd         one guest address space and machine-level devices
vCPU fd       one processor, its registers, and KVM_RUN
```

### Direct Linux boot

`LinuxKernelImage` validates the x86 boot header and extracts protocol metadata.
`LinuxBootLoader` then writes a complete direct-boot layout into guest RAM:

| Guest physical address | Contents                                                |
|-----------------------:|---------------------------------------------------------|
|          `0x0000_0500` | Bootstrap GDT written by `KvmVirtualMachine`            |
|          `0x0000_7000` | Linux `boot_params` / zero page                         |
|          `0x0002_0000` | NUL-terminated kernel command line                      |
|          `0x0010_0000` | Protected-mode kernel payload and entry point           |
|       high aligned RAM | Optional initramfs, below the kernel-advertised maximum |

The E820 map exposes usable low RAM, reserves the conventional x86
EBDA/VGA/firmware hole, and exposes the remaining RAM above 1 MiB.

`KvmVirtualMachine` starts the kernel in 32-bit protected mode as required by
the x86 boot protocol. `RIP` points at the kernel payload and `RSI` points at
`boot_params`. The Linux decompressor is responsible for constructing page
tables and entering 64-bit mode.

This is not an ELF loader and not a firmware boot path. ISO, BIOS, UEFI, and
GRUB images are outside the current contract.

## Execution and VM exits

`KVM_RUN` executes guest instructions in hardware until KVM needs userspace.
`KvmVirtualMachine.runUntilExit()` decodes one exit and returns a sealed
`VmExit` value. `VmRunner` owns the outer loop.

```text
guest OUT to COM1
    │
    ▼
KVM_EXIT_IO in struct kvm_run
    │
    ▼
VmExit.Io
    │
    ▼
IoPortBus
    │
    ▼
Uart16550 → host output
    │
    ▼
next KVM_RUN resumes the guest
```

PIO and MMIO buses validate device ranges at construction and reject overlap.
Absent-device fallbacks model probe-safe behavior explicitly. This matters for
legacy Linux probing: returning an arbitrary value for CMOS register A once
caused the guest to poll the update-in-progress bit for tens of seconds.

## Serial input and interrupts

For an interactive session, the CLI temporarily puts the host TTY in
character-at-a-time mode with local echo and host signal generation disabled.
The guest TTY remains responsible for echo, line editing, and terminal controls (e.g., Ctrl+C).

Input follows the reverse path:

```text
host byte → ConsoleSession → UART RX queue → IRQ4 → guest ttyS0 driver
```

The UART receive interrupt is level-triggered in the model: it remains asserted
while receive data is available and enabled, then deasserts after the queue is
drained or cleared. Host cursor-position responses are filtered before they can
be mistaken for guest keyboard input.

## State, ownership, and threading

- A `KvmVirtualMachine` owns its arenas, memory mapping, and all three native descriptors.
- Construction failure rolls resources back in dependency order.
- `close()` is idempotent while stopped and rejects closing a vCPU from another thread during active `KVM_RUN`.
- The current KVM backend supports exactly one vCPU and one contiguous memory slot.
- Terminal input uses a virtual thread; CPU execution remains controlled by the caller of `VmRunner`.
- The simulator implements the same high-level VM contract without native resources or CPU emulation.

## Current compatibility boundary

| Layer            | Current status                                                            |
|------------------|---------------------------------------------------------------------------|
| Host build       | Java 25 on Linux and Windows; other JVM hosts are not continuously tested |
| Hardware backend | Linux x86-64 with accessible `/dev/kvm`                                   |
| Guest CPU        | x86-64, one vCPU                                                          |
| Guest RAM        | One page-aligned slot, 64 MiB–3 GiB                                       |
| Boot             | Modern high-loaded x86 `bzImage`, optional initramfs                      |
| Tested guest     | Pinned Alpine 3.24.2 `virt` kernel and repository BusyBox initramfs       |
| Devices          | 16550A serial, minimal CMOS RTC, in-kernel IRQ chip and PIT               |
| Storage/network  | Not implemented                                                           |
| Security         | No production hardening or untrusted-workload claim                       |

The loader is distribution-neutral at the protocol level, but only the pinned
Alpine profile is currently a repository-backed regression fixture.

## Extension rules

- Add native host calls to a platform-specific module behind `platform-api`.
- Add a VM backend through `VirtualizationProvider`; do not add backend switches to the CLI.
- Add container isolation through `IsolationBackendProvider`, not as a fake VM.
- Keep device protocol logic independent of KVM so it can be unit-tested.
- Prefer `virtio-mmio` before PCI when adding the first block and network path.
- Every new device should include deterministic state-machine tests, malformed
  input tests, and an opt-in hardware-backed integration scenario where useful.

See [ROADMAP.md](ROADMAP.md) for planned increments and
[CONTRIBUTING.md](CONTRIBUTING.md) for change requirements.
