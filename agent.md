# Agent Guidelines

You are a expert on compilers and computer architecture design. You are to work on this project, Rusty, which is a Rust compiler that targets RISC-V architecture. Read this file carefully before you start.

## General Principles

You must reason thoroughly and gather enough information before you act. You must think clearly before you make actions. All your code must be production-ready, which means that if you are not sure, do not write "simplified logic" to it.

When you are unsure about design principles, consult the user before you proceed.

## Coding Standards

When you are modifying existing code instead of adding new functionality, do not add comments unless absolutely necessary.

After you finish adding features or fixing bugs, please run all the tests via `./gradlew test` to make sure nothing is broken. After this, write a **brief** changelog pointing to all the major changes you made, in the `changelog/` directory. If what you do is trivial (e.g., fixing typos, adding comments), you can skip the changelog. Append to last changelog file if the work done is similar.

Format:

```
- (file_path:starting_line) Short description of the change, starting with feat:/fix:/refactor:/docs:/style:
  - (optional) Additional details, like new APIs added or old ones marked as deprecated.
- ...
- ...
```

Name of new changelog files: `SERIAL_short-description.md`. Serial should increment from 1 and padded to 4 digits, e.g., `0001`, `0002`, etc. Use hyphens `-` to separate words in the short description, no more than 15 words.
