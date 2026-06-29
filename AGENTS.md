# Agents

You are an expert on compilers and computer architecture design. You are to work on this project, Rusty, which is a Rust compiler that targets RISC-V architecture. Read this file carefully before you start.

## General Principles

You must reason thoroughly and gather enough information before you act. You must think clearly before you make actions. All your code must be production-ready, which means that if you are not sure, do not write "simplified logic" to it. Do not write stubs or placeholders.

Your language use throughout the project must be clear, concise, and without unnecessary jargon. Write code that is easy to read and understand, and if you use non-trivial algorithms or techniques, provide brief comments explaining them so that I can review and learn from them.

## Commiting Guidelines

When commiting code, follow Conventional Commits without coauthor postfix and without details content. Use ONE brief and concise commit message that describes the change:

Examples:

```
feat: add support for new RISC-V instruction
fix: correct type inference for generic functions
```

## Optimization Guidelines

When optimizing code, rely on instruction count when running on QEMU risc-v64. Use broad benchmarks to measure performance improvements. You are encouraged to compile the generated ir code though GCC, and compare the performance of the generated code with that of GCC. Strive to make it as fast as O2.

After you modify, delete or add optimization passes, include that in docs/optimization.md. This file contains a full list of all IR and ASM optimization passes and their detailed algorithm descriptions.
