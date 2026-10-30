#!/usr/bin/env python3
"""Prepare distribution notes from the maintained, localized What's New text."""

import argparse
import json
import os
from pathlib import Path


NOTES_FILE = Path(__file__).resolve().parents[1] / "release-notes/whatsnew-en-US"


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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metadata", type=Path, default=Path("build/build-version.json"))
    parser.add_argument("--output", type=Path, default=Path("build/release-notes.txt"))
    args = parser.parse_args()
    metadata = json.loads(args.metadata.read_text(encoding="utf-8"))
    notes = format_notes(metadata, NOTES_FILE.read_text(encoding="utf-8"),
                         os.environ.get("EXTRA_RELEASE_NOTES", ""))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(notes, encoding="utf-8")


if __name__ == "__main__":
    main()
