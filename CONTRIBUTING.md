# Contributing to PanamaKVM

Thank you for helping improve PanamaKVM. The project values small, reviewable
changes, explicit native contracts, reproducible evidence, and honest limits.

## Before you start

- Use JDK 25 and the checked-in Gradle wrapper.
- Read [ARCHITECTURE.md](ARCHITECTURE.md) before changing module boundaries.
- Open an issue or discussion before a large backend, device, or public API
  change so the scope can be agreed before implementation.
- Treat the code and executable build configuration as the source of truth.
  Notes and article drafts may describe historical states or future ideas.
- Do not use PanamaKVM as a security boundary for untrusted code.

## Development setup

Linux, macOS, or WSL:

```bash
./gradlew build javadoc
```

Windows PowerShell:

```powershell
.\gradlew.bat build javadoc
```

The wrapper selects Gradle 9.7.1 and the build requests a Java 25 toolchain.
When running an installed CLI launcher separately, verify `JAVA_HOME` as well;
the launcher does not inherit Gradle's toolchain selection.

Useful commands:

```bash
./gradlew test                    # portable unit tests
./gradlew coverage                # portable tests plus JaCoCo reports
./gradlew javadoc                 # strict Javadoc/doclint checks
./gradlew build                   # compile, test, package, and verify
./gradlew :cli:installDist        # runnable CLI distribution
./gradlew :benchmarks:run --args="-l"
```

Run `clean` when validating reproducibility or investigating stale outputs; it
is not required for every local edit.

## Real KVM changes

Changes to KVM UAPI values, native layouts, VM creation, x86 bootstrap state,
guest memory, devices, interrupts, or Linux boot must also be checked on Linux
x86-64 with readable and writable `/dev/kvm`:

```bash
bash guest/scripts/build-smoke-guest.sh
export PANAMAKVM_TEST_KERNEL="$PWD/guest/artifacts/alpine-3.24.2-x86_64-smoke/vmlinuz"
export PANAMAKVM_TEST_INITRD="$PWD/guest/artifacts/alpine-3.24.2-x86_64-smoke/initramfs.cpio.gz"
./gradlew kvmTest --rerun-tasks
```

The ordinary `test` and `build` tasks exclude tests tagged `kvm`. A green
portable build is therefore not evidence that a real guest booted.

For terminal or UART input changes, also build the interactive guest and check:

- The prompt appears without pressing Enter;
- Typed characters are displayed once;
- Ctrl+C interrupts a guest foreground command without terminating the JVM;
- Another command works after Ctrl+C;
- `exit` restores the host terminal and stops the VM.

See [guest/README.md](guest/README.md) for exact commands.

## Architecture rules

- `core` must remain independent of KVM, native OS calls, and distributions.
- Host-native calls belong behind `platform-api` in an OS-specific module.
- Backend behavior belongs behind `virtualization-api` in a backend module.
- Add providers with `@AutoService` and the `panamakvm.auto-service` convention
  plugin. Do not maintain `META-INF/services` files by hand.
- Keep device state machines usable in deterministic unit tests without KVM.
- Prefer immutable records, sealed results, exhaustive switches, scoped
  resources, and explicit ownership.
- Avoid backend checks in the CLI. Capability interfaces and providers should
  express supported operations.
- Keep benchmark dependencies and generated harness code inside `benchmarks`.

## Native ABI changes

KVM and Linux structures are binary contracts, not ordinary Java models.

- Document the upstream structure, field, request, bit, or protocol meaning.
- Use explicit sizes, alignments, signedness conversions, and checked
  arithmetic.
- Validate layouts and payload bounds before exposing a `MemorySegment` slice.
- Remember that `ioctl(2)` is variadic; no-payload KVM calls in this project
  deliberately pass an explicit zero argument.
- Prefer `strace` and kernel-visible evidence when a native failure has several
  plausible explanations.
- Add malformed-input and boundary tests, not only a happy path.

## Code and documentation style

- Production code and public documentation are written in English.
- Public types, methods, fields, records, and non-obvious constants require
  meaningful Javadoc.
- Explain why a numeric constant exists and which ABI it belongs to; do not
  merely repeat its value.
- Keep user-facing commands copyable from the repository root.
- Update `README.md`, `ARCHITECTURE.md`, `ROADMAP.md`, guest documentation, or
  benchmark documentation when behavior or support boundaries change.
- Historical logs and article raw artifacts should remain historical; do not
  silently rewrite evidence to match current code.

Java compilation uses `-Xlint:all -Werror`, and Javadoc uses doclint with
warnings treated as errors.

## Tests

Choose the narrowest test that proves the behavior, then add broader evidence
when crossing a real system boundary:

1. Pure unit test for ranges, parsers, layouts, and device state;
2. Deterministic simulator test for run-loop behavior;
3. Linux-only platform test for provider/native integration;
4. Real-KVM test for boot, IRQ, and guest-visible behavior;
5. PTY-driven test or manual evidence for terminal interaction.

Regression tests should describe the user-visible failure they prevent. A test
for a fixed hang should contain a timeout or other finite completion condition.

## Benchmarks

Use JMH for Java microbenchmarks and a separate harness for end-to-end KVM or
boot measurements. A short harness smoke run is not publishable performance
evidence.

```bash
./gradlew :benchmarks:run \
  --args="IoExitDispatchBenchmark -wi 1 -i 1 -f 1 -w 100ms -r 100ms"
```

Before publishing numbers, follow [benchmarks/README.md](benchmarks/README.md)
and retain raw output plus host metadata.

## Generated and local files

Do not commit:

- downloaded kernels or generated initramfs archives;
- guest disk images;
- `guest/cache/` or `guest/artifacts/`;
- Gradle build outputs and local caches;
- benchmark result files, JFR recordings, crash dumps, logs, or secrets;
- IDE and operating-system metadata.

The Gradle wrapper JAR and scripts are intentional repository inputs and should
remain versioned. Shell entry points use LF; Windows launchers use CRLF as
declared in `.gitattributes`.

## Pull request checklist

- [ ] The change is scoped and respects module boundaries.
- [ ] New behavior has regression or boundary tests.
- [ ] `build` and `javadoc` pass on the relevant portable hosts.
- [ ] Real-KVM behavior was tested when the change crosses that boundary.
- [ ] Commands, support statements, and generated artifact paths are current.
- [ ] Benchmark claims include the required methodology and raw evidence.
- [ ] No generated guest assets, local paths, secrets, or machine-specific
      output were added.

Contributions are accepted under the repository's [MIT License](LICENSE).
