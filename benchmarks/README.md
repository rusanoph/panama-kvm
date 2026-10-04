# Benchmarks

The `benchmarks` module contains JMH microbenchmarks for specific Java code
paths. It is isolated from runtime modules so JMH, annotation processors, and
generated harness classes do not enter production artifacts.

Benchmark output is not committed. Store local results below
`benchmarks/results/`, which is ignored by Git.

## Requirements

- JDK 25;
- the repository Gradle wrapper;
- a quiet and stable host for measurements intended for comparison.

The current JMH benchmarks are portable and do not require `/dev/kvm`.

## Available benchmarks

List the generated JMH entries:

```bash
./gradlew :benchmarks:run --args="-l"
```

| Benchmark                                    | Measures                                                                                | Does not measure                                                        |
|----------------------------------------------|-----------------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| `IoExitDispatchBenchmark.dispatchUartOutput` | Java-side `VmExit.Io` routing through `IoPortBus` into a UART with a null output stream | `KVM_RUN`, host syscalls, terminal rendering, or full guest I/O latency |
| `LinuxKernelParserBenchmark.parseImage`      | Validation and defensive copying of a synthetic 16 MiB bzImage-shaped byte array        | File I/O, guest-memory placement, decompression, or boot time           |

Treat these as narrow component measurements. Neither benchmark is a VM startup
or end-to-end virtualization benchmark.

## Fast harness check

Use a short run after editing a benchmark or its measured code:

```bash
./gradlew :benchmarks:run \
  --args="IoExitDispatchBenchmark -wi 1 -i 1 -f 1 -w 100ms -r 100ms"
```

This verifies that JMH discovers and executes the benchmark. The result is too
short for a performance claim.

Run the parser benchmark the same way:

```bash
./gradlew :benchmarks:run \
  --args="LinuxKernelParserBenchmark -wi 1 -i 1 -f 1 -w 100ms -r 100ms"
```

## Normal runs

The source annotations define each benchmark's intended warmup, measurements,
forks, mode, and time unit. Run one benchmark without command-line overrides:

```bash
./gradlew :benchmarks:run --args="IoExitDispatchBenchmark"
./gradlew :benchmarks:run --args="LinuxKernelParserBenchmark"
```

Run all current benchmarks:

```bash
./gradlew :benchmarks:run
```

The complete run takes materially longer because each class uses multiple
forks and one-second warmup/measurement iterations.

## Profilers

JMH's GC profiler is a useful first check for unexpected allocation:

```bash
./gradlew :benchmarks:run \
  --args="IoExitDispatchBenchmark -prof gc"
```

Linux `perf`, `perfasm`, or JFR profiles require host support and may need
additional permissions. Run profiling separately when event multiplexing or
profiler overhead would distort the primary measurement.

Consult JMH help for the exact options supported by the pinned version:

```bash
./gradlew :benchmarks:run --args="-h"
```

## Saving raw results

From the `benchmarks` project, Gradle's `run` task uses that project directory
as its working directory. This command writes ignored JSON output to
`benchmarks/results/`:

```bash
mkdir -p benchmarks/results
./gradlew :benchmarks:run \
  --args="IoExitDispatchBenchmark -rf json -rff results/io-dispatch.json"
```

On Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force benchmarks/results | Out-Null
.\gradlew.bat :benchmarks:run `
  '--args=IoExitDispatchBenchmark -rf json -rff results/io-dispatch.json'
```

Keep the console output alongside JSON when investigating compiler decisions or
profiler warnings.

## Recording a reproducible result

Record at least:

- Exact Git commit and whether the worktree was clean;
- `java -version` and Gradle/JMH versions;
- Operating system and kernel;
- CPU model, topology, virtualization layer, and power mode;
- JVM arguments;
- Benchmark filter and parameters;
- Warmup, measurement time, iteration count, and fork count;
- Profilers and host background load;
- Raw JMH JSON and error bars.

Compare like with like. A WSL result, native Linux result, and Windows result
describe different environments even when the Java source is identical.

## Interpreting results

- Do not compare numbers with different units or benchmark modes.
- Do not publish a single fork or one 100 ms iteration as stable evidence.
- Inspect allocation before attributing a regression to dispatch logic.
- Prefer changes that improve a measured workload without making protocol code
  less clear or less testable.
- Report “not measured” when an end-to-end claim is not supported by an
  end-to-end benchmark.

## Planned benchmark coverage

The most useful next additions are:

1. Parameterized PIO/MMIO lookup by mapping count and hit position;
2. UART transmit, receive, and level-IRQ transitions;
3. Linux boot-layout construction with kernel/initramfs size parameters;
4. The backend-neutral `VmRunner` exit-dispatch loop;
5. A separate Linux-only real-KVM exit round-trip harness;
6. A separate cold-boot scenario from VM creation to a guest marker.

Real-KVM and cold-boot measurements should not be presented as JMH
microbenchmarks: they include kernel scheduling, KVM, hardware, guest behavior,
and host configuration.
