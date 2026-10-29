#!/usr/bin/env python3
"""Reserve shared Android distribution versions and verify the packaged APK."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import uuid


COUNTER_REF = "refs/heads/codex/build-version-counter"
MAX_VERSION_CODE = 2_100_000_000
KEYS = ("major", "minor", "patch", "code")


def git(root, *args, input=None, check=True):
    return subprocess.run(
        ["git", "-C", str(root), *args], input=input, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=check,
    )


def validate(version):
    if any(type(version.get(key)) is not int or version[key] < 0 for key in KEYS):
        raise ValueError("Version components must be nonnegative integers")
    if not 1 <= version["code"] <= MAX_VERSION_CODE:
        raise ValueError("Android version code is outside the supported range")
    return version


def read_properties(path):
    text = path.read_text()
    result = {}
    for key in KEYS:
        values = re.findall(rf"^version\.{key}=(\d+)$", text, re.MULTILINE)
        if len(values) != 1:
            raise ValueError(f"Expected exactly one version.{key} in {path}")
        result[key] = int(values[0])
    return validate(result)


def next_version(source, previous=None, requested=None):
    validate(source)
    previous = validate(previous) if previous is not None else source
    major, minor, patch = max(
        tuple(version[key] for key in KEYS[:3]) for version in (source, previous)
    )
    if requested is not None:
        if requested < tuple(source[key] for key in KEYS[:3]) or requested <= tuple(previous[key] for key in KEYS[:3]):
            raise ValueError("Version tag must be newer than the last reserved version and at least the source version")
        major, minor, patch = requested
    else:
        patch += 1
    return validate(dict(major=major, minor=minor, patch=patch,
                         code=max(source["code"], previous["code"]) + 1))


def version_name(version):
    return ".".join(str(version[key]) for key in KEYS[:3])


def read_counter(root, remote):
    advertised = git(root, "ls-remote", "--refs", remote, COUNTER_REF).stdout.strip()
    if not advertised:
        return None, None
    git(root, "fetch", "--no-tags", remote, COUNTER_REF)
    head = git(root, "rev-parse", "FETCH_HEAD").stdout.strip()
    previous = json.loads(git(root, "show", f"{head}:version.json").stdout)
    return head, validate(previous)


def reserve(root, remote="origin", attempts=5):
    """A normal fast-forward-only push arbitrates concurrent runs across all branches."""
    root = Path(root)
    properties = root / "gradle.properties"
    source = read_properties(properties)
    source_commit = git(root, "rev-parse", "HEAD").stdout.strip()
    tag = re.fullmatch(r"v(\d+)\.(\d+)\.(\d+)", os.environ.get("GITHUB_REF_NAME", ""))
    requested = tuple(map(int, tag.groups())) if tag and os.environ.get("GITHUB_REF_TYPE") == "tag" else None
    for _ in range(attempts):
        head, previous = read_counter(root, remote)
        record = next_version(source, previous, requested)
        record.update(
            version_name=version_name(record), source_commit=source_commit,
            ref=os.environ.get("GITHUB_REF_NAME", git(root, "branch", "--show-current").stdout.strip()),
            run_id=os.environ.get("GITHUB_RUN_ID", "local"),
            run_attempt=os.environ.get("GITHUB_RUN_ATTEMPT", "1"),
            # Distinct simultaneous requests must never generate the same commit/tree.
            reservation_id=str(uuid.uuid4()),
        )
        payload = json.dumps(record, indent=2, sort_keys=True) + "\n"
        blob = git(root, "hash-object", "-w", "--stdin", input=payload).stdout.strip()
        tree = git(root, "mktree", input=f"100644 blob {blob}\tversion.json\n").stdout.strip()
        args = ["-c", "user.name=Kern build versioning", "-c",
                "user.email=github-actions[bot]@users.noreply.github.com", "commit-tree", tree]
        if head:
            args.extend(["-p", head])
        commit = git(root, *args, input=f"Reserve {record['version_name']} ({record['code']})\n").stdout.strip()
        pushed = git(root, "push", "--porcelain", remote, f"{commit}:{COUNTER_REF}", check=False)
        if pushed.returncode == 0:
            # Do not change the checkout or build until the reservation is durable.
            text = properties.read_text()
            for key in KEYS:
                text = re.sub(rf"^version\.{key}=\d+$", f"version.{key}={record[key]}", text, flags=re.MULTILINE)
            properties.write_text(text)
            metadata = root / "build" / "build-version.json"
            metadata.parent.mkdir(parents=True, exist_ok=True)
            metadata.write_text(payload)
            return record
        current = git(root, "ls-remote", "--refs", remote, COUNTER_REF).stdout.strip()
        current_head = current.split()[0] if current else None
        if current_head == head:
            raise RuntimeError(f"Version reservation failed: {pushed.stderr.strip()}")
        # Another run won the push; fetch its value and reserve the following pair.
    raise RuntimeError("Could not reserve a unique version after concurrent updates")


def find_aapt():
    for home in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")):
        if home:
            candidates = sorted(Path(home).glob("build-tools/*/aapt"),
                                key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.parent.name)))
            if candidates:
                return str(candidates[-1])
    raise RuntimeError("Android SDK aapt is required to verify the packaged version")


def verify_apk(apk, metadata, aapt=None):
    metadata, apk = Path(metadata), Path(apk)
    record = validate(json.loads(metadata.read_text()))
    badging = subprocess.run([aapt or find_aapt(), "dump", "badging", str(apk)],
                            check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
    package = next((line for line in badging.splitlines() if line.startswith("package:")), "")
    code = re.search(r"versionCode='(\d+)'", package)
    name = re.search(r"versionName='([^']+)'", package)
    if not code or not name or (int(code[1]), name[1]) != (record["code"], version_name(record)):
        raise ValueError(f"APK does not match reserved version {version_name(record)} ({record['code']})")
    record.setdefault("artifacts", {})[apk.name] = hashlib.sha256(apk.read_bytes()).hexdigest()
    metadata.write_text(json.dumps(record, indent=2, sort_keys=True) + "\n")
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    allocate = commands.add_parser("reserve")
    allocate.add_argument("--root", type=Path, default=Path.cwd())
    verify = commands.add_parser("verify-apk")
    verify.add_argument("--apk", required=True, type=Path)
    verify.add_argument("--metadata", type=Path, default=Path("build/build-version.json"))
    args = parser.parse_args()
    if args.command == "reserve":
        record = reserve(args.root)
        outputs = f"version_name={record['version_name']}\nversion_code={record['code']}\n"
        if os.environ.get("GITHUB_OUTPUT"):
            with open(os.environ["GITHUB_OUTPUT"], "a") as output:
                output.write(outputs)
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
                summary.write(f"Build **{record['version_name']} ({record['code']})** · commit `{record['source_commit']}`\n")
    else:
        record = verify_apk(args.apk, args.metadata)
    print(f"{record['version_name']} ({record['code']}) · {record['source_commit']}")


if __name__ == "__main__":
    main()
