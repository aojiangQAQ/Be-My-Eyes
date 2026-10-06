import hashlib
import io
import unittest
import zipfile
from unittest.mock import patch

import publish_modrinth as publisher


def make_jar(version="0.1.4", game_range="[1.21.1,1.21.2)", side="BOTH"):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as jar:
        jar.writestr("META-INF/mods.toml", f"""
modLoader = "javafml"
[[mods]]
modId = "duosight"
version = "{version}"
[[dependencies.duosight]]
modId = "minecraft"
versionRange = "{game_range}"
side = "{side}"
""")
    return output.getvalue()


def make_release():
    data = make_jar()
    return {
        "tag_name": "v0.1.4",
        "draft": False,
        "prerelease": False,
        "body": "English release notes.",
        "assets": [{
            "name": "be-my-eyes-1.21.1-0.1.4.jar",
            "browser_download_url": "https://github.com/aojiangQAQ/Be-My-Eyes/releases/download/v0.1.4/be-my-eyes-1.21.1-0.1.4.jar",
            "state": "uploaded",
            "size": len(data),
            "digest": f"sha256:{hashlib.sha256(data).hexdigest()}",
        }],
    }


def existing_version(data, number="0.1.4"):
    return {
        "version_number": number,
        "game_versions": ["1.21.1"],
        "loaders": ["forge"],
        "files": [{
            "filename": "be-my-eyes-1.21.1-0.1.4.jar",
            "hashes": {"sha512": hashlib.sha512(data).hexdigest()},
        }],
    }


class PublisherTests(unittest.TestCase):
    def test_selects_exact_release_jar(self):
        version, asset = publisher.release_asset(make_release(), "v0.1.4")
        self.assertEqual(version, "0.1.4")
        self.assertEqual(asset["name"], "be-my-eyes-1.21.1-0.1.4.jar")

    def test_rejects_wrong_or_unstable_tags(self):
        for tag in ("", "v0.1.4-beta", "v0.1.4;echo unsafe", "../../v0.1.4", "v0.1.5"):
            with self.subTest(tag=tag), self.assertRaises(publisher.PublishError):
                publisher.release_asset(make_release(), tag)

    def test_rejects_draft_and_prerelease(self):
        for field in ("draft", "prerelease"):
            release = make_release()
            release[field] = True
            with self.subTest(field=field), self.assertRaises(publisher.PublishError):
                publisher.release_asset(release, "v0.1.4")

    def test_rejects_missing_or_ambiguous_assets(self):
        for count in (0, 2):
            release = make_release()
            release["assets"] *= count
            with self.subTest(count=count), self.assertRaises(publisher.PublishError):
                publisher.release_asset(release, "v0.1.4")

    def test_rejects_wrong_asset_origin_and_bad_digest(self):
        for field, value in (
            ("browser_download_url", "https://example.com/mod.jar"),
            ("digest", None), ("state", "starter"), ("size", 0),
            ("size", publisher.MAX_JAR_BYTES + 1),
        ):
            release = make_release()
            release["assets"][0][field] = value
            with self.subTest(field=field), self.assertRaises(publisher.PublishError):
                publisher.release_asset(release, "v0.1.4")

    def test_accepts_correct_jar(self):
        publisher.validate_jar(make_jar(), "0.1.4")

    def test_rejects_wrong_internal_version_and_compatibility(self):
        for data in (
            make_jar(version="0.1.3"), make_jar(game_range="[1.21,1.22)"),
            make_jar(side="CLIENT"), b"not a jar",
        ):
            with self.subTest(data_length=len(data)), self.assertRaises(publisher.PublishError):
                publisher.validate_jar(data, "0.1.4")

    def test_skips_same_version_and_hash(self):
        data = make_jar()
        for number in ("0.1.4", "v0.1.4"):
            self.assertTrue(publisher.already_published(
                [existing_version(data, number)], "0.1.4",
                "be-my-eyes-1.21.1-0.1.4.jar", data,
            ))

    def test_allows_new_version(self):
        self.assertFalse(publisher.already_published([], "0.1.4", "mod.jar", make_jar()))

    def test_rejects_conflicting_version(self):
        data = make_jar()
        version = existing_version(data)
        version["files"][0]["hashes"]["sha512"] = "different"
        with self.assertRaises(publisher.PublishError):
            publisher.already_published(
                [version], "0.1.4", "be-my-eyes-1.21.1-0.1.4.jar", data,
            )

    def test_rejects_conflicting_environment(self):
        data = make_jar()
        version = existing_version(data)
        version["environment"] = "server_only"
        with self.assertRaises(publisher.PublishError):
            publisher.already_published(
                [version], "0.1.4", "be-my-eyes-1.21.1-0.1.4.jar", data,
            )

    def test_dry_run_does_not_upload(self):
        with (
            patch.dict("os.environ", {"MODRINTH_TOKEN": ""}, clear=True),
            patch.object(publisher, "request_json", return_value=make_release()) as request,
            patch.object(publisher, "download_asset", return_value=make_jar()),
        ):
            publisher.publish("v0.1.4", dry_run=True)
            self.assertEqual(request.call_count, 1)
            self.assertIsNone(request.call_args.kwargs.get("data"))

    def test_missing_token_cannot_upload(self):
        with (
            patch.dict("os.environ", {"MODRINTH_TOKEN": ""}, clear=True),
            patch.object(publisher, "request_json", return_value=make_release()) as request,
            patch.object(publisher, "download_asset", return_value=make_jar()),
            self.assertRaises(publisher.PublishError),
        ):
            publisher.publish("v0.1.4", dry_run=False)
        self.assertEqual(request.call_count, 1)

    def test_authenticated_dry_run_only_reads(self):
        responses = [
            make_release(),
            {"id": "AABBCCDD", "slug": "be-my-eyes", "title": "Be My Eyes"},
            [],
        ]
        with (
            patch.dict("os.environ", {"MODRINTH_TOKEN": "test-placeholder"}, clear=True),
            patch.object(publisher, "request_json", side_effect=responses) as request,
            patch.object(publisher, "download_asset", return_value=make_jar()),
        ):
            publisher.publish("v0.1.4", dry_run=True)
        self.assertEqual(request.call_count, 3)
        self.assertTrue(all(len(call.args) <= 2 for call in request.call_args_list))

    def test_existing_version_is_not_uploaded(self):
        data = make_jar()
        responses = [
            make_release(),
            {"id": "AABBCCDD", "slug": "be-my-eyes", "title": "Be My Eyes"},
            [existing_version(data)],
        ]
        with (
            patch.dict("os.environ", {"MODRINTH_TOKEN": "test-placeholder"}, clear=True),
            patch.object(publisher, "request_json", side_effect=responses) as request,
            patch.object(publisher, "download_asset", return_value=data),
        ):
            publisher.publish("v0.1.4", dry_run=False)
        self.assertEqual(request.call_count, 3)

    def test_reports_project_review_status_without_uploading_in_dry_run(self):
        responses = [
            make_release(),
            {"id": "AABBCCDD", "slug": "be-my-eyes", "title": "Be My Eyes", "status": "processing"},
            [],
        ]
        output = io.StringIO()
        with (
            patch.dict("os.environ", {"MODRINTH_TOKEN": "test-placeholder"}, clear=True),
            patch.object(publisher, "request_json", side_effect=responses) as request,
            patch.object(publisher, "download_asset", return_value=make_jar()),
            patch("sys.stdout", output),
        ):
            publisher.publish("v0.1.4", dry_run=True)
        self.assertIn("Modrinth project status: processing.", output.getvalue())
        self.assertEqual(request.call_count, 3)
        self.assertTrue(all(len(call.args) <= 2 for call in request.call_args_list))

    def test_new_version_uploads_release_notes_and_correct_metadata(self):
        data = make_jar()
        responses = [
            make_release(),
            {"id": "AABBCCDD", "slug": "be-my-eyes", "title": "Be My Eyes"},
            [],
            {"project_id": "AABBCCDD", "version_number": "0.1.4"},
        ]
        with (
            patch.dict("os.environ", {"MODRINTH_TOKEN": "test-placeholder"}, clear=True),
            patch.object(publisher, "request_json", side_effect=responses) as request,
            patch.object(publisher, "download_asset", return_value=data),
        ):
            publisher.publish("v0.1.4", dry_run=False)
        url, token, body, content_type = request.call_args.args
        self.assertEqual(url, "https://api.modrinth.com/v2/version")
        self.assertIn(data, body)
        for field in (
            b'"changelog": "English release notes."',
            b'"game_versions": ["1.21.1"]',
            b'"loaders": ["forge"]',
            b'"environment": "client_and_server"',
        ):
            self.assertIn(field, body)

    def test_other_repository_is_rejected_before_network_access(self):
        with (
            patch.dict("os.environ", {"GITHUB_REPOSITORY": "someone/other-mod"}, clear=True),
            patch.object(publisher, "request_json") as request,
            self.assertRaises(publisher.PublishError),
        ):
            publisher.publish("v0.1.4", dry_run=False)
        request.assert_not_called()

    def test_multipart_preserves_exact_jar(self):
        data = make_jar()
        body, content_type = publisher.multipart({"name": "Be My Eyes"}, "mod.jar", data)
        self.assertIn(data, body)
        self.assertIn(b'name="data"', body)
        self.assertIn(b'name="file"; filename="mod.jar"', body)
        self.assertTrue(content_type.startswith("multipart/form-data; boundary="))


if __name__ == "__main__":
    unittest.main()
