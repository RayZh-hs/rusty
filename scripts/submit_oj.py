#!/usr/bin/env python3
"""Submit this repository to the course OJ.

The script reads scripts/config.json by default. With --retry it resubmits every
30 seconds while the finished attempt is a clone failure or contains a JE
verdict. Other failures, such as build/codegen/opti failures, stop the loop.
"""

from __future__ import annotations

import argparse
import html
import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONFIG = ROOT / "scripts" / "config.json"
DEFAULT_RESULTS_DIR = ROOT / "build" / "oj-submissions"
PENDING_STATUSES = {
    "Pending",
    "Waiting",
    "Queued",
    "Running",
    "Judging",
    "Cloning",
    "Under Cloning",
    "In Build",
    "In Queue",
    "In Judge",
}


@dataclass(frozen=True)
class Attempt:
    attempt_id: int
    branch: str
    commit: str
    status: str
    detail_url: str


@dataclass(frozen=True)
class AttemptDetail:
    attempt_id: int
    html: str
    build_log: str
    page_text: str

    @property
    def clone_failed(self) -> bool:
        return "Clone repo failed" in self.build_log

    @property
    def has_je(self) -> bool:
        return bool(re.search(r">\s*JE\s*<|\bJudge Error\b", self.page_text, re.IGNORECASE))

    @property
    def score(self) -> str | None:
        match = re.search(r"\b(?:Opti\s+)?Score:\s*([0-9.]+\s*/\s*[0-9.]+)", self.page_text, re.IGNORECASE)
        return normalize_space(match.group(1)) if match else None


class OjClient:
    def __init__(self, base_url: str, session: str, timeout: int) -> None:
        self.base_url = base_url.rstrip("/") + "/"
        self.timeout = timeout
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor())
        self.cookie = f"session={session}"

    def get(self, path_or_url: str) -> str:
        return self._request("GET", path_or_url)

    def post(self, path_or_url: str, data: dict[str, str]) -> str:
        body = urllib.parse.urlencode(data).encode()
        return self._request("POST", path_or_url, body)

    def _request(self, method: str, path_or_url: str, body: bytes | None = None) -> str:
        url = urllib.parse.urljoin(self.base_url, path_or_url)
        request = urllib.request.Request(
            url,
            data=body,
            method=method,
            headers={
                "Cookie": self.cookie,
                "User-Agent": "rusty-oj-submit/1.0",
            },
        )
        if body is not None:
            request.add_header("Content-Type", "application/x-www-form-urlencoded")
        try:
            with self.opener.open(request, timeout=self.timeout) as response:
                return response.read().decode("utf-8", errors="replace")
        except urllib.error.HTTPError as exc:
            text = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"{method} {url} failed: HTTP {exc.code}\n{text}") from exc
        except urllib.error.URLError as exc:
            raise RuntimeError(f"{method} {url} failed: {exc.reason}") from exc

    def latest_attempts(self) -> list[Attempt]:
        return parse_attempts(self.get("/queue"), self.base_url)

    def submit(self, docker_image: str, branch: str) -> None:
        page = self.post(
            "/judge",
            {
                "select-docker-image": docker_image,
                "git-branch": branch,
            },
        )
        notice = parse_notice(page)
        if notice and "previous judge request is still running" in notice.lower():
            raise RuntimeError(f"OJ refused the submit: {notice}")

    def detail(self, attempt_id: int) -> AttemptDetail:
        page = self.get(f"/detail/{attempt_id}")
        return AttemptDetail(
            attempt_id=attempt_id,
            html=page,
            build_log=parse_build_log(page),
            page_text=html.unescape(strip_tags(page)),
        )


def parse_attempts(page: str, base_url: str) -> list[Attempt]:
    attempts: list[Attempt] = []
    for row in re.findall(r"<tr>(.*?)</tr>", page, flags=re.DOTALL | re.IGNORECASE):
        cells = re.findall(r"<td[^>]*>(.*?)</td>", row, flags=re.DOTALL | re.IGNORECASE)
        if len(cells) < 6:
            continue

        attempt_id_text = strip_tags(cells[0]).strip()
        if not attempt_id_text.isdigit():
            continue

        # /judge has columns: local index, time, branch, commit, status, detail.
        # /queue has columns: attempt id, user, time, branch, commit, status, detail.
        if len(cells) >= 7:
            branch_cell, commit_cell, status_cell, detail_cell = cells[3], cells[4], cells[5], cells[6]
        else:
            branch_cell, commit_cell, status_cell, detail_cell = cells[2], cells[3], cells[4], cells[5]

        detail_match = re.search(r'href=["\']([^"\']*/detail/\d+)["\']', detail_cell)
        if not detail_match:
            continue

        detail_url = urllib.parse.urljoin(base_url, html.unescape(detail_match.group(1)))
        detail_id = int(detail_url.rstrip("/").rsplit("/", 1)[1])
        attempts.append(
            Attempt(
                attempt_id=detail_id,
                branch=strip_tags(branch_cell).strip(),
                commit=strip_tags(commit_cell).strip(),
                status=strip_tags(status_cell).strip(),
                detail_url=detail_url,
            )
        )
    return attempts


def parse_build_log(page: str) -> str:
    match = re.search(r"<textarea[^>]*>(.*?)</textarea>", page, flags=re.DOTALL | re.IGNORECASE)
    return html.unescape(match.group(1)).strip() if match else ""


def parse_notice(page: str) -> str:
    match = re.search(r"<small[^>]*>(.*?)</small>", page, flags=re.DOTALL | re.IGNORECASE)
    return strip_tags(match.group(1)).strip() if match else ""


def strip_tags(fragment: str) -> str:
    return html.unescape(re.sub(r"<[^>]+>", " ", fragment)).replace("\xa0", " ")


def normalize_space(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def is_pending_status(status: str) -> bool:
    normalized = status.strip()
    words = set(re.findall(r"[A-Za-z]+", normalized))
    return normalized in PENDING_STATUSES or normalized.startswith("In ") or bool(words & PENDING_STATUSES)


def phase_for_status(status: str) -> str | None:
    normalized = status.lower()
    if "semantic" in normalized:
        if re.search(r"(?:\b|[^\d])1(?:\b|[^\d])", normalized):
            return "Semantic 1"
        if re.search(r"(?:\b|[^\d])2(?:\b|[^\d])", normalized):
            return "Semantic 2"
    if "codegen" in normalized:
        return "Codegen"
    if "opti" in normalized:
        return "Opti"
    return None


def format_attempt_progress(page: str, status: str) -> str:
    phase = phase_for_status(status)
    if phase is None:
        return ""
    for row in parse_phase_summary(page):
        if row["phase"] == phase:
            return f"\t{row['pass']}+{row['fail']}/{row['total']}"
    return ""


def parse_phase_summary(page: str) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for row in re.findall(r"<tr>(.*?)</tr>", page, flags=re.DOTALL | re.IGNORECASE):
        header_match = re.search(r"<th[^>]*scope=[\"']row[\"'][^>]*>(.*?)</th>", row, flags=re.DOTALL | re.IGNORECASE)
        if not header_match:
            continue
        header = header_match.group(1)
        phase = normalize_space(re.sub(r"<span.*", "", header, flags=re.DOTALL | re.IGNORECASE))
        if phase not in {"Semantic 1", "Semantic 2", "Codegen", "Opti"}:
            continue
        verdict_match = re.search(r"<span[^>]*badge[^>]*>(.*?)</span>", header, flags=re.DOTALL | re.IGNORECASE)
        cells = [normalize_space(strip_tags(cell)) for cell in re.findall(r"<td[^>]*>(.*?)</td>", row, flags=re.DOTALL | re.IGNORECASE)]
        if len(cells) >= 3:
            rows.append(
                {
                    "phase": phase,
                    "verdict": normalize_space(strip_tags(verdict_match.group(1))) if verdict_match else "",
                    "pass": cells[0],
                    "fail": cells[1],
                    "total": cells[2],
                }
            )
    return rows


def parse_case_results(page: str) -> list[dict[str, str]]:
    cases: list[dict[str, str]] = []
    for phase in ("Semantic 1", "Semantic 2", "Codegen", "Opti"):
        title_pattern = re.escape(f'<h5 class="card-title">{phase}</h5>')
        match = re.search(title_pattern + r"(.*?)(?:<h5 class=\"card-title\">|</body>)", page, flags=re.DOTALL)
        if not match:
            continue
        block = match.group(1)
        for row in re.findall(r"<tr>(.*?)</tr>", block, flags=re.DOTALL | re.IGNORECASE):
            cells = re.findall(r"<(?:td|th)[^>]*>(.*?)</(?:td|th)>", row, flags=re.DOTALL | re.IGNORECASE)
            if len(cells) < 4:
                continue
            first = normalize_space(strip_tags(cells[0]))
            if not first.isdigit():
                continue
            verdict_match = re.search(r"<span[^>]*badge[^>]*>(.*?)</span>", cells[2], flags=re.DOTALL | re.IGNORECASE)
            verdict = normalize_space(strip_tags(verdict_match.group(1))) if verdict_match else normalize_space(strip_tags(cells[2]))
            result = {
                "phase": phase,
                "index": first,
                "case": normalize_space(strip_tags(cells[1])),
                "verdict": verdict,
            }
            if phase == "Opti":
                result["score"] = normalize_space(strip_tags(cells[3]))
                if len(cells) > 4:
                    result["reference"] = normalize_space(strip_tags(cells[4]))
                if len(cells) > 5:
                    result["baseline"] = normalize_space(strip_tags(cells[5]))
            else:
                result["time"] = normalize_space(strip_tags(cells[3]))
            cases.append(result)
    return cases


def archive_attempt(results_dir: Path, attempt: Attempt, detail: AttemptDetail) -> Path:
    attempt_dir = results_dir / str(attempt.attempt_id)
    attempt_dir.mkdir(parents=True, exist_ok=True)

    (attempt_dir / "detail.html").write_text(detail.html, encoding="utf-8")
    (attempt_dir / "build.log").write_text(detail.build_log + ("\n" if detail.build_log else ""), encoding="utf-8")

    summary = {
        "attempt_id": attempt.attempt_id,
        "branch": attempt.branch,
        "commit": attempt.commit,
        "status": attempt.status,
        "detail_url": attempt.detail_url,
        "score": detail.score,
        "clone_failed": detail.clone_failed,
        "has_je": detail.has_je,
        "phases": parse_phase_summary(detail.html),
    }
    (attempt_dir / "summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    cases = parse_case_results(detail.html)
    if cases:
        (attempt_dir / "cases.json").write_text(json.dumps(cases, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    return attempt_dir


def load_config(path: Path) -> dict[str, str]:
    with path.open(encoding="utf-8") as handle:
        config = json.load(handle)
    missing = [key for key in ("baseUrl", "session", "dockerImage", "branch") if not config.get(key)]
    if missing:
        raise SystemExit(f"{path} is missing required key(s): {', '.join(missing)}")
    return config


def git(args: list[str], *, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        cwd=ROOT,
        check=check,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )


def ensure_clean_local_branch(branch: str, skip_check: bool) -> None:
    current = git(["branch", "--show-current"]).stdout.strip()
    if current != branch:
        raise SystemExit(f"Current branch is {current!r}, but config branch is {branch!r}.")

    if skip_check:
        return

    status = git(["status", "--porcelain"]).stdout.strip()
    if status:
        raise SystemExit(
            "Working tree has uncommitted changes. Commit/stash them first, "
            "or pass --skip-clean-check if you intentionally want to submit the pushed branch as-is."
        )


def push_branch(branch: str, remote: str) -> None:
    print(f"Pushing {branch} to {remote}...", flush=True)
    result = git(["push", remote, f"{branch}:{branch}"], check=False)
    if result.returncode != 0:
        raise SystemExit(f"git push failed:\n{result.stderr.strip()}")


def wait_for_created_attempt(client: OjClient, branch: str, previous_max_id: int, poll_seconds: float) -> Attempt:
    while True:
        candidates = [
            attempt
            for attempt in client.latest_attempts()
            if attempt.branch == branch and attempt.attempt_id > previous_max_id
        ]
        if candidates:
            return max(candidates, key=lambda attempt: attempt.attempt_id)
        print("Waiting for OJ to create the attempt...", flush=True)
        time.sleep(poll_seconds)


def wait_for_terminal_attempt(client: OjClient, attempt_id: int, poll_seconds: float) -> Attempt:
    last_status: str | None = None
    last_progress: str | None = None
    while True:
        attempts = [attempt for attempt in client.latest_attempts() if attempt.attempt_id == attempt_id]
        if attempts:
            attempt = attempts[0]
            progress = ""
            try:
                progress = format_attempt_progress(client.detail(attempt_id).html, attempt.status)
            except RuntimeError:
                progress = ""
            if attempt.status != last_status or progress != last_progress:
                print(f"Attempt #{attempt_id}: {attempt.status}{progress}", flush=True)
                last_status = attempt.status
                last_progress = progress
            if not is_pending_status(attempt.status):
                return attempt
        else:
            print(f"Attempt #{attempt_id}: waiting for status row...", flush=True)
        time.sleep(poll_seconds)


def submit_once(
    client: OjClient,
    docker_image: str,
    branch: str,
    poll_seconds: float,
) -> tuple[Attempt, AttemptDetail]:
    previous_ids = [attempt.attempt_id for attempt in client.latest_attempts()]
    previous_max_id = max(previous_ids, default=0)

    print(f"Submitting branch {branch} with image {docker_image}...", flush=True)
    client.submit(docker_image, branch)
    created = wait_for_created_attempt(client, branch, previous_max_id, poll_seconds)
    print(f"Created attempt #{created.attempt_id}: {created.detail_url}", flush=True)

    finished = wait_for_terminal_attempt(client, created.attempt_id, poll_seconds)
    detail = client.detail(finished.attempt_id)
    return finished, detail


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG, help="Path to OJ config.json.")
    parser.add_argument("--retry", action="store_true", help="Retry while clone fails or verdict is JE.")
    parser.add_argument("--retry-interval", type=int, default=30, help="Seconds between retry submissions.")
    parser.add_argument("--poll-interval", type=float, default=5, help="Seconds between OJ status polls.")
    parser.add_argument("--timeout", type=int, default=30, help="HTTP timeout in seconds.")
    parser.add_argument("--results-dir", type=Path, default=DEFAULT_RESULTS_DIR, help="Directory for archived OJ results.")
    parser.add_argument("--push", action="store_true", help="Push the configured branch before the first submit.")
    parser.add_argument("--remote", default="origin", help="Git remote used by --push.")
    parser.add_argument("--skip-clean-check", action="store_true", help="Allow submitting while the worktree is dirty.")
    args = parser.parse_args()

    config = load_config(args.config)
    branch = config["branch"]
    docker_image = config["dockerImage"]

    ensure_clean_local_branch(branch, args.skip_clean_check)
    if args.push:
        push_branch(branch, args.remote)

    client = OjClient(config["baseUrl"], config["session"], args.timeout)

    while True:
        attempt, detail = submit_once(client, docker_image, branch, args.poll_interval)
        print(f"Finished attempt #{attempt.attempt_id}: {attempt.status}", flush=True)
        attempt_dir = archive_attempt(args.results_dir, attempt, detail)
        score_text = f"score: {detail.score}" if detail.score else "score: n/a"
        print(f"Saved result: {attempt_dir} ({score_text})", flush=True)

        if detail.clone_failed:
            print("Build log reports: Clone repo failed.", flush=True)
        if detail.has_je:
            print("Detail page contains a JE verdict.", flush=True)

        should_retry = detail.clone_failed or detail.has_je
        if not args.retry or not should_retry:
            return 75 if should_retry else 0

        print(f"Retrying in {args.retry_interval}s...", flush=True)
        time.sleep(args.retry_interval)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("\nInterrupted.", file=sys.stderr)
        raise SystemExit(130)
