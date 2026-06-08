#!/usr/bin/env python3
"""Render a per-file Java LOC/complexity diff for a PR."""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

SCC_BIN = "scc"
MAIN_MARKER = "/src/main/java/"
TEST_MARKER = "/src/test/java/"
DEFAULT_SCC_VERSION = "unknown"
FileChange = tuple[str | None, str | None, str]


def run(cmd: list[str], check: bool = True, cwd: str | None = None) -> str:
  result = subprocess.run(
      cmd, check=check, capture_output=True, text=True, cwd=cwd
  )
  return result.stdout


def changed_java_files(base: str, head: str) -> list[FileChange]:
  out = run(
      [
          "git",
          "diff",
          "--name-status",
          "-M",
          "--diff-filter=ACMRDT",
          f"{base}..{head}",
      ]
  )
  changes: list[FileChange] = []
  for line in out.splitlines():
    parts = line.split("\t")
    status = parts[0]
    if status.startswith("R"):
      base_path, head_path = parts[1], parts[2]
      if not (base_path.endswith(".java") or head_path.endswith(".java")):
        continue
      changes.append((base_path, head_path, head_path))
      continue
    if status.startswith("C"):
      _, head_path = parts[1], parts[2]
      if not head_path.endswith(".java"):
        continue
      changes.append((None, head_path, head_path))
      continue

    path = parts[1]
    if not path.endswith(".java"):
      continue
    if status == "D":
      changes.append((path, None, path))
    else:
      changes.append((path, path, path))
  return changes


def materialise(ref: str, files: list[tuple[str, str]], out_dir: Path) -> None:
  for source_path, target_path in files:
    probe = subprocess.run(
        ["git", "cat-file", "-e", f"{ref}:{source_path}"],
        capture_output=True,
    )
    if probe.returncode != 0:
      continue
    content = run(["git", "show", f"{ref}:{source_path}"])
    target = out_dir / target_path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content)


def scc_by_file(dir_path: Path) -> dict[str, dict]:
  if not any(dir_path.rglob("*.java")):
    return {}
  out = run(
      [
          SCC_BIN,
          "--by-file",
          "--format",
          "json",
          "-i",
          "java",
          str(dir_path),
      ]
  )
  data = json.loads(out)
  rows: dict[str, dict] = {}
  for language in data:
    for file_data in language.get("Files", []):
      rel = os.path.relpath(file_data["Location"], dir_path)
      rows[rel] = file_data
  return rows


def signed(value: int) -> str:
  if value > 0:
    return f"+{value}"
  return str(value)


def short_path(path: str) -> str:
  normalized = f"/{path}"
  if MAIN_MARKER in normalized:
    module, java_path = normalized.split(MAIN_MARKER, 1)
    module = module.removeprefix("/")
    return f"{module}/{java_path}" if module else java_path
  if TEST_MARKER in normalized:
    module, java_path = normalized.split(TEST_MARKER, 1)
    module = module.removeprefix("/")
    return f"{module}/{java_path}" if module else java_path
  return path


def display_path(base_path: str | None, head_path: str | None, key_path: str) -> str:
  short_key = short_path(key_path)
  if base_path and head_path and base_path != head_path:
    return f"{short_key} (renamed from {short_path(base_path)})"
  if base_path and not head_path:
    return f"{short_key} (deleted)"
  return short_key


def make_row(
    base_path: str | None,
    head_path: str | None,
    key_path: str,
    base: dict | None,
    head: dict | None,
) -> tuple:
  loc_base = base["Code"] if base else 0
  loc_head = head["Code"] if head else 0
  cx_base = base["Complexity"] if base else 0
  cx_head = head["Complexity"] if head else 0
  display = display_path(base_path, head_path, key_path)
  return (
      display,
      loc_base,
      loc_head,
      loc_head - loc_base,
      cx_base,
      cx_head,
      cx_head - cx_base,
  )


def split_blocks(
    rows: list[tuple], original_paths: list[str]
) -> tuple[list[tuple], list[tuple]]:
  main_rows: list[tuple] = []
  test_rows: list[tuple] = []
  for original_path, row in zip(original_paths, rows):
    normalized = f"/{original_path}"
    if TEST_MARKER in normalized:
      test_rows.append(row)
    else:
      main_rows.append(row)
  return main_rows, test_rows


def render_table(rows: list[tuple]) -> str:
  if not rows:
    return ""
  lines = [
      "| file | LOC b | LOC h | ΔLOC | Cx b | Cx h | ΔCx |",
      "|---|---:|---:|---:|---:|---:|---:|",
  ]
  for row in rows:
    path, loc_base, loc_head, delta_loc, cx_base, cx_head, delta_cx = row
    lines.append(
        f"| {path} | {loc_base} | {loc_head} | {signed(delta_loc)} | "
        f"{cx_base} | {cx_head} | {signed(delta_cx)} |"
    )
  return "\n".join(lines)


def render(
    base_sha: str,
    head_sha: str,
    main_rows: list[tuple],
    test_rows: list[tuple],
    zero_delta_count: int,
) -> str:
  out: list[str] = []
  scc_version = os.environ.get("SCC_VERSION", DEFAULT_SCC_VERSION)
  out.append(f"## Code complexity impact (scc v{scc_version})")
  out.append("")
  out.append(
      f"Comparing `{base_sha[:8]}` -> `{head_sha[:8]}`, Java files only."
  )

  if main_rows:
    out.append("")
    out.append("### Production-like Java files")
    out.append("")
    out.append(render_table(main_rows))

  if test_rows:
    out.append("")
    out.append("### Test Java files")
    out.append("")
    out.append(render_table(test_rows))

  def totals(rows: list[tuple]) -> tuple[int, int, int, int]:
    loc_base = sum(row[1] for row in rows)
    loc_head = sum(row[2] for row in rows)
    cx_base = sum(row[4] for row in rows)
    cx_head = sum(row[5] for row in rows)
    return loc_base, loc_head, cx_base, cx_head

  mlb, mlh, mcb, mch = totals(main_rows)
  tlb, tlh, tcb, tch = totals(test_rows)
  alb, alh = mlb + tlb, mlh + tlh
  acb, ach = mcb + tcb, mch + tch

  out.append("")
  out.append("### Totals")
  out.append("")
  out.append("| | LOC b | LOC h | ΔLOC | Cx b | Cx h | ΔCx |")
  out.append("|---|---:|---:|---:|---:|---:|---:|")
  if main_rows:
    out.append(
        f"| Production-like | {mlb} | {mlh} | {signed(mlh - mlb)} | "
        f"{mcb} | {mch} | {signed(mch - mcb)} |"
    )
  if test_rows:
    out.append(
        f"| Tests | {tlb} | {tlh} | {signed(tlh - tlb)} | "
        f"{tcb} | {tch} | {signed(tch - tcb)} |"
    )
  out.append(
      f"| **All** | **{alb}** | **{alh}** | **{signed(alh - alb)}** | "
      f"**{acb}** | **{ach}** | **{signed(ach - acb)}** |"
  )

  if zero_delta_count > 0:
    plural = "s" if zero_delta_count != 1 else ""
    out.append("")
    out.append(
        f"_{zero_delta_count} other Java file{plural} changed but had no "
        "scc metric change._"
    )

  out.append("")
  return "\n".join(out)


def main() -> int:
  if len(sys.argv) != 3:
    print("usage: scc_diff.py BASE_SHA HEAD_SHA", file=sys.stderr)
    return 2

  base_sha = run(["git", "rev-parse", sys.argv[1]]).strip()
  head_sha = run(["git", "rev-parse", sys.argv[2]]).strip()

  changes = changed_java_files(base_sha, head_sha)
  if not changes:
    print("## Code complexity impact")
    print("")
    print("_No Java files changed in this PR._")
    return 0

  with tempfile.TemporaryDirectory() as temp_dir:
    base_dir = Path(temp_dir) / "base"
    head_dir = Path(temp_dir) / "head"
    base_dir.mkdir()
    head_dir.mkdir()
    materialise(
        base_sha,
        [(base_path, key_path) for base_path, _, key_path in changes if base_path],
        base_dir,
    )
    materialise(
        head_sha,
        [(head_path, key_path) for _, head_path, key_path in changes if head_path],
        head_dir,
    )
    base_scc = scc_by_file(base_dir)
    head_scc = scc_by_file(head_dir)

  rows: list[tuple] = []
  kept_paths: list[str] = []
  zero_delta_count = 0
  for base_path, head_path, key_path in changes:
    row = make_row(
        base_path,
        head_path,
        key_path,
        base_scc.get(key_path),
        head_scc.get(key_path),
    )
    if row[3] == 0 and row[6] == 0:
      zero_delta_count += 1
      continue
    rows.append(row)
    kept_paths.append(key_path)

  main_rows, test_rows = split_blocks(rows, kept_paths)
  sort_key = lambda row: (-abs(row[6]), -abs(row[3]))
  main_rows.sort(key=sort_key)
  test_rows.sort(key=sort_key)

  print(render(base_sha, head_sha, main_rows, test_rows, zero_delta_count))
  return 0


if __name__ == "__main__":
  sys.exit(main())
