"""Copy a published Be My Eyes release to Modrinth without rebuilding its JAR."""

import argparse
import hashlib
import io
import json
import os
import re
import sys
import tomllib
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile

REPOSITORY = "aojiangQAQ/Be-My-Eyes"
PROJECT = "be-my-eyes"
GAME_VERSION = "1.21.1"
USER_AGENT = f"{REPOSITORY} (GitHub Actions release sync)"
MAX_JAR_BYTES = 20 * 1024 * 1024


class PublishError(Exception):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def request_json(url, token="", data=None, content_type="application/json"):
    """Do not forward API credentials through redirects or log response bodies."""
    headers = {"User-Agent": USER_AGENT, "Accept": "application/json"}
    if token:
        headers["Authorization"] = token
    if data is not None:
        headers["Content-Type"] = content_type
    request = urllib.request.Request(url, data=data, headers=headers)
    try:
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=60) as response:
            raw = response.read(4 * 1024 * 1024 + 1)
        if len(raw) > 4 * 1024 * 1024:
            raise PublishError("API response exceeds the expected size.")
        return json.loads(raw)
    except urllib.error.HTTPError as error:
        action = "upload" if data is not None else "read"
        raise PublishError(f"API {action} failed (HTTP {error.code}).") from None
    except (urllib.error.URLError, TimeoutError):
        raise PublishError("API request failed or timed out; check the remote state before retrying.") from None
    except (ValueError, UnicodeError):
        raise PublishError("API returned an invalid JSON response.") from None


def release_asset(release, tag):
    match = re.fullmatch(r"v?(\d+\.\d+\.\d+)", tag)
    if not match:
        raise PublishError("Only stable release tags such as v0.1.4 are supported.")
    if release.get("tag_name") != tag:
        raise PublishError("The GitHub release tag does not match the requested tag.")
    if release.get("draft") or release.get("prerelease"):
        raise PublishError("Drafts and prereleases are not published automatically.")
    version = match.group(1)
    filename = f"be-my-eyes-{GAME_VERSION}-{version}.jar"
    candidates = [asset for asset in release.get("assets", []) if asset.get("name") == filename]
    if len(candidates) != 1:
        raise PublishError(f"Attach exactly one {filename} to the GitHub release before publishing it.")
    asset = candidates[0]
    expected_url = (
        f"https://github.com/{REPOSITORY}/releases/download/"
        f"{urllib.parse.quote(tag, safe='')}/{filename}"
    )
    if asset.get("browser_download_url") != expected_url:
        raise PublishError("The asset download URL does not belong to this release.")
    if asset.get("state") != "uploaded" or not 0 < asset.get("size", 0) <= MAX_JAR_BYTES:
        raise PublishError("The release asset is incomplete or exceeds 20 MiB.")
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", asset.get("digest") or ""):
        raise PublishError("The GitHub asset must have a SHA-256 digest.")
    return version, asset


def download_asset(asset):
    request = urllib.request.Request(
        asset["browser_download_url"], headers={"User-Agent": USER_AGENT}
    )
    # GitHub redirects public release downloads to its CDN. No token is sent here.
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            if urllib.parse.urlsplit(response.url).scheme != "https":
                raise PublishError("The release download did not use HTTPS.")
            data = response.read(MAX_JAR_BYTES + 1)
    except (urllib.error.URLError, TimeoutError):
        raise PublishError("The release asset could not be downloaded.") from None
    if len(data) != asset["size"]:
        raise PublishError("The downloaded JAR size does not match the release asset.")
    if f"sha256:{hashlib.sha256(data).hexdigest()}" != asset["digest"]:
        raise PublishError("The downloaded JAR SHA-256 does not match GitHub.")
    return data


def validate_jar(data, version):
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            info = jar.getinfo("META-INF/mods.toml")
            if info.file_size > 128 * 1024:
                raise PublishError("The JAR metadata exceeds the expected size.")
            metadata = tomllib.loads(jar.read(info).decode("utf-8"))
    except (zipfile.BadZipFile, KeyError, UnicodeError, ValueError):
        raise PublishError("The JAR does not contain valid Forge mods.toml metadata.") from None
    mods = [mod for mod in metadata.get("mods", []) if mod.get("modId") == "duosight"]
    if len(mods) != 1 or mods[0].get("version") != version:
        raise PublishError("The JAR's internal mod version does not match the release tag.")
    if metadata.get("modLoader") != "javafml":
        raise PublishError("This publisher only supports the Forge build.")
    dependencies = metadata.get("dependencies", {}).get("duosight", [])
    minecraft = [dep for dep in dependencies if dep.get("modId") == "minecraft"]
    if len(minecraft) != 1 or minecraft[0].get("versionRange") != "[1.21.1,1.21.2)":
        raise PublishError("The JAR's Minecraft version range does not match the publisher.")
    if minecraft[0].get("side") != "BOTH":
        raise PublishError("The JAR must be intended for both client and server.")


def already_published(versions, version, filename, data):
    same_number = [
        item for item in versions
        if item.get("version_number", "").removeprefix("v") == version
    ]
    if not same_number:
        return False
    digest = hashlib.sha512(data).hexdigest()
    if len(same_number) == 1:
        item = same_number[0]
        same_file = any(
            file.get("filename") == filename and file.get("hashes", {}).get("sha512") == digest
            for file in item.get("files", [])
        )
        if (
            same_file
            and item.get("game_versions") == [GAME_VERSION]
            and item.get("loaders") == ["forge"]
            and item.get("environment", "client_and_server") == "client_and_server"
        ):
            return True
    raise PublishError("This version number already exists with different files or compatibility metadata; nothing was overwritten.")


def multipart(metadata, filename, data):
    boundary = f"BeMyEyes{uuid.uuid4().hex}"
    body = (
        f"--{boundary}\r\n"
        'Content-Disposition: form-data; name="data"\r\n'
        "Content-Type: application/json\r\n\r\n"
    ).encode() + json.dumps(metadata, ensure_ascii=False).encode("utf-8")
    body += (
        f"\r\n--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        "Content-Type: application/java-archive\r\n\r\n"
    ).encode() + data + f"\r\n--{boundary}--\r\n".encode()
    return body, f"multipart/form-data; boundary={boundary}"


def publish(tag, dry_run):
    if os.environ.get("GITHUB_REPOSITORY", REPOSITORY) != REPOSITORY:
        raise PublishError("This publisher is restricted to the Be My Eyes repository.")
    if not re.fullmatch(r"v?\d+\.\d+\.\d+", tag):
        raise PublishError("Specify a stable GitHub release tag such as v0.1.4.")
    gh_token = os.environ.get("GH_TOKEN", "").strip()
    release = request_json(
        f"https://api.github.com/repos/{REPOSITORY}/releases/tags/{urllib.parse.quote(tag, safe='')}",
        f"Bearer {gh_token}" if gh_token else "",
    )
    version, asset = release_asset(release, tag)
    data = download_asset(asset)
    validate_jar(data, version)
    print(f"Validated {asset['name']}: {len(data)} bytes, SHA-256 matches GitHub.")

    token = os.environ.get("MODRINTH_TOKEN", "").strip()
    if not token:
        if dry_run:
            print("Dry run complete. MODRINTH_TOKEN is not set; duplicate checks were not performed.")
            return
        raise PublishError("Set the MODRINTH_TOKEN repository secret before publishing.")
    project = request_json(f"https://api.modrinth.com/v2/project/{PROJECT}", token)
    if project.get("slug") != PROJECT or project.get("title") != "Be My Eyes":
        raise PublishError("The Modrinth project does not match Be My Eyes.")
    project_id = project.get("id", "")
    if not re.fullmatch(r"[A-Za-z0-9]{8}", project_id):
        raise PublishError("Modrinth returned an invalid project ID.")
    versions = request_json(f"https://api.modrinth.com/v2/project/{project_id}/version", token)
    if already_published(versions, version, asset["name"], data):
        print(f"Version {version} already contains this exact JAR; skipped without changes.")
        return
    if dry_run:
        print(f"Dry run complete. Version {version} would be uploaded; no changes made.")
        return
    metadata = {
        "project_id": project_id,
        "name": f"Be My Eyes {version}",
        "version_number": version,
        "changelog": release.get("body") or "",
        "game_versions": [GAME_VERSION],
        "loaders": ["forge"],
        "environment": "client_and_server",
        "version_type": "release",
        "status": "listed",
        "featured": False,
        "dependencies": [],
        "file_parts": ["file"],
        "primary_file": "file",
    }
    body, content_type = multipart(metadata, asset["name"], data)
    result = request_json("https://api.modrinth.com/v2/version", token, body, content_type)
    if result.get("version_number") != version or result.get("project_id") != project_id:
        raise PublishError("The upload response was unexpected; check Modrinth before retrying.")
    print(f"Published Be My Eyes {version} to Modrinth.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", default=os.environ.get("RELEASE_TAG", ""))
    parser.add_argument("--publish", action="store_true", help="Allow creating a Modrinth version")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    dry_run = args.dry_run or not args.publish or os.environ.get("DRY_RUN", "").lower() == "true"
    try:
        publish(args.tag, dry_run)
    except PublishError as error:
        print(f"Release sync stopped: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
