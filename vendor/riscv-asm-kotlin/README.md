# riscv-asm-kotlin

`space.norb.riscv` is a small Kotlin DSL for generating RISC-V assembly text.
It emits assembly source, not machine code or object files.

The initial target is RV32IM plus common assembler pseudo-instructions. The API
keeps the target and extension model explicit so additional extensions can be
added without changing the DSL shape.

## Example

```kotlin
import space.norb.riscv.*

val loop = labelRef("loop")

val asm = riscv {
    text()
    global("main")
    label("main")

    li(t0, 10)
    li(t1, 1)

    label(loop)
    mul(t1, t1, t0)
    addi(t0, t0, -1)
    bnez(t0, loop)

    mv(a0, t1)
    ret()
}.render()

println(asm)
```

Output:

```asm
    .section .text
    .globl main
main:
    li t0, 10
    li t1, 1
loop:
    mul t1, t1, t0
    addi t0, t0, -1
    bnez t0, loop
    mv a0, t1
    ret
```

## Supported surface

- RV32I integer instructions: jumps, branches, loads, stores, ALU immediates,
  register ALU operations, shifts, `fence`, `ecall`, and `ebreak`.
- RV32M multiply/divide instructions: `mul`, `mulh`, `mulhsu`, `mulhu`, `div`,
  `divu`, `rem`, and `remu`.
- Common pseudo-instructions: `nop`, `li`, `mv`, `not`, `neg`, `seqz`, `snez`,
  `sltz`, `sgtz`, zero-compare branches, `bgt`, `ble`, unsigned variants, `j`,
  `jr`, single-operand `jalr`, `ret`, `call`, `tail`, `la`, `lla`, `lga`, and
  `unimp`.
- Basic assembler directives: sections, globals, labels, `.align`, `.balign`,
  data values, strings, space reservation, symbols, options, comments, blank
  lines, and raw lines.

For unsupported instructions or target-specific assembler features, use
`emit("mnemonic", ...)`, `directive(...)`, or `raw(...)`.

## Memory operands

Use `mem(offset, base)` or Kotlin indexing syntax:

```kotlin
riscv {
    lw(a0, sp[0])
    sw(a0, sp[4])
    lw(t0, mem("%lo(value)", gp))
}
```

## Targets

The default target is `RiscvTarget.RV32IM`.

```kotlin
riscv(RiscvTarget.RV32I) {
    add(a0, a1, a2)
}
```

M-extension instructions are rejected when the target does not include
`RiscvExtension.M`.

## Build

This repository uses the Gradle wrapper with the Kotlin JVM plugin. The Gradle
runtime should be Java 21 or another Java 17+ runtime because the Maven Central
publishing plugin requires it; the published library remains JVM 8 compatible.

```sh
./gradlew test
```

## Publishing

The project publishes as `space.norb:riscv-asm-kotlin`.

For local development, publish to the local Maven cache:

```sh
./gradlew publishToMavenLocal
```

If you use `mise`, the same workflows are available as tasks:

```sh
mise trust
mise run publish-local
```
