#!/usr/bin/env bash
# Correctness + dynamic-instruction-count harness over all official IR-1 cases,
# using rusty's own backend (--stdio-asm). Prints per-case AC/WA and qemu insn count.
set -uo pipefail
ROOT=/home/rayzh/Projects/rusty
PLUGIN=$ROOT/ref/scripts/insn_count.so
GCC="riscv64-linux-gnu-gcc -march=rv64gc -mabi=lp64d"
SRC=$ROOT/src/test/resources/@official/IR-1/src
W=/tmp/brbench
mkdir -p $W

count() { qemu-riscv64 -plugin $PLUGIN "$1" < "$2" 2>&1 >/dev/null | sed -n 's/^INSNS //p'; }
chk() { local got; got="$(qemu-riscv64 "$1" < "$2" 2>/dev/null)"; [ "$got" = "$(cat "$3")" ] && echo AC || echo WA; }

CASES=("$@")
if [ ${#CASES[@]} -eq 0 ]; then
  CASES=($(ls -d $SRC/*/ | xargs -n1 basename | sort))
fi

total_ac=0; total_n=0
for C in "${CASES[@]}"; do
  D=$SRC/$C
  RX=$D/$C.rx; EXP=$D/$C.out
  [ -f "$RX" ] || { printf "%-22s MISSING\n" "$C"; continue; }
  IN=/dev/null; [ -f $D/$C.in ] && IN=$D/$C.in

  if ! kotlin -J-XX:-UsePerfData -cp $ROOT/build/classes rusty.MainKt --stdio-asm < $RX > $W/r.s 2>$W/r.builtin.s; then
    printf "%-22s COMPILE_FAIL\n" "$C"; continue
  fi
  if ! $GCC -O0 -static $W/r.s $W/r.builtin.s -o $W/rusty.elf 2>$W/r.link.err; then
    printf "%-22s LINK_FAIL\n" "$C"; continue
  fi
  st="$(chk $W/rusty.elf $IN $EXP)"
  n="$(count $W/rusty.elf $IN)"
  printf "%-22s %-4s %12s\n" "$C" "$st" "$n"
  [ "$st" = AC ] && total_ac=$((total_ac+1))
  total_n=$((total_n+1))
done
printf "%-22s %d/%d AC\n" "TOTAL" "$total_ac" "$total_n"
