#!/usr/bin/env python3
"""Unified benchmark for the Rusty compiler over the official IR-1 suite.

Each testcase is compiled with rusty's own backend, run under qemu-riscv64, checked
for correctness (AC/WA), and measured by a chosen metric:

  * insns - dynamic instruction count via the qemu TCG plugin (the OJ-relevant number);
  * time  - wall-clock runtime, warmed up and repeated for a stable median.

Optionally, LLVM reference backends are built from rusty's *own* emitted IR (clang/llc)
to expose the optimization gap. gcc is used only as the rv64 assembler/linker -- it
cannot consume rusty's LLVM IR, so it is never a reference compiler here.

This single tool replaces the former branch_check.sh / ir_headroom.sh / full_compare.sh
(insn-count + reference comparison) and profile_ir1_rv64.py (wall-clock profiling).

Examples:
  scripts/benchmark.py                                  # rusty-only insn counts, all cases
  scripts/benchmark.py --refs all                       # + llc-O2 / clang-O2 / clang-O3 gap
  scripts/benchmark.py --case comprehensive1 --refs llc,clang-O2
  scripts/benchmark.py --metric time --report-dir build/bench
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import shutil
import subprocess
import time
from dataclasses import dataclass, field
from pathlib import Path
from statistics import median


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SUITE = REPO_ROOT / "src/test/resources/@official/IR-1"
DEFAULT_WORK = REPO_ROOT / "build/benchmark"
PRELUDE_DIR = REPO_ROOT / "src/main/kotlin/rusty/ir/prelude"
DEFAULT_PLUGIN = REPO_ROOT / "ref/scripts/insn_count.so"
CLASSPATH = REPO_ROOT / "build/classes"

RV64_ARCH = "rv64gc"
RV64_ABI = "lp64d"
LLC_MATTR = "+m,+a,+f,+d,+c"

# Shared link flags. `--build-id=none` strips the random GNU build-id note: the system gcc
# otherwise stamps a fresh id into every static binary, perturbing glibc startup by a few
# hundred instructions and making counts non-reproducible. The optimization level is appended
# per call (rusty asm links at -O0, reference objects at -O2).
LINK_FLAGS = [f"-march={RV64_ARCH}", f"-mabi={RV64_ABI}", "-static", "-Wl,--build-id=none"]

# Known-flawed in the upstream submodule; excluded from every run.
SKIP_CASES = {"overflow"}

# Reference backends that can be built from rusty's emitted IR. Each entry knows how to
# turn opt.ll into a relocatable object/asm; linking is shared (see build_reference).
#   key -> (column label, clang -O level or None, use_llc)
REFERENCES: dict[str, tuple[str, str | None, bool]] = {
    "llc": ("llc-O2", None, True),
    "clang-O0": ("clang-O0", "-O0", False),
    "clang-O1": ("clang-O1", "-O1", False),
    "clang-O2": ("clang-O2", "-O2", False),
    "clang-O3": ("clang-O3", "-O3", False),
}
REFS_ALL = ["llc", "clang-O2", "clang-O3"]


@dataclass(frozen=True)
class Case:
    name: str
    source: Path
    stdin: Path | None
    expected_output: Path | None


@dataclass
class Tools:
    gcc: str
    clang: str
    llc: str
    qemu: str
    plugin: Path
    target: str
    sysroot: str | None
    prelude_c_obj: Path
    prelude_ll_obj: Path


@dataclass
class Row:
    case: str
    status: str  # AC / WA / COMPILE_FAIL / LINK_FAIL / ERROR
    # metric value per backend key ("rusty" plus any reference keys); None if unbuilt.
    values: dict[str, float | None] = field(default_factory=dict)
    message: str = ""


# --------------------------------------------------------------------------- args


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="Benchmark the Rusty compiler over the official IR-1 suite.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    p.add_argument("--suite", type=Path, default=DEFAULT_SUITE, help="IR-1 suite root (has global.json).")
    p.add_argument("--case", action="append", help="Run only this case. May be repeated.")
    p.add_argument("--metric", choices=["insns", "time"], default="insns", help="What to measure (default: insns).")
    p.add_argument(
        "--refs",
        default="none",
        help="Reference backends to also build, comma-separated: "
        "none | all | any of llc,clang-O0,clang-O1,clang-O2,clang-O3 (default: none).",
    )
    p.add_argument("--runs", type=int, default=5, help="Measured qemu runs per case (time metric).")
    p.add_argument("--warmups", type=int, default=1, help="Untimed warmup runs (time metric).")
    p.add_argument("--timeout", type=float, default=60.0, help="Per-run qemu timeout in seconds.")
    p.add_argument("--work-dir", type=Path, default=DEFAULT_WORK, help="Scratch dir for artifacts.")
    p.add_argument("--report-dir", type=Path, help="If set, also write report.csv + report.md here.")
    p.add_argument("--qemu", default=os.environ.get("QEMU_RISCV64", "qemu-riscv64"), help="qemu-riscv64 binary.")
    p.add_argument("--plugin", type=Path, default=DEFAULT_PLUGIN, help="qemu insn-count TCG plugin (.so).")
    p.add_argument("--target", default=os.environ.get("QEMU_GCC_TARGET", "riscv64-linux-gnu"), help="GCC target prefix.")
    p.add_argument("--gcc", default=os.environ.get("GCC_RISCV64"), help="RISC-V gcc. Defaults to <target>-gcc.")
    p.add_argument("--clang", default=os.environ.get("CLANG", "clang"), help="clang binary.")
    p.add_argument("--llc", default=os.environ.get("LLC", "llc"), help="llc binary.")
    p.add_argument("--sysroot", default=os.environ.get("QEMU_SYSROOT"), help="RISC-V sysroot for qemu -L.")
    return p.parse_args()


def parse_refs(spec: str) -> list[str]:
    spec = spec.strip()
    if spec.lower() in ("", "none"):
        return []
    if spec.lower() == "all":
        return list(REFS_ALL)
    # Match tokens case-insensitively but resolve to the canonical key (e.g. clang-O2).
    canonical = {k.lower(): k for k in REFERENCES}
    keys: list[str] = []
    for tok in spec.split(","):
        tok = tok.strip().lower()
        if not tok:
            continue
        if tok not in canonical:
            raise SystemExit(f"Unknown reference '{tok}'. Choose from: none, all, {', '.join(REFERENCES)}")
        keys.append(canonical[tok])
    return keys


# --------------------------------------------------------------------- discovery


def require_tool(binary: str) -> None:
    if shutil.which(binary) is None:
        raise SystemExit(f"Required tool not found on PATH: {binary}")


def discover_sysroot(gcc: str) -> str | None:
    if shutil.which(gcc) is None:
        return None
    result = run([gcc, "-print-sysroot"])
    value = decode(result.stdout).strip()
    return value if value and value != "/" else None


def load_cases(suite: Path) -> list[Case]:
    """Parse the suite's global.json into the active, non-skipped cases (natural-sorted)."""
    with (suite / "global.json").open(encoding="utf-8") as fh:
        records = json.load(fh)

    cases: list[Case] = []
    for record in records:
        if not record.get("active", True):
            continue
        name = record["name"]
        if name in SKIP_CASES:
            continue
        source = suite / first_path(record.get("source"), f"src/{name}/{name}.rx")
        if not source.exists():
            continue
        in_path = suite / first_path(record.get("input"), "") if record.get("input") else None
        out_path = suite / first_path(record.get("output"), "") if record.get("output") else None
        cases.append(
            Case(
                name=name,
                source=source,
                stdin=in_path if in_path and in_path.exists() else None,
                expected_output=out_path if out_path and out_path.exists() else None,
            )
        )
    return sorted(cases, key=lambda c: natural_key(c.name))


def first_path(value: object, default: str) -> str:
    if isinstance(value, list) and value:
        return str(value[0])
    if isinstance(value, str):
        return value
    return default


def natural_key(name: str) -> tuple[str, int]:
    prefix = name.rstrip("0123456789")
    suffix = name[len(prefix):]
    return (prefix, int(suffix) if suffix else -1)


# ---------------------------------------------------------------------- process


def run(cmd: list[str], *, stdin: bytes | None = None, timeout: float | None = None):
    """Run a command capturing stdout/stderr separately; never raises on non-zero exit."""
    try:
        return subprocess.run(
            cmd, input=stdin, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            cwd=REPO_ROOT, timeout=timeout, check=False,
        )
    except subprocess.TimeoutExpired as exc:
        raise RuntimeError(f"timed out after {timeout}s: {' '.join(cmd)}") from exc


def decode(data: bytes | None) -> str:
    return (data or b"").decode("utf-8", errors="replace")


def normalize(text: str) -> str:
    """OJ-style comparison: ignore trailing newline differences."""
    return text.replace("\r\n", "\n").rstrip("\n\r")


# ------------------------------------------------------------------- compilation


def kotlin_cmd(*extra: str) -> list[str]:
    return ["kotlin", "-J-XX:-UsePerfData", "-cp", str(CLASSPATH), "rusty.MainKt", *extra]


def build_prelude(t: Tools, work: Path) -> None:
    """Compile the C and LLVM-IR preludes once; reference ELFs link against these."""
    run([t.gcc, f"-march={RV64_ARCH}", f"-mabi={RV64_ABI}", "-O2", "-c",
         str(PRELUDE_DIR / "prelude.c"), "-o", str(t.prelude_c_obj)])
    run([t.clang, f"--target={t.target}", f"-march={RV64_ARCH}", f"-mabi={RV64_ABI}", "-O2", "-c",
         str(PRELUDE_DIR / "prelude.ll"), "-o", str(t.prelude_ll_obj)])


def build_rusty(case: Case, t: Tools, work: Path) -> tuple[Path | None, str]:
    """Compile a case through rusty's own backend. Returns (elf, status)."""
    user_s, builtin_s = work / "rusty.user.s", work / "rusty.builtin.s"
    src = case.source.read_bytes()
    res = run(kotlin_cmd("--stdio-asm"), stdin=src)
    if res.returncode != 0:
        return None, "COMPILE_FAIL"
    user_s.write_bytes(res.stdout)
    builtin_s.write_bytes(res.stderr or b"")

    elf = work / "rusty.elf"
    link = run([t.gcc, *LINK_FLAGS, "-O0", str(user_s), str(builtin_s), "-o", str(elf)])
    if link.returncode != 0:
        return None, "LINK_FAIL"
    return elf, "OK"


def emit_opt_ir(case: Case, work: Path) -> Path | None:
    """Emit rusty's optimized LLVM IR for the reference backends."""
    opt_ll = work / "opt.ll"
    res = run(kotlin_cmd("--emit", "opt", "-i", str(case.source), "-o", str(opt_ll)))
    return opt_ll if res.returncode == 0 and opt_ll.exists() else None


def build_reference(key: str, opt_ll: Path, t: Tools, work: Path) -> Path | None:
    """Build a reference ELF (llc or clang at some -O level) from rusty's IR."""
    _, clang_opt, use_llc = REFERENCES[key]
    elf = work / f"ref_{key}.elf"
    obj = work / f"ref_{key}.o"
    if use_llc:
        asm = work / f"ref_{key}.s"
        r = run([t.llc, "-O2", f"-mtriple={t.target}", f"-mattr={LLC_MATTR}", str(opt_ll), "-o", str(asm)])
        if r.returncode != 0:
            return None
        link_input = str(asm)
    else:
        r = run([t.clang, f"--target={t.target}", f"-march={RV64_ARCH}", f"-mabi={RV64_ABI}",
                 clang_opt, "-c", str(opt_ll), "-o", str(obj)])
        if r.returncode != 0:
            return None
        link_input = str(obj)
    link = run([t.gcc, *LINK_FLAGS, "-O2",
                link_input, str(t.prelude_ll_obj), str(t.prelude_c_obj), "-o", str(elf)])
    return elf if link.returncode == 0 else None


# ------------------------------------------------------------------- measurement


_INSNS_RE = re.compile(r"^INSNS\s+(\d+)", re.MULTILINE)


def qemu_base_cmd(t: Tools) -> list[str]:
    cmd = [t.qemu]
    if t.sysroot:
        cmd += ["-L", t.sysroot]
    return cmd


def check_output(case: Case, t: Tools, elf: Path, stdin: bytes, timeout: float) -> bool:
    """True if the program's stdout matches the expected output (AC)."""
    res = run([*qemu_base_cmd(t), str(elf)], stdin=stdin, timeout=timeout)
    if case.expected_output is None:
        return True
    return normalize(decode(res.stdout)) == normalize(case.expected_output.read_text(encoding="utf-8"))


def measure_insns(t: Tools, elf: Path, stdin: bytes, timeout: float) -> float | None:
    res = run([*qemu_base_cmd(t), "-plugin", str(t.plugin), str(elf)], stdin=stdin, timeout=timeout)
    m = _INSNS_RE.search(decode(res.stderr))
    return float(m.group(1)) if m else None


def measure_time(t: Tools, elf: Path, stdin: bytes, runs: int, warmups: int, timeout: float) -> float | None:
    cmd = [*qemu_base_cmd(t), str(elf)]
    for _ in range(warmups):
        run(cmd, stdin=stdin, timeout=timeout)
    samples: list[float] = []
    for _ in range(runs):
        start = time.perf_counter_ns()
        run(cmd, stdin=stdin, timeout=timeout)
        samples.append((time.perf_counter_ns() - start) / 1_000_000.0)
    return median(samples) if samples else None


def measure(elf: Path, case: Case, t: Tools, args: argparse.Namespace) -> float | None:
    stdin = case.stdin.read_bytes() if case.stdin else b""
    if args.metric == "insns":
        return measure_insns(t, elf, stdin, args.timeout)
    return measure_time(t, elf, stdin, args.runs, args.warmups, args.timeout)


# ------------------------------------------------------------------------ driver


def run_case(case: Case, refs: list[str], t: Tools, work: Path, args: argparse.Namespace) -> Row:
    row = Row(case=case.name, status="ERROR")
    try:
        elf, status = build_rusty(case, t, work)
        if elf is None:
            row.status = status
            return row

        stdin = case.stdin.read_bytes() if case.stdin else b""
        row.status = "AC" if check_output(case, t, elf, stdin, args.timeout) else "WA"
        row.values["rusty"] = measure(elf, case, t, args)

        if refs:
            opt_ll = emit_opt_ir(case, work)
            for key in refs:
                ref_elf = build_reference(key, opt_ll, t, work) if opt_ll else None
                row.values[key] = measure(ref_elf, case, t, args) if ref_elf else None
        return row
    except RuntimeError as exc:
        row.status = "ERROR"
        row.message = str(exc)
        return row


# ------------------------------------------------------------------------ output


def fmt(value: float | None, metric: str) -> str:
    if value is None:
        return "-"
    return f"{value:.0f}" if metric == "insns" else f"{value:.3f}"


def column_keys(refs: list[str]) -> list[str]:
    return ["rusty", *refs]


def header_label(key: str) -> str:
    return "rusty" if key == "rusty" else REFERENCES[key][0]


def gap_columns(refs: list[str]) -> bool:
    """Backend/IR gap decomposition needs both llc and clang-O2 alongside rusty."""
    return "llc" in refs and "clang-O2" in refs


def print_header(refs: list[str], metric: str) -> None:
    keys = column_keys(refs)
    head = [f"{'case':<18}", f"{'st':<4}"] + [f"{header_label(k):>12}" for k in keys]
    if metric == "insns" and gap_columns(refs):
        head += [f"{'bk_gap':>8}", f"{'ir_gap':>8}", f"{'total':>8}"]
    line = " ".join(head)
    print(line)
    print("-" * len(line))


def print_row(r: Row, refs: list[str], metric: str) -> None:
    keys = column_keys(refs)
    cells = [f"{r.case:<18}", f"{r.status:<4}"] + [f"{fmt(r.values.get(k), metric):>12}" for k in keys]
    if metric == "insns" and gap_columns(refs):
        cells += gap_cells(r)
    suffix = f"  {r.message}" if r.message else ""
    print(" ".join(cells) + suffix, flush=True)


def ratio(num: float | None, den: float | None) -> str:
    if not num or not den:
        return "-"
    return f"{num / den:.2f}x"


def gap_cells(r: Row) -> list[str]:
    rusty, llc, c2 = r.values.get("rusty"), r.values.get("llc"), r.values.get("clang-O2")
    return [f"{ratio(rusty, llc):>8}", f"{ratio(llc, c2):>8}", f"{ratio(rusty, c2):>8}"]


def print_summary(rows: list[Row], refs: list[str], metric: str) -> None:
    ac = [r for r in rows if r.status == "AC"]
    print()
    print(f"=== Summary (AC: {len(ac)}/{len(rows)}) ===")
    if not ac:
        return
    keys = column_keys(refs)
    totals = {k: sum(r.values.get(k) or 0.0 for r in ac if r.values.get(k) is not None) for k in keys}
    unit = "insns" if metric == "insns" else "ms"
    parts = [f"{header_label(k)}={fmt(totals[k], metric)}" for k in keys]
    print(f"Total {unit}: " + "  ".join(parts))
    if metric == "insns" and gap_columns(refs):
        rusty, llc, c2 = totals["rusty"], totals["llc"], totals["clang-O2"]
        print(f"Aggregate:   backend_gap={ratio(rusty, llc)}  ir_gap={ratio(llc, c2)}  total_gap={ratio(rusty, c2)}")


def write_reports(rows: list[Row], refs: list[str], metric: str, report_dir: Path) -> None:
    report_dir.mkdir(parents=True, exist_ok=True)
    keys = column_keys(refs)

    csv_path = report_dir / "report.csv"
    with csv_path.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.writer(fh)
        writer.writerow(["case", "status", *(header_label(k) for k in keys), "message"])
        for r in rows:
            writer.writerow([r.case, r.status, *(fmt(r.values.get(k), metric) for k in keys), r.message])

    md = [f"# Rusty IR-1 benchmark ({metric})", ""]
    md.append("| case | status | " + " | ".join(header_label(k) for k in keys) + " |")
    md.append("| --- | --- | " + " | ".join("---:" for _ in keys) + " |")
    for r in rows:
        md.append("| " + " | ".join([r.case, r.status, *(fmt(r.values.get(k), metric) for k in keys)]) + " |")
    (report_dir / "report.md").write_text("\n".join(md) + "\n", encoding="utf-8")
    print(f"\nWrote {csv_path} and {report_dir / 'report.md'}")


# -------------------------------------------------------------------------- main


def main() -> int:
    args = parse_args()
    refs = parse_refs(args.refs)

    suite = args.suite.resolve()
    work = args.work_dir.resolve()
    work.mkdir(parents=True, exist_ok=True)

    if not CLASSPATH.exists():
        raise SystemExit(f"{CLASSPATH} missing -- run `make build` first.")

    gcc = args.gcc or f"{args.target}-gcc"
    require_tool(gcc)
    require_tool(args.qemu)
    if args.metric == "insns" and not args.plugin.exists():
        raise SystemExit(f"qemu plugin not found: {args.plugin}")
    if refs:
        require_tool(args.clang)
        if any(REFERENCES[k][2] for k in refs):
            require_tool(args.llc)

    t = Tools(
        gcc=gcc, clang=args.clang, llc=args.llc, qemu=args.qemu, plugin=args.plugin.resolve(),
        target=args.target, sysroot=args.sysroot or discover_sysroot(gcc),
        prelude_c_obj=work / "prelude_c.o", prelude_ll_obj=work / "prelude_ll.o",
    )
    if refs:
        build_prelude(t, work)

    cases = load_cases(suite)
    if args.case:
        wanted = set(args.case)
        cases = [c for c in cases if c.name in wanted]
        missing = sorted(wanted - {c.name for c in cases})
        if missing:
            raise SystemExit(f"Unknown or inactive case(s): {', '.join(missing)}")
    if not cases:
        raise SystemExit(f"No active cases found in {suite}")

    print_header(refs, args.metric)
    rows: list[Row] = []
    for case in cases:
        row = run_case(case, refs, t, work, args)
        rows.append(row)
        print_row(row, refs, args.metric)

    print_summary(rows, refs, args.metric)
    if args.report_dir:
        write_reports(rows, refs, args.metric, args.report_dir.resolve())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
