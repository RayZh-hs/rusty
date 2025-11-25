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
