#!/usr/bin/env python3
"""Profile official IR-1 testcase runtime on rv64/qemu.

The script intentionally excludes compiler and linker time from measurements:
assembly artifacts are emitted with ``make run`` and linked once per testcase,
then only repeated qemu executions are timed.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import shutil
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from statistics import median


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SUITE = REPO_ROOT / "src/test/resources/@official/IR-1"
DEFAULT_OUT = REPO_ROOT / "build/ir1-rv64-profile"
RV64_ARCH = "rv64gc"
RV64_ABI = "lp64d"
PROFILE_MODE = "asm"


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
        description="Profile official IR-1 rv64/qemu runtime, excluding make/link time."
    )
    parser.add_argument("--suite", type=Path, default=DEFAULT_SUITE, help="Official IR-1 suite root.")
    parser.add_argument("--out-dir", type=Path, default=DEFAULT_OUT, help="Artifact and report directory.")
    parser.add_argument("--case", action="append", help="Case name filter. May be repeated.")
    parser.add_argument("--runs", type=int, default=5, help="Measured qemu runs per testcase.")
    parser.add_argument("--warmups", type=int, default=1, help="Untimed qemu runs before measurements.")
    parser.add_argument("--timeout", type=float, default=30.0, help="Timeout in seconds for each qemu run.")
    parser.add_argument("--qemu", default=os.environ.get("QEMU_RISCV64", "qemu-riscv64"), help="qemu-riscv64 binary.")
    parser.add_argument(
        "--target",
        default=os.environ.get("QEMU_GCC_TARGET") or os.environ.get("QEMU_CLANG_TARGET", "riscv64-linux-gnu"),
        help="GCC target prefix used to derive <target>-gcc.",
    )
    parser.add_argument("--gcc", default=os.environ.get("GCC_RISCV64"), help="RISC-V gcc binary. Defaults to <target>-gcc.")
    parser.add_argument("--sysroot", default=os.environ.get("QEMU_SYSROOT"), help="RISC-V sysroot for qemu.")
    parser.add_argument("--qemu-arg", action="append", default=[], help="Extra qemu argument. May be repeated.")
    parser.add_argument(
        "--gcc-arg",
        action="append",
        default=[],
        help="Extra gcc link argument. May be repeated; defaults already include --gcc-opt.",
    )
    parser.add_argument(
        "--gcc-opt",
        default="-O2",
        help="GCC optimization level for testcase linking. Use '' to disable.",
    )
    parser.add_argument("--no-make-build", action="store_true", help="Do not run make build before profiling.")
    parser.add_argument(
        "--keep-going",
        action="store_true",
        help="Continue after compile/link/run failures and include failures in the table.",
    )
    parser.add_argument(
        "--skip-existing",
        action="store_true",
        help="Reuse existing .s/.out artifacts when present.",
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

    gcc = args.gcc or f"{args.target}-gcc"
    require_tool(gcc)
    require_tool(args.qemu)
    sysroot = args.sysroot or discover_sysroot(gcc)

    if not args.no_make_build:
        print("Running `make build` ...")
        make_build_result = run_checked(["make", "build"])
        if make_build_result.returncode != 0:
            raise SystemExit("make build failed:\n" + decode(make_build_result.stdout))

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

    gcc_args = build_gcc_args(args.gcc_opt, args.gcc_arg)
    rows: list[ProfileRow] = []

    print(f"Profiling {len(cases)} IR-1 cases through make run")
    print(f"GCC:      {gcc}")
    print(f"Reports:  {reports_dir}")

    for case in cases:
        row = profile_case(
            case=case,
            gcc=gcc,
            sysroot=sysroot,
            gcc_args=gcc_args,
            qemu=args.qemu,
            qemu_args=args.qemu_arg,
            artifacts_dir=artifacts_dir,
            runs=args.runs,
            warmups=args.warmups,
            timeout=args.timeout,
            skip_existing=args.skip_existing,
        )
        rows.append(row)
        print(format_progress(row), flush=True)
        if row.status != "ok" and not args.keep_going:
            write_reports(rows, reports_dir)
            return 1

    write_reports(rows, reports_dir)
    failed = [row for row in rows if row.status != "ok"]
    if failed:
        print(f"Completed with {len(failed)} failing row(s). See {reports_dir / 'profile.md'}.")
        return 1

    print(f"Wrote {reports_dir / 'profile.md'} and {reports_dir / 'profile.csv'}")
    return 0


def require_tool(binary: str) -> None:
    if shutil.which(binary) is None:
        raise SystemExit(f"Required tool not found on PATH: {binary}")


def run_checked(
    cmd: list[str],
    *,
    stdin: bytes | None = None,
    timeout: float | None = None,
    stderr_to_stdout: bool = True,
) -> subprocess.CompletedProcess[bytes]:
    try:
        return subprocess.run(
            cmd,
            input=stdin,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT if stderr_to_stdout else subprocess.PIPE,
            cwd=REPO_ROOT,
            timeout=timeout,
            check=False,
        )
    except subprocess.TimeoutExpired as exc:
        output = exc.stdout or b""
        raise RuntimeError(f"Command timed out after {timeout}s: {' '.join(cmd)}\n{decode(output)}") from exc


def discover_sysroot(gcc: str) -> str | None:
    if shutil.which(gcc) is None:
        return None
    result = run_checked([gcc, "-print-sysroot"])
    if result.returncode != 0:
        return None
    value = decode(result.stdout).strip()
    return value if value and value != "/" else None


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


def build_gcc_args(gcc_opt: str, extra_args: list[str]) -> list[str]:
    return ([gcc_opt] if gcc_opt else []) + extra_args


def profile_case(
    *,
    case: Case,
    gcc: str,
    sysroot: str | None,
    gcc_args: list[str],
    qemu: str,
    qemu_args: list[str],
    artifacts_dir: Path,
    runs: int,
    warmups: int,
    timeout: float,
    skip_existing: bool,
) -> ProfileRow:
    case_dir = artifacts_dir / case.name
    case_dir.mkdir(parents=True, exist_ok=True)
    user_asm_output = case_dir / f"{case.name}.user.s"
    builtin_asm_output = case_dir / f"{case.name}.builtin.s"
    exe_output = case_dir / f"{case.name}.out"

    try:
        if not skip_existing or not (user_asm_output.exists() and builtin_asm_output.exists()):
            compile_result = run_checked(
                ["make", "run"],
                stdin=case.source.read_bytes(),
                stderr_to_stdout=False,
            )
            if compile_result.returncode != case.expected_compile_exit:
                return ProfileRow(
                    case=case.name,
                    mode=PROFILE_MODE,
                    status="compile-failed",
                    message=truncate(combined_output(compile_result)),
                )
            if case.expected_compile_exit != 0:
                return ProfileRow(case=case.name, mode=PROFILE_MODE, status="expected-compile-failure")
            user_asm_output.write_bytes(compile_result.stdout)
            builtin_asm_output.write_bytes(compile_result.stderr or b"")

        if not skip_existing or not exe_output.exists():
            link_cmd = [
                gcc,
                f"-march={RV64_ARCH}",
                f"-mabi={RV64_ABI}",
            ]
            link_cmd += gcc_args
            link_cmd += [str(user_asm_output), str(builtin_asm_output), "-o", str(exe_output)]
            link_result = run_checked(link_cmd)
            if link_result.returncode != 0:
                return ProfileRow(
                    case=case.name,
                    mode=PROFILE_MODE,
                    status="link-failed",
                    message=truncate(decode(link_result.stdout)),
                )

        stdin_bytes = case.stdin.read_bytes() if case.stdin else b""
        qemu_cmd = build_qemu_cmd(qemu, qemu_args, sysroot, exe_output)

        for _ in range(warmups):
            run_result = run_checked(qemu_cmd, stdin=stdin_bytes, timeout=timeout)
            validation_error = validate_run(case, run_result)
            if validation_error:
                return ProfileRow(case=case.name, mode=PROFILE_MODE, status="wrong-answer", message=validation_error)

        timings: list[float] = []
        for _ in range(runs):
            start = time.perf_counter_ns()
            run_result = run_checked(qemu_cmd, stdin=stdin_bytes, timeout=timeout)
            elapsed_ms = (time.perf_counter_ns() - start) / 1_000_000.0
            validation_error = validate_run(case, run_result)
            if validation_error:
                return ProfileRow(case=case.name, mode=PROFILE_MODE, status="wrong-answer", message=validation_error)
            timings.append(elapsed_ms)

        return ProfileRow(
            case=case.name,
            mode=PROFILE_MODE,
            status="ok",
            min_ms=min(timings),
            median_ms=median(timings),
            mean_ms=sum(timings) / len(timings),
            max_ms=max(timings),
            runs=len(timings),
            binary_bytes=exe_output.stat().st_size,
        )
    except RuntimeError as exc:
        return ProfileRow(case=case.name, mode=PROFILE_MODE, status="timeout/error", message=truncate(str(exc)))


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


def write_reports(rows: list[ProfileRow], reports_dir: Path) -> None:
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
    md_path.write_text(build_markdown(rows), encoding="utf-8")


def build_markdown(rows: list[ProfileRow]) -> str:
    lines = [
        "# Official IR-1 rv64/qemu Runtime Profile",
        "",
        "Measurements time only testcase execution under qemu. `make run` assembly emission and gcc link time are excluded.",
        "",
    ]
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


def combined_output(result: subprocess.CompletedProcess[bytes]) -> str:
    return decode(result.stdout or b"") + decode(result.stderr or b"")


if __name__ == "__main__":
    raise SystemExit(main())
