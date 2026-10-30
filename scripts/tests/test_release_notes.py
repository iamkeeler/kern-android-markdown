import importlib.util
from pathlib import Path
import unittest
import tempfile
import zipfile


spec = importlib.util.spec_from_file_location("prepare_release_notes", Path(__file__).parents[1] / "prepare_release_notes.py")
notes = importlib.util.module_from_spec(spec)
spec.loader.exec_module(notes)


class ReleaseNotesTest(unittest.TestCase):
    def test_maintained_notes_are_ready_for_the_next_release(self):
        self.assertTrue(notes.validate_notes(notes.NOTES_FILE.read_text(encoding="utf-8")))

    def test_empty_or_oversized_notes_cannot_be_prepared(self):
        for text in ("", " \n ", "x" * 501):
            with self.subTest(text_length=len(text)), self.assertRaises(ValueError):
                notes.validate_notes(text)
        self.assertEqual("x" * 500, notes.validate_notes("x" * 500))

    def test_notes_keep_actual_version_source_and_optional_tester_context(self):
        metadata = dict(version_name="0.1.43", code=44, source_commit="abc123",
                        ref="codex/application-polish", run_id="123", run_attempt="2")
        body = notes.format_notes(metadata, "  Improved writing and fold controls.\n", "  Check the Settings layout.  ")
        self.assertIn("Version 0.1.43 (build 44)\nCommit: abc123", body)
        self.assertIn("Ref: codex/application-polish / Run: 123 / Attempt: 2", body)
        self.assertTrue(body.endswith("Improved writing and fold controls.\n\nCheck the Settings layout.\n"))
        self.assertNotIn("\n\n\n", notes.format_notes(metadata, "Changes.", " \n"))

    def test_markdown_is_versioned_and_written_identically_to_download_and_apk_assets(self):
        metadata = dict(version_name="0.1.44", code=45, source_commit="abc123")
        body = notes.format_markdown(metadata, notes.MARKDOWN_FILE.read_text(encoding="utf-8"))
        self.assertTrue(body.startswith("# Kern 0.1.44 (build 45)\n\n"))
        self.assertTrue(body.endswith("Source commit: `abc123`\n"))
        with tempfile.TemporaryDirectory() as directory:
            output, assets = Path(directory) / "Release Notes", Path(directory) / "assets"
            notes.write_markdown(metadata, body, output, assets)
            filename = "Kern-0.1.44-build-45.md"
            self.assertEqual(body, (output / filename).read_text(encoding="utf-8"))
            self.assertEqual((output / filename).read_bytes(), (assets / "release-notes" / filename).read_bytes())

    def test_missing_or_changed_packaged_markdown_blocks_distribution(self):
        metadata = dict(version_name="0.1.44", code=45, source_commit="abc123")
        expected = notes.format_markdown(metadata, "## Changes\n\n- Notes are bundled.")
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "app.apk"
            for contents in (None, "Outdated release notes", expected):
                with zipfile.ZipFile(apk, "w") as package:
                    if contents is not None:
                        package.writestr("assets/release-notes/" + notes.markdown_filename(metadata), contents)
                if contents == expected:
                    notes.verify_bundled_markdown(apk, metadata, expected)
                else:
                    with self.assertRaises(ValueError):
                        notes.verify_bundled_markdown(apk, metadata, expected)

    def test_empty_markdown_is_rejected(self):
        with self.assertRaises(ValueError):
            notes.format_markdown(dict(version_name="1.0.0", code=1, source_commit="abc"), " \n")


if __name__ == "__main__":
    unittest.main()
