#!/usr/bin/env python3
"""Profile official IR-1 testcase runtime on rv64/qemu.

The script intentionally excludes compiler and linker time from measurements:
artifacts are emitted and linked once per testcase/mode, then only repeated
qemu executions are timed.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import shutil
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from statistics import median
from typing import Iterable


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SUITE = REPO_ROOT / "src/test/resources/@official/IR-1"
DEFAULT_OUT = REPO_ROOT / "build/ir1-rv64-profile"
PRELUDE_DIR = REPO_ROOT / "src/main/kotlin/rusty/ir/prelude"
PRELUDE_LL = PRELUDE_DIR / "prelude.ll"
PRELUDE_C = PRELUDE_DIR / "prelude.c"
RV64_ARCH = "rv64gc"
RV64_ABI = "lp64d"


@dataclass(frozen=True)
class Case:
    name: str
    source: Path
    stdin: Path | None
    expected_output: Path | None
    expected_compile_exit: int
    expected_run_exit: int


@dataclass
class ProfileRow:
    case: str
    mode: str
    status: str
    min_ms: float | None = None
    median_ms: float | None = None
    mean_ms: float | None = None
    max_ms: float | None = None
    runs: int = 0
    binary_bytes: int | None = None
    message: str = ""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Profile official IR-1 rv64/qemu runtime, excluding compiler/link time."
    )
    parser.add_argument("--suite", type=Path, default=DEFAULT_SUITE, help="Official IR-1 suite root.")
    parser.add_argument("--out-dir", type=Path, default=DEFAULT_OUT, help="Artifact and report directory.")
    parser.add_argument("--case", action="append", help="Case name filter. May be repeated.")
    parser.add_argument(
        "--mode",
        action="append",
        choices=("ir", "opt"),
        help="Compiler emit mode to profile. Defaults to both ir and opt.",
    )
    parser.add_argument("--runs", type=int, default=5, help="Measured qemu runs per testcase/mode.")
    parser.add_argument("--warmups", type=int, default=1, help="Untimed qemu runs before measurements.")
    parser.add_argument("--timeout", type=float, default=30.0, help="Timeout in seconds for each qemu run.")
    parser.add_argument("--clang", default=os.environ.get("CLANG", "clang"), help="Clang binary.")
    parser.add_argument("--qemu", default=os.environ.get("QEMU_RISCV64", "qemu-riscv64"), help="qemu-riscv64 binary.")
    parser.add_argument(
        "--target",
        default=os.environ.get("QEMU_CLANG_TARGET", "riscv64-linux-gnu"),
        help="Clang target triple and gcc prefix.",
    )
    parser.add_argument("--sysroot", default=os.environ.get("QEMU_SYSROOT"), help="RISC-V sysroot for clang/qemu.")
    parser.add_argument("--qemu-arg", action="append", default=[], help="Extra qemu argument. May be repeated.")
    parser.add_argument(
        "--clang-arg",
        action="append",
        default=[],
        help="Extra clang link argument. May be repeated; defaults already include --clang-opt.",
    )
    parser.add_argument(
        "--clang-opt",
        default="-O2",
        help="Clang optimization level for prelude C IR and testcase linking. Use '' to disable.",
    )
    parser.add_argument(
        "--compiler",
        type=Path,
        help="Compiler executable. Defaults to build/install/rusty/bin/rusty after installDist.",
    )
    parser.add_argument(
        "--no-install-dist",
        action="store_true",
        help="Do not run ./gradlew installDist before profiling.",
    )
    parser.add_argument(
        "--keep-going",
        action="store_true",
        help="Continue after compile/link/run failures and include failures in the table.",
    )
    parser.add_argument(
        "--skip-existing",
        action="store_true",
        help="Reuse existing .ll/.out artifacts when present.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.runs <= 0:
        raise SystemExit("--runs must be positive")
    if args.warmups < 0:
        raise SystemExit("--warmups must be non-negative")

    suite = args.suite.resolve()
    out_dir = args.out_dir.resolve()
    modes = args.mode or ["opt"]

    require_tool(args.clang)
    require_tool(args.qemu)
    sysroot = args.sysroot or discover_sysroot(args.target)

    compiler = resolve_compiler(args.compiler, args.no_install_dist)
    cases = load_cases(suite)
    if args.case:
        wanted = set(args.case)
        cases = [case for case in cases if case.name in wanted]
        missing = sorted(wanted - {case.name for case in cases})
        if missing:
            raise SystemExit(f"Unknown or inactive testcase(s): {', '.join(missing)}")
    if not cases:
        raise SystemExit(f"No active cases found in {suite}")

    reports_dir = out_dir / "reports"
    artifacts_dir = out_dir / "artifacts"
    reports_dir.mkdir(parents=True, exist_ok=True)
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    prelude_c_ll = ensure_prelude_c_ir(args.clang, args.target, sysroot, out_dir, args.clang_opt)
    clang_args = build_clang_args(args.clang_opt, args.clang_arg)
    rows: list[ProfileRow] = []

    print(f"Profiling {len(cases)} IR-1 cases x {len(modes)} mode(s)")
    print(f"Compiler: {compiler}")
    print(f"Reports:  {reports_dir}")

    for case in cases:
        for mode in modes:
            row = profile_case_mode(
                case=case,
                mode=mode,
                compiler=compiler,
                clang=args.clang,
                clang_target=args.target,
                sysroot=sysroot,
                clang_args=clang_args,
                qemu=args.qemu,
                qemu_args=args.qemu_arg,
                prelude_c_ll=prelude_c_ll,
                artifacts_dir=artifacts_dir,
                runs=args.runs,
                warmups=args.warmups,
                timeout=args.timeout,
                skip_existing=args.skip_existing,
            )
            rows.append(row)
            print(format_progress(row), flush=True)
            if row.status != "ok" and not args.keep_going:
                write_reports(rows, reports_dir, modes)
                return 1

    write_reports(rows, reports_dir, modes)
    failed = [row for row in rows if row.status != "ok"]
    if failed:
        print(f"Completed with {len(failed)} failing row(s). See {reports_dir / 'profile.md'}.")
        return 1

    print(f"Wrote {reports_dir / 'profile.md'} and {reports_dir / 'profile.csv'}")
    return 0


def require_tool(binary: str) -> None:
    if shutil.which(binary) is None:
        raise SystemExit(f"Required tool not found on PATH: {binary}")


def run_checked(cmd: list[str], *, stdin: bytes | None = None, timeout: float | None = None) -> subprocess.CompletedProcess[bytes]:
    try:
        return subprocess.run(
            cmd,
            input=stdin,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            cwd=REPO_ROOT,
            timeout=timeout,
            check=False,
        )
    except subprocess.TimeoutExpired as exc:
        output = exc.stdout or b""
        raise RuntimeError(f"Command timed out after {timeout}s: {' '.join(cmd)}\n{decode(output)}") from exc


def discover_sysroot(target: str) -> str | None:
    gcc = f"{target}-gcc"
    if shutil.which(gcc) is None:
        return None
    result = run_checked([gcc, "-print-sysroot"])
    if result.returncode != 0:
        return None
    value = decode(result.stdout).strip()
    return value if value and value != "/" else None


def resolve_compiler(explicit: Path | None, no_install_dist: bool) -> Path:
    if explicit is not None:
        compiler = explicit.resolve()
        if not compiler.exists():
            raise SystemExit(f"Compiler executable does not exist: {compiler}")
        return compiler

    compiler = REPO_ROOT / "build/install/rusty/bin/rusty"
    if compiler.exists() and no_install_dist:
        return compiler
    if not no_install_dist:
        result = run_checked([str(REPO_ROOT / "gradlew"), "installDist"])
        if result.returncode != 0:
            raise SystemExit("installDist failed:\n" + decode(result.stdout))
    if not compiler.exists():
        raise SystemExit(
            f"Compiler executable missing: {compiler}. Run ./gradlew installDist or pass --compiler."
        )
    return compiler


def load_cases(suite: Path) -> list[Case]:
    global_json = suite / "global.json"
    with global_json.open(encoding="utf-8") as fh:
        records = json.load(fh)

    cases: list[Case] = []
    for record in records:
        if not record.get("active", True):
            continue
        name = record["name"]
        source = suite / first_path(record.get("source"), f"src/{name}/{name}.rx")
        if not source.exists():
            continue
        input_path = suite / first_path(record.get("input"), "") if record.get("input") else None
        output_path = suite / first_path(record.get("output"), "") if record.get("output") else None
        cases.append(
            Case(
                name=name,
                source=source,
                stdin=input_path if input_path and input_path.exists() else None,
                expected_output=output_path if output_path and output_path.exists() else None,
                expected_compile_exit=int(record.get("compileexitcode", 0)),
                expected_run_exit=int(record.get("exitcode", 0)),
            )
        )
    return sorted(cases, key=lambda case: natural_case_key(case.name))


def first_path(value: object, default: str) -> str:
    if isinstance(value, list) and value:
        return str(value[0])
    if isinstance(value, str):
        return value
    return default


def natural_case_key(name: str) -> tuple[str, int]:
    prefix = name.rstrip("0123456789")
    suffix = name[len(prefix) :]
    return (prefix, int(suffix) if suffix else -1)


def ensure_prelude_c_ir(clang: str, target: str, sysroot: str | None, out_dir: Path, clang_opt: str) -> Path:
    opt_tag = sanitize_filename(clang_opt) if clang_opt else "O0"
    output = out_dir / "prelude" / f"prelude.c.{target}.{RV64_ARCH}.{RV64_ABI}.{opt_tag}.ll"
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists() and output.stat().st_mtime >= PRELUDE_C.stat().st_mtime:
        return output

    cmd = [
        clang,
        "-S",
        "-emit-llvm",
        f"--target={target}",
        f"-march={RV64_ARCH}",
        f"-mabi={RV64_ABI}",
    ]
    if clang_opt:
        cmd.append(clang_opt)
    if sysroot:
        cmd.append(f"--sysroot={sysroot}")
    cmd += [str(PRELUDE_C), "-o", str(output)]

    result = run_checked(cmd)
    if result.returncode != 0:
        raise SystemExit("Failed to build prelude C IR:\n" + decode(result.stdout))
    return output


def build_clang_args(clang_opt: str, extra_args: list[str]) -> list[str]:
    return ([clang_opt] if clang_opt else []) + extra_args


def sanitize_filename(value: str) -> str:
    sanitized = "".join(ch if ch.isalnum() or ch in "._-" else "_" for ch in value)
    return sanitized or "default"


def profile_case_mode(
    *,
    case: Case,
    mode: str,
    compiler: Path,
    clang: str,
    clang_target: str,
    sysroot: str | None,
    clang_args: list[str],
    qemu: str,
    qemu_args: list[str],
    prelude_c_ll: Path,
    artifacts_dir: Path,
    runs: int,
    warmups: int,
    timeout: float,
    skip_existing: bool,
) -> ProfileRow:
    case_dir = artifacts_dir / mode / case.name
    case_dir.mkdir(parents=True, exist_ok=True)
    ir_output = case_dir / f"{case.name}.{mode}.ll"
    exe_output = case_dir / f"{case.name}.{mode}.out"

    try:
        if not skip_existing or not ir_output.exists():
            compile_cmd = [
                str(compiler),
                "-i",
                str(case.source),
                "-o",
                str(ir_output),
                "--emit",
                mode,
            ]
            compile_result = run_checked(compile_cmd)
            if compile_result.returncode != case.expected_compile_exit:
                return ProfileRow(
                    case=case.name,
                    mode=mode,
                    status="compile-failed",
                    message=truncate(decode(compile_result.stdout)),
                )
            if case.expected_compile_exit != 0:
                return ProfileRow(case=case.name, mode=mode, status="expected-compile-failure")

        if not skip_existing or not exe_output.exists():
            link_cmd = [
                clang,
                f"--target={clang_target}",
                f"-march={RV64_ARCH}",
                f"-mabi={RV64_ABI}",
            ]
            if sysroot:
                link_cmd.append(f"--sysroot={sysroot}")
            link_cmd += clang_args
            link_cmd += [str(ir_output), str(PRELUDE_LL), str(prelude_c_ll), "-o", str(exe_output)]
            link_result = run_checked(link_cmd)
            if link_result.returncode != 0:
                return ProfileRow(
                    case=case.name,
                    mode=mode,
                    status="link-failed",
                    message=truncate(decode(link_result.stdout)),
                )

        stdin_bytes = case.stdin.read_bytes() if case.stdin else b""
        qemu_cmd = build_qemu_cmd(qemu, qemu_args, sysroot, exe_output)

        for _ in range(warmups):
            run_result = run_checked(qemu_cmd, stdin=stdin_bytes, timeout=timeout)
            validation_error = validate_run(case, run_result)
            if validation_error:
                return ProfileRow(case=case.name, mode=mode, status="wrong-answer", message=validation_error)

        timings: list[float] = []
        for _ in range(runs):
            start = time.perf_counter_ns()
            run_result = run_checked(qemu_cmd, stdin=stdin_bytes, timeout=timeout)
            elapsed_ms = (time.perf_counter_ns() - start) / 1_000_000.0
            validation_error = validate_run(case, run_result)
            if validation_error:
                return ProfileRow(case=case.name, mode=mode, status="wrong-answer", message=validation_error)
            timings.append(elapsed_ms)

        return ProfileRow(
            case=case.name,
            mode=mode,
            status="ok",
            min_ms=min(timings),
            median_ms=median(timings),
            mean_ms=sum(timings) / len(timings),
            max_ms=max(timings),
            runs=len(timings),
            binary_bytes=exe_output.stat().st_size,
        )
    except RuntimeError as exc:
        return ProfileRow(case=case.name, mode=mode, status="timeout/error", message=truncate(str(exc)))


def build_qemu_cmd(qemu: str, qemu_args: list[str], sysroot: str | None, exe: Path) -> list[str]:
    cmd = [qemu]
    if sysroot:
        cmd += ["-L", sysroot]
    cmd += qemu_args
    cmd.append(str(exe))
    return cmd


def validate_run(case: Case, result: subprocess.CompletedProcess[bytes]) -> str:
    output = decode(result.stdout)
    if result.returncode != case.expected_run_exit:
        return f"exit {result.returncode}, expected {case.expected_run_exit}; output: {truncate(output)}"
    if case.expected_output:
        expected = normalize(case.expected_output.read_text(encoding="utf-8"))
        actual = normalize(output)
        if actual != expected:
            return f"output mismatch; expected {len(expected)} chars, got {len(actual)} chars"
    return ""


def normalize(text: str) -> str:
    return text.replace("\r\n", "\n").rstrip("\n\r")


def write_reports(rows: list[ProfileRow], reports_dir: Path, modes: list[str]) -> None:
    reports_dir.mkdir(parents=True, exist_ok=True)
    csv_path = reports_dir / "profile.csv"
    with csv_path.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.DictWriter(
            fh,
            fieldnames=[
                "case",
                "mode",
                "status",
                "runs",
                "min_ms",
                "median_ms",
                "mean_ms",
                "max_ms",
                "binary_bytes",
                "message",
            ],
        )
        writer.writeheader()
        for row in rows:
            writer.writerow(
                {
                    "case": row.case,
                    "mode": row.mode,
                    "status": row.status,
                    "runs": row.runs,
                    "min_ms": number(row.min_ms),
                    "median_ms": number(row.median_ms),
                    "mean_ms": number(row.mean_ms),
                    "max_ms": number(row.max_ms),
                    "binary_bytes": row.binary_bytes or "",
                    "message": row.message,
                }
            )

    md_path = reports_dir / "profile.md"
    md_path.write_text(build_markdown(rows, modes), encoding="utf-8")


def build_markdown(rows: list[ProfileRow], modes: list[str]) -> str:
    lines = [
        "# Official IR-1 rv64/qemu Runtime Profile",
        "",
        "Measurements time only testcase execution under qemu. Compiler emission and clang link time are excluded.",
        "",
    ]
    lines.extend(build_comparison_table(rows, modes))
    lines.append("")
    lines.append("## Per-Mode Results")
    lines.append("")
    lines.append("| Case | Mode | Status | Runs | Min ms | Median ms | Mean ms | Max ms | Binary bytes |")
    lines.append("| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |")
    for row in rows:
        lines.append(
            "| {case} | {mode} | {status} | {runs} | {min_ms} | {median_ms} | {mean_ms} | {max_ms} | {binary_bytes} |".format(
                case=row.case,
                mode=row.mode,
                status=row.status if not row.message else f"{row.status}: {escape_pipe(row.message)}",
                runs=row.runs,
                min_ms=number(row.min_ms),
                median_ms=number(row.median_ms),
                mean_ms=number(row.mean_ms),
                max_ms=number(row.max_ms),
                binary_bytes=row.binary_bytes or "",
            )
        )
    lines.append("")
    lines.extend(build_summary(rows))
    lines.append("")
    return "\n".join(lines)


def build_comparison_table(rows: list[ProfileRow], modes: list[str]) -> list[str]:
    if set(modes) != {"ir", "opt"}:
        return []
    by_case: dict[str, dict[str, ProfileRow]] = {}
    for row in rows:
        by_case.setdefault(row.case, {})[row.mode] = row

    lines = [
        "## IR vs Opt",
        "",
        "| Case | IR median ms | Opt median ms | Speedup | Delta ms | Status |",
        "| --- | ---: | ---: | ---: | ---: | --- |",
    ]
    for case in sorted(by_case, key=natural_case_key):
        ir = by_case[case].get("ir")
        opt = by_case[case].get("opt")
        if not ir or not opt or ir.status != "ok" or opt.status != "ok" or not ir.median_ms or not opt.median_ms:
            status = ", ".join(filter(None, [ir.status if ir else "missing-ir", opt.status if opt else "missing-opt"]))
            lines.append(f"| {case} | {number(ir.median_ms if ir else None)} | {number(opt.median_ms if opt else None)} |  |  | {status} |")
            continue
        speedup = ir.median_ms / opt.median_ms
        delta = ir.median_ms - opt.median_ms
        lines.append(
            f"| {case} | {number(ir.median_ms)} | {number(opt.median_ms)} | {speedup:.3f}x | {delta:.3f} | ok |"
        )
    lines.append("")
    return lines


def build_summary(rows: list[ProfileRow]) -> list[str]:
    ok_rows = [row for row in rows if row.status == "ok" and row.median_ms is not None]
    failed = [row for row in rows if row.status != "ok"]
    lines = ["## Summary", ""]
    lines.append(f"- Successful rows: {len(ok_rows)}")
    lines.append(f"- Failed rows: {len(failed)}")

    by_mode: dict[str, list[ProfileRow]] = {}
    for row in ok_rows:
        by_mode.setdefault(row.mode, []).append(row)
    for mode in sorted(by_mode):
        total = sum(row.median_ms or 0.0 for row in by_mode[mode])
        lines.append(f"- {mode} total median runtime: {total:.3f} ms")

    if {"ir", "opt"}.issubset(by_mode):
        pairs = []
        for ir in by_mode["ir"]:
            opt = next((row for row in by_mode["opt"] if row.case == ir.case), None)
            if opt and ir.median_ms and opt.median_ms:
                pairs.append((ir, opt))
        if pairs:
            improved = sum(1 for ir, opt in pairs if opt.median_ms < ir.median_ms)
            regressed = sum(1 for ir, opt in pairs if opt.median_ms > ir.median_ms)
            ir_total = sum(ir.median_ms or 0.0 for ir, _ in pairs)
            opt_total = sum(opt.median_ms or 0.0 for _, opt in pairs)
            lines.append(f"- Comparable cases: {len(pairs)}")
            lines.append(f"- Improved/regressed by median: {improved}/{regressed}")
            lines.append(f"- Aggregate median speedup: {ir_total / opt_total:.3f}x")
    return lines


def format_progress(row: ProfileRow) -> str:
    if row.status == "ok":
        return f"{row.case:<18} {row.mode:<3} median={row.median_ms:.3f} ms mean={row.mean_ms:.3f} ms"
    return f"{row.case:<18} {row.mode:<3} {row.status}: {row.message}"


def number(value: float | None) -> str:
    return "" if value is None else f"{value:.3f}"


def truncate(text: str, limit: int = 500) -> str:
    compact = " ".join(text.split())
    return compact if len(compact) <= limit else compact[: limit - 3] + "..."


def escape_pipe(text: str) -> str:
    return text.replace("|", "\\|")


def decode(data: bytes) -> str:
    return data.decode("utf-8", errors="replace")


if __name__ == "__main__":
    raise SystemExit(main())
