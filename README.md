# PanamaKVM

PanamaKVM is an experimental, embeddable virtual-machine monitor written in
Java 25. It talks to Linux KVM through the Foreign Function and Memory API,
boots a modern x86 Linux `bzImage` directly, and exposes a serial console
without a project-specific JNI library.

The project intentionally implements a small microVM-shaped boundary rather
than general PC emulation. Today it can boot the pinned Alpine kernel into a
BusyBox smoke guest or an interactive shell. Block devices, networking, SMP,
firmware boot, and production isolation are not implemented yet.

> [!IMPORTANT]
> PanamaKVM is a research project, not a hardened sandbox. Do not run untrusted
> guest workloads with it.

## What works today

- Java 25 multi-module build on Linux and Windows;
- Linux x86-64 KVM backend implemented with FFM;
- Deterministic simulator for portable development;
- Direct Linux boot with `boot_params`, E820, command line, kernel, and initramfs placement;
- One vCPU and one contiguous RAM slot from 64 MiB to 3 GiB;
- Userspace 16550A serial console, CMOS RTC, PIO, MMIO, IRQ chip, and PIT;
- Interactive BusyBox shell with prompt flushing and guest-directed terminal controls;
- JUnit, opt-in real-KVM integration testing, JaCoCo, Javadoc, and JMH.

## Requirements

### Portable build

- JDK 25;
- The checked-in Gradle wrapper (`gradlew` or `gradlew.bat`).

The portable build and simulator work on Linux and Windows. 
No system Gradle installation is required.

### Real KVM guest

- Linux x86-64, including WSL 2 when `/dev/kvm` is exposed;
- Readable and writable `/dev/kvm`;
- `bash`, `curl`, `sha256sum`, `cpio`, `gzip`, and a statically linked BusyBox
  for building the repository initramfs.

For Ubuntu or WSL Ubuntu, install the guest build dependencies with:

```bash
sudo apt-get update
sudo apt-get install --yes busybox-static coreutils cpio curl gzip
```

Check KVM access before trying to boot:

```bash
test -r /dev/kvm && test -w /dev/kvm && echo "KVM is accessible"
```

## Quick start

### 1. Build and test

Linux, macOS, or WSL:

```bash
./gradlew build javadoc
```

Windows PowerShell:

```powershell
.\gradlew.bat build javadoc
```

The normal build runs the portable test suite. Generate JaCoCo reports with:

```bash
./gradlew coverage
```

Reports are written below each module's `build/reports/` directory.

### 2. Run the portable simulator

No KVM or guest image is required:

```bash
./gradlew :cli:run --args="--list-backends"
./gradlew :cli:run --args="--backend sim"
```

### 3. Build the pinned smoke guest

The build script reads
[`guest/kernel/alpine-3.24.2-x86_64.properties`](guest/kernel/alpine-3.24.2-x86_64.properties),
downloads the declared kernel, verifies its SHA-256, and builds a minimal
BusyBox initramfs:

```bash
bash guest/scripts/build-smoke-guest.sh
```

This does not compile Linux from source. The properties file is an immutable
download manifest for the tested kernel. Generated files are placed in:

```text
guest/artifacts/alpine-3.24.2-x86_64-smoke/
├── vmlinuz
├── initramfs.cpio.gz
└── SHA256SUMS
```

### 4. Boot the smoke guest

Build the installed CLI distribution once, then run it directly:

```bash
./gradlew :cli:installDist

./cli/build/install/panama-kvm/bin/panama-kvm \
  --kernel guest/artifacts/alpine-3.24.2-x86_64-smoke/vmlinuz \
  --initrd guest/artifacts/alpine-3.24.2-x86_64-smoke/initramfs.cpio.gz \
  --non-interactive
```

Expected terminal markers include:

```text
PANAMAKVM_SMOKE_OK
Linux guest completed: SHUTDOWN
```

The smoke guest always runs a finite script and stops by itself. The
`--non-interactive` flag additionally prevents the CLI from forwarding host
terminal input.

### 5. Boot an interactive guest

```bash
bash guest/scripts/build-interactive-guest.sh

./cli/build/install/panama-kvm/bin/panama-kvm \
  --kernel guest/artifacts/alpine-3.24.2-x86_64-interactive/vmlinuz \
  --initrd guest/artifacts/alpine-3.24.2-x86_64-interactive/initramfs.cpio.gz
```

After `PANAMAKVM_INTERACTIVE_READY`, try:

```text
/ # pwd
/
/ # uname -a
/ # mkdir /example
/ # ls /
```

Terminal controls (e.g., Ctrl+C) is forwarded to the guest foreground command. Type `exit` to close the
shell and stop the VM. `reboot -f` also terminates the current minimal VM; it
does not create a fresh VM instance.

Do not combine the interactive initramfs with `--non-interactive` unless some
other input mechanism is provided: the flag disables host input forwarding,
but the guest init still starts a shell and waits for input.

### 6. Run the real-KVM integration test

The hardware-backed test is intentionally excluded from ordinary `test` and
`build` tasks:

```bash
export PANAMAKVM_TEST_KERNEL="$PWD/guest/artifacts/alpine-3.24.2-x86_64-smoke/vmlinuz"
export PANAMAKVM_TEST_INITRD="$PWD/guest/artifacts/alpine-3.24.2-x86_64-smoke/initramfs.cpio.gz"
./gradlew kvmTest
```

It boots the real kernel, waits for `PANAMAKVM_SMOKE_OK`, and requires a clean
KVM shutdown within its timeout. Missing environment variables or unavailable
KVM cause the test to be skipped.

## CLI reference

```text
panama-kvm --list-backends
panama-kvm [--backend sim]
panama-kvm --kernel <bzImage> [--initrd <initramfs.cpio.gz>]
           [--memory-mib 256] [--cmdline <linux arguments>]
           [--label key=value] [--non-interactive]
```

| Option              | Meaning                                                     |
|---------------------|-------------------------------------------------------------|
| `--backend <id>`    | Selects `sim`, `kvm`, or another installed provider         |
| `--kernel <path>`   | Selects direct Linux boot and defaults the backend to `kvm` |
| `--initrd <path>`   | Supplies an optional initramfs; requires `--kernel`         |
| `--memory-mib <n>`  | Sets guest RAM; KVM currently accepts 64–3072 MiB           |
| `--cmdline <text>`  | Replaces the default serial-console Linux command line      |
| `--label key=value` | Adds immutable metadata; repeat with unique keys            |
| `--non-interactive` | Disables host terminal setup and input forwarding           |
| `--list-backends`   | Prints installed providers and their availability           |
| `--help`, `-h`      | Prints CLI help                                             |

The loader accepts a modern x86 `bzImage` and an optional initramfs. A full
distribution may need its own command line, modules, root filesystem, and
userspace. ISO, GRUB, and UEFI images are not accepted.

## Project layout

| Module               | Responsibility                                             |
|----------------------|------------------------------------------------------------|
| `core`               | Guest memory, typed VM exits, PIO/MMIO buses, and devices  |
| `platform-api`       | Host OS and CPU contracts plus provider discovery          |
| `platform-linux`     | Linux file descriptors, `ioctl`, and `mmap` through FFM    |
| `virtualization-api` | VM/provider contracts, run loop, and Linux boot capability |
| `virtualization-sim` | Deterministic portable backend                             |
| `virtualization-kvm` | KVM UAPI, x86 bootstrap state, and Linux loader            |
| `cli`                | Service-loaded application and terminal bridge             |
| `benchmarks`         | Isolated JMH harness and microbenchmarks                   |
| `guest`              | Kernel metadata, initramfs sources, and build scripts      |

## Documentation

- [Architecture](ARCHITECTURE.md)
- [Guest profiles and troubleshooting](guest/README.md)
- [Benchmark guide](benchmarks/README.md)
- [Roadmap](ROADMAP.md)
- [Contributing](CONTRIBUTING.md)

## Troubleshooting

- **`/dev/kvm` is unavailable:** use the simulator or enable KVM/nested
  virtualization in the Linux environment.
- **The initramfs build rejects BusyBox:** install a statically linked BusyBox;
  dynamically linked binaries cannot run in this minimal initramfs alone.
- **The generated launcher uses the wrong Java:** check `JAVA_HOME`; Gradle's
  toolchain does not control a launcher executed later in another shell.
- **The terminal looks broken after a force kill:** run `stty sane`. Normal
  close and JVM shutdown restore the saved terminal state automatically.
- **A custom distribution does not boot:** start with the repository Alpine
  profile, then verify kernel format, command line, initramfs userspace,
  required modules, and root-device assumptions separately.

## License

PanamaKVM is licensed under the [MIT License](LICENSE). Produced JARs embed the
license as `META-INF/LICENSE`.
