#!/usr/bin/env python3
import html
import json
import os
import re
import struct
import subprocess
from pathlib import Path
from zipfile import ZipFile


PACKAGE_NAME_REGEX = re.compile(r"package: name='([^']+)'")
VERSION_CODE_REGEX = re.compile(r"versionCode='([^']+)'")
VERSION_NAME_REGEX = re.compile(r"versionName='([^']+)'")
CONTENT_WARNING_REGEX = re.compile(r"'tachiyomix.contentWarning' value='([^']+)'")
EXTENSION_LIB_REGEX = re.compile(r"'tachiyomix.extensionLib' value='([^']+)'")
EXTENSION_NAME_REGEX = re.compile(r"'tachiyomix.name' value='([^']+)'")
APPLICATION_ICON_320_REGEX = re.compile(r"^application-icon-320:'([^']+)'", re.MULTILINE)
LANGUAGE_REGEX = re.compile(r"tachiyomi-([^.]+)")


def hex_to_float(hex_str: str) -> float:
    int_val = int(hex_str, 16)
    float_val = struct.unpack(">f", struct.pack(">I", int_val))[0]
    return round(float_val, 1)


def android_aapt() -> Path:
    build_tools = sorted((Path(os.environ["ANDROID_HOME"]) / "build-tools").iterdir())
    return build_tools[-1] / "aapt"


def raw_base_url() -> str:
    repo = os.environ.get("GITHUB_REPOSITORY", "soymustamahti/extension-manga-manhwa")
    return f"https://raw.githubusercontent.com/{repo}/repo"


def legacy_lang(apk_name: str, sources: list[dict]) -> str:
    match = LANGUAGE_REGEX.search(apk_name)
    lang = match.group(1) if match else "all"
    if len(sources) == 1:
        source_lang = sources[0]["lang"]
        if source_lang != lang and source_lang not in {"all", "other"} and lang not in {"all", "other"}:
            return source_lang
    return lang


def main() -> None:
    repo_dir = Path("repo")
    apk_dir = repo_dir / "apk"
    icon_dir = repo_dir / "icon"
    icon_dir.mkdir(parents=True, exist_ok=True)

    with Path("output.json").open(encoding="utf-8") as f:
        inspector_data = json.load(f)

    extensions = []
    legacy_index = []
    base = raw_base_url()
    aapt = android_aapt()

    for apk in sorted(apk_dir.glob("*.apk")):
        badging = subprocess.check_output(
            [aapt, "dump", "--include-meta-data", "badging", apk],
            text=True,
        )

        package_info = next(line for line in badging.splitlines() if line.startswith("package: "))
        package_name = PACKAGE_NAME_REGEX.search(package_info).group(1)
        application_icon = APPLICATION_ICON_320_REGEX.search(badging).group(1)

        with ZipFile(apk) as zip_file:
            with zip_file.open(application_icon) as icon_in:
                with (icon_dir / f"{package_name}.png").open("wb") as icon_out:
                    icon_out.write(icon_in.read())

        sources = inspector_data[package_name]
        content_warning = int(CONTENT_WARNING_REGEX.search(badging).group(1))
        extension_lib = hex_to_float(EXTENSION_LIB_REGEX.search(badging).group(1).strip())
        ext_name = EXTENSION_NAME_REGEX.search(badging).group(1)
        version_code = int(VERSION_CODE_REGEX.search(package_info).group(1))
        version_name = VERSION_NAME_REGEX.search(package_info).group(1)

        extension = {
            "name": ext_name,
            "packageName": package_name,
            "resources": {
                "apkUrl": f"{base}/apk/{apk.name}",
                "iconUrl": f"{base}/icon/{package_name}.png",
            },
            "extensionLib": str(extension_lib),
            "versionCode": version_code,
            "versionName": version_name,
            "contentWarning": content_warning + 1,
            "sources": [
                {
                    "id": int(source["id"]),
                    "name": source["name"],
                    "language": source["lang"],
                    "homeUrl": source["baseUrl"],
                }
                for source in sources
            ],
        }
        extensions.append(extension)

        legacy_sources = [
            {
                "name": source["name"],
                "lang": source["lang"],
                "id": str(source["id"]),
                "baseUrl": source["baseUrl"],
            }
            for source in sources
        ]
        legacy_index.append(
            {
                "name": f"Tachiyomi: {ext_name}",
                "pkg": package_name,
                "apk": apk.name,
                "lang": legacy_lang(apk.name, sources),
                "code": version_code,
                "version": version_name,
                "nsfw": 1 if content_warning > 2 else 0,
                "sources": legacy_sources,
            }
        )

    with (repo_dir / "index.json").open("w", encoding="utf-8") as f:
        json.dump({"extensions": extensions}, f, ensure_ascii=False, separators=(",", ":"))

    with (repo_dir / "index.min.json").open("w", encoding="utf-8") as f:
        json.dump(legacy_index, f, ensure_ascii=False, separators=(",", ":"))

    with (repo_dir / "index.html").open("w", encoding="utf-8") as f:
        f.write("<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>extensions</title></head><body><pre>\n")
        for extension in extensions:
            apk_url = html.escape(extension["resources"]["apkUrl"])
            name = html.escape(f"Tachiyomi: {extension['name']}")
            f.write(f'<a href="{apk_url}">{name}</a>\n')
        f.write("</pre></body></html>\n")


if __name__ == "__main__":
    main()
