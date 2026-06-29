# scripts/

## `benchmark.py` — compiler benchmark

Compiles each official IR-1 testcase with rusty's own backend, runs it under
`qemu-riscv64`, checks correctness (AC/WA), and measures a metric. Optionally builds
LLVM reference backends (clang/llc) from rusty's *own* emitted IR to show the
optimization gap. gcc is used only as the rv64 assembler/linker.

Replaces the former `branch_check.sh`, `ir_headroom.sh`, `full_compare.sh`, and
`profile_ir1_rv64.py`.

### Prerequisites

`make build` (needs `build/classes`), and on `PATH`: `kotlin`, `qemu-riscv64`,
`riscv64-linux-gnu-gcc`, and — for `--refs` — `clang` and `llc`. Instruction-count mode
needs the qemu plugin at `ref/scripts/insn_count.so`.

### Common usage

```sh
scripts/benchmark.py                              # rusty-only insn counts, all cases
scripts/benchmark.py --refs all                   # + llc-O2 / clang-O2 / clang-O3 gap
scripts/benchmark.py --case comprehensive1 --refs llc,clang-O2
scripts/benchmark.py --metric time --report-dir build/bench
```

With both `llc` and `clang-O2` present, the insns table adds gap columns:
`bk_gap` = rusty/llc (backend), `ir_gap` = llc/clang-O2 (middle-end), `total` = rusty/clang-O2.

### Key flags

| Flag | Default | Meaning |
| --- | --- | --- |
| `--metric {insns,time}` | `insns` | qemu instruction count, or wall-clock median |
| `--refs none\|all\|<list>` | `none` | reference backends: `llc`, `clang-O0..O3` (comma-separated); `all` = `llc,clang-O2,clang-O3` |
| `--case NAME` | all | run only this case (repeatable) |
| `--suite DIR` | `@official/IR-1` | suite root containing `global.json` |
| `--report-dir DIR` | — | also write `report.csv` + `report.md` |
| `--runs / --warmups` | `5 / 1` | sampling for `--metric time` |

Counts are reproducible: links pass `-Wl,--build-id=none` so the random GNU build-id
note (which perturbs glibc startup by a few hundred instructions) is stripped.

Toolchain binaries are overridable via flags or env vars (`QEMU_RISCV64`, `GCC_RISCV64`,
`CLANG`, `LLC`, `QEMU_SYSROOT`, `QEMU_GCC_TARGET`).

## `submit_oj.py` — OJ submission

Driven by the `submit-oj` skill; see `config.json` for the endpoint/session. Not part
of benchmarking.
