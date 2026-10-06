#!/usr/bin/env python3
"""Build the Tachiyomi/Tachimanga extension repo files from the Gradle build output.

Each extension module writes `build/keiyoushi-source-info.json` next to its APK, so the
index can be produced without the extensions-inspector (which still rejects lib 1.6) and
without aapt.

Output (under ./repo):
  apk/*.apk        the release APKs
  icon/*.png       one launcher icon per package
  index.min.json   legacy index, what Suwayomi/Tachimanga read
  index.json       newer index with per-source metadata
  index.html       plain download listing
"""
from __future__ import annotations

import html
import json
import os
import shutil
from pathlib import Path

SRC_DIR = Path("src")
REPO_DIR = Path("repo")

# Largest icon every extension module ships.
ICON_DENSITIES = ("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi")


def raw_base_url() -> str:
    repo = os.environ.get("GITHUB_REPOSITORY", "soymustamahti/extension-manga-manhwa")
    return f"https://raw.githubusercontent.com/{repo}/repo"


def find_icon(module_dir: Path) -> Path | None:
    for density in ICON_DENSITIES:
        icon = module_dir / "res" / f"mipmap-{density}" / "ic_launcher.png"
        if icon.is_file():
            return icon
    return None


def main() -> None:
    apk_dir = REPO_DIR / "apk"
    icon_dir = REPO_DIR / "icon"
    apk_dir.mkdir(parents=True, exist_ok=True)
    icon_dir.mkdir(parents=True, exist_ok=True)

    base = raw_base_url()
    extensions = []
    legacy_index = []

    for info_file in sorted(SRC_DIR.glob("*/*/build/keiyoushi-source-info.json")):
        module_dir = info_file.parent.parent
        info = json.loads(info_file.read_text(encoding="utf-8"))

        apk = next(iter(sorted((info_file.parent / "outputs/apk/release").glob("*.apk"))), None)
        if apk is None:
            raise SystemExit(f"{info['module']}: no release APK found")

        shutil.copy2(apk, apk_dir / apk.name)

        package_name = info["packageName"]
        icon = find_icon(module_dir)
        if icon is None:
            raise SystemExit(f"{info['module']}: no launcher icon found under {module_dir / 'res'}")
        shutil.copy2(icon, icon_dir / f"{package_name}.png")

        sources = info["sources"]
        content_warning = info["contentWarning"]  # 1 = safe, 2 = mixed, 3 = nsfw

        extensions.append(
            {
                "name": info["name"],
                "packageName": package_name,
                "resources": {
                    "apkUrl": f"{base}/apk/{apk.name}",
                    "iconUrl": f"{base}/icon/{package_name}.png",
                },
                "extensionLib": info["extensionLib"],
                "versionCode": info["versionCode"],
                "versionName": info["versionName"],
                "contentWarning": content_warning,
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
        )

        legacy_index.append(
            {
                "name": f"Tachiyomi: {info['name']}",
                "pkg": package_name,
                "apk": apk.name,
                "lang": sources[0]["lang"] if len(sources) == 1 else "all",
                "code": info["versionCode"],
                "version": info["versionName"],
                "nsfw": 0 if content_warning == 1 else 1,
                "sources": [
                    {
                        "name": source["name"],
                        "lang": source["lang"],
                        "id": str(source["id"]),
                        "baseUrl": source["baseUrl"],
                    }
                    for source in sources
                ],
            }
        )

    if not extensions:
        raise SystemExit("no extensions were built")

    with (REPO_DIR / "index.json").open("w", encoding="utf-8") as f:
        json.dump({"extensions": extensions}, f, ensure_ascii=False, separators=(",", ":"))

    with (REPO_DIR / "index.min.json").open("w", encoding="utf-8") as f:
        json.dump(legacy_index, f, ensure_ascii=False, separators=(",", ":"))

    with (REPO_DIR / "index.html").open("w", encoding="utf-8") as f:
        f.write('<!DOCTYPE html>\n<html><head><meta charset="UTF-8"><title>extensions</title></head><body><pre>\n')
        for extension in extensions:
            apk_url = html.escape(extension["resources"]["apkUrl"])
            name = html.escape(f"Tachiyomi: {extension['name']}")
            f.write(f'<a href="{apk_url}">{name}</a>\n')
        f.write("</pre></body></html>\n")

    print(f"wrote {len(extensions)} extensions to {REPO_DIR}")


if __name__ == "__main__":
    main()
