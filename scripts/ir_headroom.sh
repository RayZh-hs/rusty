#!/usr/bin/env bash
set -uo pipefail
ROOT=/home/rayzh/Projects/rusty
PRELUDE=$ROOT/src/main/kotlin/rusty/ir/prelude
PLUGIN=$ROOT/ref/scripts/insn_count.so
TGT="--target=riscv64-linux-gnu -march=rv64gc -mabi=lp64d"
GCC="riscv64-linux-gnu-gcc -march=rv64gc -mabi=lp64d"
W=/tmp/irbench
mkdir -p $W
riscv64-linux-gnu-gcc -march=rv64gc -mabi=lp64d -O2 -c $PRELUDE/prelude.c -o $W/prelude_c.o 2>/dev/null
clang $TGT -O2 -c $PRELUDE/prelude.ll -o $W/prelude_ll.o 2>/dev/null

count() { qemu-riscv64 -plugin $PLUGIN "$1" < "$2" 2>&1 >/dev/null | sed -n 's/^INSNS //p'; }
# Compare via $(...) so trailing newlines are stripped on BOTH sides — matches the OJ, which
# normalizes trailing whitespace. (8/50 official .out files have no trailing newline; a raw
# `diff -q` mis-reports those as WA even when the content is byte-identical.)
chk() { local got; got="$(qemu-riscv64 "$1" < "$2" 2>/dev/null)"; [ "$got" = "$(cat "$3")" ] && echo AC || echo WA; }

printf "%-18s %12s %12s %12s %12s %12s\n" case rusty clangO0 clangO1 clangO2 llcO2
for C in "$@"; do
  D=$ROOT/src/test/resources/@official-fixed/IR-1/src/$C
  RX=$D/$C.rx; EXP=$D/$C.out
  IN=/dev/null; [ -f $D/$C.in ] && IN=$D/$C.in

  kotlin -J-XX:-UsePerfData -cp $ROOT/build/classes rusty.MainKt --stdio-asm < $RX > $W/r.s 2>$W/r.builtin.s
  $GCC -O0 -static $W/r.s $W/r.builtin.s -o $W/rusty.elf 2>/dev/null
  rusty_n="$(count $W/rusty.elf $IN)($(chk $W/rusty.elf $IN $EXP))"

  kotlin -J-XX:-UsePerfData -cp $ROOT/build/classes rusty.MainKt --emit opt -i $RX -o $W/u.ll 2>/dev/null
  declare -A N
  for opt in O0 O1 O2; do
    clang $TGT -$opt -c $W/u.ll -o $W/u_$opt.o 2>/dev/null
    $GCC -O2 -static $W/u_$opt.o $W/prelude_ll.o $W/prelude_c.o -o $W/c_$opt.elf 2>/dev/null
    N[$opt]="$(count $W/c_$opt.elf $IN)($(chk $W/c_$opt.elf $IN $EXP))"
  done
  llc -O2 -mtriple=riscv64-linux-gnu -mattr=+m,+a,+f,+d,+c $W/u.ll -o $W/u_llc.s 2>/dev/null
  $GCC -O2 -static $W/u_llc.s $W/prelude_ll.o $W/prelude_c.o -o $W/llc.elf 2>/dev/null
  llc_n="$(count $W/llc.elf $IN)($(chk $W/llc.elf $IN $EXP))"

  printf "%-18s %12s %12s %12s %12s %12s\n" "$C" "$rusty_n" "${N[O0]}" "${N[O1]}" "${N[O2]}" "$llc_n"
done
