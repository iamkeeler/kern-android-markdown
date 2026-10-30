import concurrent.futures
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import threading
import unittest
from unittest import mock


spec = importlib.util.spec_from_file_location("build_version", Path(__file__).parents[1] / "build_version.py")
versioning = importlib.util.module_from_spec(spec)
spec.loader.exec_module(versioning)
BASE = dict(major=0, minor=1, patch=39, code=40)
PROPERTIES = "# Keep configuration intact\nversion.major=0\nversion.minor=1\nversion.patch=39\nversion.code=40\nother.setting=true\n"


class VersionPolicyTest(unittest.TestCase):
    def test_publishing_workflows_reserve_before_build_and_verify_before_upload(self):
        workflows = Path(__file__).parents[2] / ".github/workflows"
        for filename, upload in (
            ("android-build-distribution.yml", "uses: wzieba/Firebase-Distribution"),
            ("google-play-release.yml", "uses: r0adkll/upload-google-play"),
        ):
            with self.subTest(workflow=filename):
                workflow = (workflows / filename).read_text()
                reserve = workflow.index("run: python3 scripts/build_version.py reserve")
                build = workflow.index("run: ./gradlew")
                verify = workflow.index("run: python3 scripts/build_version.py verify-apk")
                self.assertLess(reserve, build)
                self.assertLess(workflow.index("run: python3 scripts/prepare_release_notes.py\n"), build)
                self.assertLess(build, verify)
                self.assertLess(build, workflow.index("prepare_release_notes.py --verify-apk"))
                self.assertLess(verify, workflow.index(upload))
                self.assertLess(workflow.index("prepare_release_notes.py --verify-apk"), workflow.index(upload))

    def test_both_numbers_advance_from_latest_source_or_counter(self):
        old_branch = dict(BASE, patch=12, code=13)
        self.assertEqual(dict(BASE, patch=40, code=41), versioning.next_version(old_branch, BASE))
        newer_source = dict(major=1, minor=0, patch=0, code=100)
        self.assertEqual(dict(major=1, minor=0, patch=1, code=101), versioning.next_version(newer_source, BASE))

    def test_version_tags_are_honored_and_cannot_reuse_a_reserved_version(self):
        self.assertEqual(dict(major=0, minor=2, patch=0, code=41), versioning.next_version(BASE, requested=(0, 2, 0)))
        with self.assertRaises(ValueError):
            versioning.next_version(BASE, dict(BASE, patch=40, code=41), requested=(0, 1, 40))

    def test_malformed_and_exhausted_codes_fail_instead_of_wrapping(self):
        for value in (True, -1, "40", versioning.MAX_VERSION_CODE + 1):
            with self.assertRaises(ValueError):
                versioning.next_version(dict(BASE, code=value))
        with self.assertRaises(ValueError):
            versioning.next_version(dict(BASE, code=versioning.MAX_VERSION_CODE))

    def test_apk_must_contain_the_reserved_name_and_code_before_upload(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk, metadata = root / "app.apk", root / "build-version.json"
            apk.write_bytes(b"test artifact")
            record = dict(BASE, version_name="0.1.39", source_commit="abc")
            metadata.write_text(json.dumps(record))
            for name, code in (("0.1.38", 40), ("0.1.39", 39)):
                result = subprocess.CompletedProcess([], 0, f"package: name='com.attachdesign.kern' versionCode='{code}' versionName='{name}'\n", "")
                with mock.patch.object(versioning.subprocess, "run", return_value=result):
                    with self.assertRaises(ValueError):
                        versioning.verify_apk(apk, metadata, "aapt")
                self.assertNotIn("artifacts", json.loads(metadata.read_text()))
            result = subprocess.CompletedProcess([], 0, "package: name='com.attachdesign.kern' versionCode='40' versionName='0.1.39'\n", "")
            with mock.patch.object(versioning.subprocess, "run", return_value=result):
                verified = versioning.verify_apk(apk, metadata, "aapt")
            self.assertEqual(hashlib.sha256(apk.read_bytes()).hexdigest(), verified["artifacts"][apk.name])


class ReservationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.remote = self.root / "remote.git"
        subprocess.run(["git", "init", "--bare", "--initial-branch=main", str(self.remote)], check=True, capture_output=True)
        self.checkout = self.root / "checkout"
        self.checkout.mkdir()
        versioning.git(self.checkout, "init", "--initial-branch=main")
        versioning.git(self.checkout, "config", "user.name", "Version test")
        versioning.git(self.checkout, "config", "user.email", "version@example.invalid")
        (self.checkout / "gradle.properties").write_text(PROPERTIES)
        versioning.git(self.checkout, "add", "gradle.properties")
        versioning.git(self.checkout, "commit", "-m", "Initial source")
        versioning.git(self.checkout, "remote", "add", "origin", str(self.remote))
        versioning.git(self.checkout, "push", "origin", "HEAD:main")
        self.head = versioning.git(self.checkout, "rev-parse", "HEAD").stdout.strip()
        # Tests must not inherit the invoking CI run's tag or output-file state.
        self.environment = mock.patch.dict(os.environ, {
            "GITHUB_REF_TYPE": "branch", "GITHUB_REF_NAME": "feature/test", "GITHUB_RUN_ID": "123", "GITHUB_RUN_ATTEMPT": "1",
        })
        self.environment.start()
        self.addCleanup(self.environment.stop)

    def clone(self, name):
        checkout = self.root / name
        versioning.git(self.root, "clone", str(self.remote), str(checkout))
        return checkout

    def test_reservation_survives_new_checkout_and_failed_run_without_changing_source_history(self):
        first = versioning.reserve(self.checkout)
        with mock.patch.dict(os.environ, {"GITHUB_RUN_ATTEMPT": "2"}):
            second = versioning.reserve(self.clone("retry"))
        self.assertEqual(("0.1.40", 41), (first["version_name"], first["code"]))
        self.assertEqual(("0.1.41", 42), (second["version_name"], second["code"]))
        self.assertEqual("2", second["run_attempt"])
        self.assertEqual(self.head, second["source_commit"])
        self.assertEqual(self.head, versioning.git(self.checkout, "rev-parse", "HEAD").stdout.strip())
        self.assertEqual("main", versioning.git(self.checkout, "branch", "--show-current").stdout.strip())
        self.assertEqual(self.head, versioning.git(self.checkout, "ls-remote", "origin", "refs/heads/main").stdout.split()[0])
        self.assertIn("other.setting=true", (self.checkout / "gradle.properties").read_text())
        metadata = json.loads((self.checkout / "build/build-version.json").read_text())
        self.assertEqual(first, metadata)

    def test_two_simultaneous_branches_cannot_reserve_the_same_pair(self):
        versioning.reserve(self.checkout)
        workers = [self.clone("one"), self.clone("two")]
        real_read = versioning.read_counter
        barrier, visited = threading.Barrier(2), threading.local()

        def read_at_same_counter(root, remote):
            result = real_read(root, remote)
            if not getattr(visited, "ready", False):
                visited.ready = True
                barrier.wait(timeout=10)
            return result

        with mock.patch.object(versioning, "read_counter", side_effect=read_at_same_counter):
            with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
                results = list(pool.map(versioning.reserve, workers))
        self.assertEqual([42, 43], sorted(record["code"] for record in results))
        self.assertEqual(["0.1.41", "0.1.42"], sorted(record["version_name"] for record in results))

    def test_rejected_push_never_changes_build_inputs_or_writes_success_metadata(self):
        hook = self.remote / "hooks/pre-receive"
        hook.write_text("#!/bin/sh\nexit 1\n")
        hook.chmod(0o755)
        with self.assertRaises(RuntimeError):
            versioning.reserve(self.checkout)
        self.assertEqual(PROPERTIES, (self.checkout / "gradle.properties").read_text())
        self.assertFalse((self.checkout / "build/build-version.json").exists())

    def test_duplicate_source_properties_fail_before_reservation(self):
        (self.checkout / "gradle.properties").write_text(PROPERTIES + "version.code=999\n")
        with self.assertRaises(ValueError):
            versioning.reserve(self.checkout)
        self.assertEqual("", versioning.git(self.checkout, "ls-remote", "origin", versioning.COUNTER_REF).stdout)

    def test_tag_build_records_its_exact_version_and_ref(self):
        with mock.patch.dict(os.environ, {"GITHUB_REF_TYPE": "tag", "GITHUB_REF_NAME": "v0.2.0"}):
            record = versioning.reserve(self.checkout)
        self.assertEqual(("0.2.0", 41, "v0.2.0"), (record["version_name"], record["code"], record["ref"]))


if __name__ == "__main__":
    unittest.main()
