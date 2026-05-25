# QEMU rv64

This file keeps its historical name, but the parent repo now runs execution tests with `qemu-riscv64` rather than `reimu`.

## Quick Start

### Installation

Install a riscv64 user-mode QEMU binary such as `qemu-riscv64`, plus a riscv64 Linux toolchain/sysroot that clang can use for linking.

### Running a Program

The Gradle-backed execution tests compile a riscv64 Linux executable and run it through QEMU.

Examples:

```shell
./gradlew officialIrTests -DqemuPath=qemu-riscv64
./gradlew officialAsmTests -DclangArgs="--sysroot=/opt/riscv64-sysroot" -DqemuSysroot=/opt/riscv64-sysroot
```

Use `-DqemuArgs` for extra emulator flags and `-DqemuClangTarget` if your local toolchain expects a different riscv64 Linux target triple.

## Notes

- The compiler IR still uses a generic riscv64 ELF triple internally; the test harness overrides clang's target when it builds the executable that QEMU runs.
- `-DclangArgs` is split on whitespace and appended directly to the clang command line.
- `-DqemuSysroot` maps to `qemu-riscv64 -L <path>` for dynamically linked rv64 binaries.

## Support

Use your local toolchain documentation for QEMU and the riscv64 sysroot you are linking against.
