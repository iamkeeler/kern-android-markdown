#!/usr/bin/env python3
"""Prepare distribution notes from the maintained, localized What's New text."""

import argparse
import json
import os
from pathlib import Path
import re
import zipfile


NOTES_FILE = Path(__file__).resolve().parents[1] / "release-notes/whatsnew-en-US"
MARKDOWN_FILE = Path(__file__).resolve().parents[1] / "app/src/main/assets/release-notes/current.md"


def validate_notes(text):
    text = text.strip()
    if not text or len(text) > 500:
        raise ValueError("What's New must contain 1–500 characters")
    return text


def format_notes(metadata, text, extra=""):
    text = validate_notes(text)
    header = (
        f"Version {metadata['version_name']} (build {metadata['code']})\n"
        f"Commit: {metadata['source_commit']}\n"
        f"Ref: {metadata['ref']} / Run: {metadata['run_id']} / Attempt: {metadata['run_attempt']}"
    )
    return f"{header}\n\n{text}" + (f"\n\n{extra.strip()}" if extra.strip() else "") + "\n"


def markdown_filename(metadata):
    name = re.sub(r"[^A-Za-z0-9._-]", "_", metadata["version_name"])[:80]
    return f"Kern-{name}-build-{metadata['code']}.md"


def format_markdown(metadata, body):
    if not body.strip():
        raise ValueError("Bundled Markdown release notes must not be empty")
    return (f"# Kern {metadata['version_name']} (build {metadata['code']})\n\n{body.strip()}\n\n"
            f"---\n\nSource commit: `{metadata['source_commit']}`\n")


def write_markdown(metadata, text, output_directory, assets_directory):
    filename = markdown_filename(metadata)
    for destination in (Path(output_directory) / filename, Path(assets_directory) / "release-notes" / filename):
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(text, encoding="utf-8")


def verify_bundled_markdown(apk, metadata, expected):
    asset = "assets/release-notes/" + markdown_filename(metadata)
    with zipfile.ZipFile(apk) as package:
        try:
            bundled = package.read(asset)
        except KeyError as failure:
            raise ValueError("APK does not contain its versioned Markdown release notes") from failure
    if bundled != expected.encode("utf-8"):
        raise ValueError("APK release notes differ from the maintained Markdown for this build")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metadata", type=Path, default=Path("build/build-version.json"))
    parser.add_argument("--output", type=Path, default=Path("build/release-notes.txt"))
    parser.add_argument("--markdown-directory", type=Path, default=Path("build/Release Notes"))
    parser.add_argument("--assets-directory", type=Path, default=Path("build/releaseNotesAssets"))
    parser.add_argument("--verify-apk", type=Path)
    args = parser.parse_args()
    metadata = json.loads(args.metadata.read_text(encoding="utf-8"))
    markdown = format_markdown(metadata, MARKDOWN_FILE.read_text(encoding="utf-8"))
    if args.verify_apk:
        verify_bundled_markdown(args.verify_apk, metadata, markdown)
        return
    notes = format_notes(metadata, NOTES_FILE.read_text(encoding="utf-8"),
                         os.environ.get("EXTRA_RELEASE_NOTES", ""))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(notes, encoding="utf-8")
    write_markdown(metadata, markdown, args.markdown_directory, args.assets_directory)


if __name__ == "__main__":
    main()
