# Guest profiles

This directory contains reviewable initramfs sources, pinned kernel metadata,
and guest build scripts. Downloaded kernels and generated initramfs archives
are deliberately excluded from Git.

```text
guest/
├── kernel/
│   └── alpine-3.24.2-x86_64.properties  # pinned URL, SHA-256, and filename
├── initramfs/
│   ├── overlay/init                     # finite smoke init
│   └── interactive/init                 # serial BusyBox shell init
├── scripts/
│   ├── build-smoke-guest.sh
│   └── build-interactive-guest.sh
├── cache/                               # downloaded kernel; ignored
└── artifacts/                           # generated profiles; ignored
```

The current scripts do not compile Linux from source. They read the pinned
properties file, download that exact Alpine kernel, verify its SHA-256, and
combine it with the locally installed static BusyBox in a minimal `newc`
initramfs. The kernel content is pinned; the BusyBox version comes from the
build host and is recorded only indirectly in the resulting archive checksum.

## Requirements

Run guest builds on Linux or WSL with:

- `bash`;
- `curl`;
- `sha256sum` from GNU coreutils;
- statically linked `busybox`;
- `cpio` and `gzip`.

Ubuntu/WSL Ubuntu:

```bash
sudo apt-get update
sudo apt-get install --yes busybox-static coreutils cpio curl gzip
```

The scripts reject a dynamically linked BusyBox because this minimal initramfs
does not contain its dynamic loader or shared libraries.

## Kernel profile

[`kernel/alpine-3.24.2-x86_64.properties`](kernel/alpine-3.24.2-x86_64.properties)
contains:

```properties
url=https://...
sha256=...
filename=...
```

Both repository guest profiles currently source this file directly. Changing
the URL requires updating the SHA-256 and then rerunning the real-KVM checks.
Do not weaken or bypass checksum validation.

The downloaded kernel is cached at `guest/cache/<filename>`. If the cached file
does not match the declared hash, the script downloads and verifies a fresh
temporary file before replacing the cache entry.

## Smoke guest

Build:

```bash
bash guest/scripts/build-smoke-guest.sh
```

Output:

```text
guest/artifacts/alpine-3.24.2-x86_64-smoke/
├── vmlinuz
├── initramfs.cpio.gz
└── SHA256SUMS
```

Verify the generated pair from inside its artifact directory:

```bash
(cd guest/artifacts/alpine-3.24.2-x86_64-smoke && sha256sum --check SHA256SUMS)
```

Run from the repository root:

```bash
./gradlew :cli:installDist

./cli/build/install/panama-kvm/bin/panama-kvm \
  --kernel guest/artifacts/alpine-3.24.2-x86_64-smoke/vmlinuz \
  --initrd guest/artifacts/alpine-3.24.2-x86_64-smoke/initramfs.cpio.gz \
  --non-interactive
```

The smoke `/init` mounts `/dev`, `/proc`, and `/sys`, prints
`PANAMAKVM_SMOKE_OK` plus basic system information, syncs, and requests reboot.
It never starts a shell and should finish without input.

Expected final marker:

```text
Linux guest completed: SHUTDOWN
```

## Interactive guest

Build:

```bash
bash guest/scripts/build-interactive-guest.sh
```

Output:

```text
guest/artifacts/alpine-3.24.2-x86_64-interactive/
├── vmlinuz
├── initramfs.cpio.gz
└── SHA256SUMS
```

Run in a real terminal rather than through an IDE output pane:

```bash
./gradlew :cli:installDist

./cli/build/install/panama-kvm/bin/panama-kvm \
  --kernel guest/artifacts/alpine-3.24.2-x86_64-interactive/vmlinuz \
  --initrd guest/artifacts/alpine-3.24.2-x86_64-interactive/initramfs.cpio.gz
```

The guest prints `PANAMAKVM_INTERACTIVE_READY`, acquires `/dev/console` as its
controlling terminal, enables guest-side Ctrl+C handling, and starts BusyBox
`ash` interactively.

Useful commands:

```text
/ # pwd
/ # ls -la /
/ # cat /proc/cpuinfo
/ # free -m
/ # mkdir /example
/ # cd /example
```

Type `exit` to leave the shell. The init process reports the shell status,
syncs, and requests reboot so the VMM finishes with `SHUTDOWN`. `reboot -f`
ends the current VM as well; PanamaKVM does not yet implement reset as creation
of a new guest instance.

Ctrl+C is transported as byte `0x03` to the guest serial TTY. It should
interrupt a foreground command such as `sleep 30` without stopping the host JVM
or making the shell unusable.

`--non-interactive` only disables host terminal setup and input forwarding. It
does not change the interactive guest's `/init`; without another input source,
that guest will continue waiting in its shell.

## Real-KVM integration test

The automated smoke test consumes the generated smoke artifacts through
environment variables:

```bash
export PANAMAKVM_TEST_KERNEL="$PWD/guest/artifacts/alpine-3.24.2-x86_64-smoke/vmlinuz"
export PANAMAKVM_TEST_INITRD="$PWD/guest/artifacts/alpine-3.24.2-x86_64-smoke/initramfs.cpio.gz"
./gradlew kvmTest --rerun-tasks
```

It requires Linux x86-64 and readable/writable `/dev/kvm`. If prerequisites or
environment variables are absent, JUnit skips the test rather than claiming a
real boot passed.

## Using another kernel or initramfs

The CLI can accept another modern x86 high-loaded `bzImage` directly:

```bash
./cli/build/install/panama-kvm/bin/panama-kvm \
  --kernel /path/to/bzImage \
  --initrd /path/to/initramfs.cpio.gz \
  --cmdline "console=ttyS0,115200 rdinit=/init panic=-1 reboot=t"
```

Protocol compatibility does not guarantee distribution compatibility. Another
distribution may require:

- a different `rdinit` or `init` path;
- kernel modules in its initramfs;
- a block-backed root filesystem, which is not implemented yet;
- distribution-specific command-line arguments;
- virtual devices that PanamaKVM does not provide.

ISO, UEFI, GRUB, and disk-image boot are not supported by the current loader.

## Troubleshooting

- **`/dev/kvm` cannot be opened:** verify Linux x86-64, nested virtualization,
  device presence, and read/write permissions.
- **BusyBox is reported as dynamic:** install `busybox-static` and confirm
  `ldd "$(command -v busybox)"` reports a non-dynamic executable.
- **The shell prompt is not interactive:** run the installed launcher from a
  PTY and omit `--non-interactive`.
- **The terminal is left in a bad state after `kill -9`:** run `stty sane`.
  Normal exits and ordinary JVM shutdown restore the saved terminal settings.
- **The build uses stale input:** remove the specific file below `guest/cache/`
  and rerun the script; generated guest directories are safe to regenerate.

Never commit files under `guest/cache/` or `guest/artifacts/`.
