# Testing

## Compiling Individual Files

To compile individual Rust source files, use the following command:

```bash
./gradlew run --args="-i <input-file> -o <output-file> -s verbose -m <mode>"
```

- You can find test files under `src/test/resources/`.
- Place output files in `out/`
- Supported modes include `sem` (semantic), `ir` (ir generation).

## Compiling Test Suites

The project includes several test suites:

### Manual Tests

Manual tests reside in `src/test/resources/<dir>/`, where `<dir>` does not start with `@`. You can run these tests using:

```
# Run all manual tests (this is the on-push action)
# By default this disregards the official tests
./gradlew test

# Run only ir tests
./gradlew test --tests "rusty.ManualIrTests"

# Use -Dname to filter by name (string inclusion)
./gradlew test --tests "rusty.ManualIrTests" -Dname=shadowing_redeclare --info
```

After running manual tests, the ir dumps will be located in `build/ir-manual/all/`

### Official Tests

Official tests are located in `src/test/resources/@official/`, which can be run using:

```
# All official semantic tests
./gradlew officialSemanticTest

# All official ir tests
./gradlew officialIrTest

# Run a single official ir test
./gradlew officialSemanticTest -Dname=comprehensive1
```

### Official Fixed Tests

Official fixed tests are located in `src/test/resources/@official_fixed/`, which can be run using. They no longer need to be implemented.

### Arguments

You can pass additional arguments to the tests using the `-D<arg>=<value>` syntax. Common arguments include:

- `-Dname [name]`: Filter by name of the test (string inclusion).
- `-DnoClang`: Do not use clang for IR generation.
- `-DclangPath [path]`: Specify a custom path to the clang binary.
- `-DclangArgs [args]`: Extra whitespace-separated arguments forwarded to clang when linking rv64 test executables.
- `-DqemuPath [path]`: Specify a custom path to the `qemu-riscv64` binary.
- `-DqemuArgs [args]`: Extra whitespace-separated arguments forwarded to QEMU.
- `-DqemuSysroot [path]`: Pass `-L <path>` to `qemu-riscv64` when running dynamically linked rv64 binaries.
- `-DqemuClangTarget [triple]`: Override the clang link/assembly target used for the QEMU backend. The default is `riscv64-linux-gnu`.

## Runtime Backend

IR, opt, and asm execution tests now target `rv64im`, link a riscv64 Linux executable with clang, and run that executable under `qemu-riscv64`.

If your cross-linker setup is not on the default search path, use `-DclangArgs` to provide the relevant toolchain or sysroot flags and `-DqemuSysroot` to point QEMU at the matching runtime.
