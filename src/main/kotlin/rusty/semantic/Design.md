The passes of semantic check are designed as follows:

## Item Name Collection (DONE)
**Items** (specifically names, and their components' names) are recorded. No type deduction is performed (left as `Slot()`)

## Impl Injection (DONE)
Impl blocks are injected into the declaration to form C-like structs.

> Trait checks should be performed in this phase, but has not been implemented.

## Const Value Resolution
Constant expressions will be evaluated and assigned their values.

## Item Type Resolution
Constants and item field types will be deduced. After this phase the Scope Tree will have been completed and will remain frozen.

## Dynamic Type Check
The program will be stepped to ensure that all types align correctly. Let shadowing will be resolved in this phase.

**Closure checks will be performed in this phase**

## Context Check
A context stack will be maintained whist the program is being stepped through. This check will ensure that:

1. Breaks and Returns are used correctly;
