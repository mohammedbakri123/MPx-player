import glob
import json
import math
import os
import re
import shutil
import sys
import tempfile
import urllib.request
from typing import Any
from urllib.error import URLError


MOBILE_USER_AGENT = (
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/135.0.0.0 Mobile Safari/537.36"
)
PYPI_JSON_URL = "https://pypi.org/pypi/yt-dlp/json"


def _updates_dir():
    return os.path.join(os.environ.get("HOME", os.getcwd()), "yt_dlp_updates")


def _ensure_updates_dir():
    path = _updates_dir()
    os.makedirs(path, exist_ok=True)
    return path


def _activate_runtime_wheel():
    wheels = sorted(glob.glob(os.path.join(_updates_dir(), "yt_dlp-*.whl")))
    if not wheels:
        return None
    wheel = wheels[-1]
    if wheel not in sys.path:
        sys.path.insert(0, wheel)
    return wheel


_activate_runtime_wheel()

import yt_dlp
import yt_dlp.version
from yt_dlp import YoutubeDL


class DownloadCancelled(Exception):
    pass


def get_yt_dlp_version():
    return yt_dlp.version.__version__


def get_runtime_status_json():
    latest_path = _activate_runtime_wheel()
    version = get_yt_dlp_version()
    return json.dumps(
        {
            "version": version,
            "latestVersion": None,
            "ytDlpAvailable": True,
            "ffmpegAvailable": False,
            "ytDlpPath": latest_path or "python:yt_dlp",
            "ffmpegPath": None,
            "updateAvailable": False,
            "updated": False,
            "requiresRestart": False,
            "message": None,
        }
    )


def install_or_update_yt_dlp_json(install_if_available=True):
    current_version = get_yt_dlp_version()
    latest_info = _fetch_latest_release_info()
    latest_version = latest_info["version"]
    update_available = _compare_versions(current_version, latest_version) < 0

    if not install_if_available or not update_available:
        return json.dumps(
            {
                "version": current_version,
                "latestVersion": latest_version,
                "ytDlpAvailable": True,
                "ffmpegAvailable": False,
                "ytDlpPath": _activate_runtime_wheel() or "python:yt_dlp",
                "ffmpegPath": None,
                "updateAvailable": update_available,
                "updated": False,
                "requiresRestart": False,
                "message": "Already up to date" if not update_available else "Update available",
            }
        )

    wheel_url = latest_info["wheel_url"]
    wheel_path = _download_latest_wheel(wheel_url, latest_version)
    _reload_ytdlp_runtime(wheel_path)
    new_version = get_yt_dlp_version()

    return json.dumps(
        {
            "version": new_version,
            "latestVersion": latest_version,
            "ytDlpAvailable": True,
            "ffmpegAvailable": False,
            "ytDlpPath": wheel_path,
            "ffmpegPath": None,
            "updateAvailable": _compare_versions(new_version, latest_version) < 0,
            "updated": True,
            "requiresRestart": False,
            "message": f"yt-dlp updated to {new_version}",
        }
    )


def fetch_video_info(url, cookies_path=None):
    try:
        with YoutubeDL(_build_options(cookies_path=cookies_path)) as ydl:  # type: ignore[arg-type]
            info = ydl.extract_info(url, download=False)
        return json.dumps(info, default=str)
    except Exception as exc:
        raise RuntimeError(_friendly_error_message(url, exc)) from exc


def download_video(
    task_id,
    url,
    output_path,
    format_selector,
    cookies_path,
    emitter,
    cancel_token,
):
    def progress_hook(data):
        if cancel_token.isCancelled():
            raise DownloadCancelled("cancelled")

        status = data.get("status")
        if status == "downloading":
            downloaded = data.get("downloaded_bytes") or 0
            total = (
                data.get("total_bytes")
                or data.get("total_bytes_estimate")
                or 0
            )
            progress = 0.0
            if total:
                progress = min(downloaded / total, 1.0)

            speed = data.get("speed")
            eta = data.get("eta")
            emitter.emit(
                "downloading",
                progress,
                _format_speed(speed),
                _format_eta(eta),
                data.get("_default_template") or "Downloading",
                None,
            )
        elif status == "finished":
            emitter.emit(
                "downloading",
                1.0,
                None,
                None,
                "Finalizing file",
                data.get("filename"),
            )

    options: dict[str, Any] = {
        **_build_options(cookies_path=cookies_path),
        "outtmpl": output_path,
        "format": format_selector,
        "progress_hooks": [progress_hook],
        "continuedl": True,
        "retries": 1,
    }
    import logging
    logging.warning(f"[yt-dlp] format_selector = {format_selector}")
    logging.warning(f"[yt-dlp] output_path = {output_path}")
    try:
        with YoutubeDL(options) as ydl:  # type: ignore[arg-type]
            info = ydl.extract_info(url, download=True)
            selected_format = info.get("format") or info.get("format_id") or "unknown"
            selected_height = info.get("height") or "unknown"
            logging.warning(f"[yt-dlp] selected format = {selected_format}, height = {selected_height}")
            final_path = _resolve_final_path(info, ydl, output_path)
    except Exception as exc:
        raise RuntimeError(_friendly_error_message(url, exc)) from exc

    emitter.emit("completed", 1.0, None, None, "Download completed", final_path)
    return final_path


def _build_options(cookies_path=None):
    options: dict[str, Any] = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "socket_timeout": 20,
        "http_headers": {
            "User-Agent": MOBILE_USER_AGENT,
            "Accept-Language": "en-US,en;q=0.9",
        },
    }
    if cookies_path:
        options["cookiefile"] = cookies_path
    return options


def _fetch_latest_release_info():
    with urllib.request.urlopen(PYPI_JSON_URL, timeout=15) as response:
        payload = json.loads(response.read().decode("utf-8"))

    version = payload["info"]["version"]
    urls = payload.get("urls") or []
    wheel_url = None
    for item in urls:
        filename = item.get("filename", "")
        if item.get("packagetype") == "bdist_wheel" and filename.endswith("py3-none-any.whl"):
            wheel_url = item.get("url")
            break

    if not wheel_url:
        raise RuntimeError("Could not locate a runtime-compatible yt-dlp wheel")

    return {"version": version, "wheel_url": wheel_url}


def _download_latest_wheel(wheel_url, version):
    updates_dir = _ensure_updates_dir()
    target_path = os.path.join(updates_dir, f"yt_dlp-{version}-py3-none-any.whl")
    if os.path.exists(target_path):
        return target_path

    with tempfile.NamedTemporaryFile(delete=False, suffix=".whl") as temp_file:
        temp_path = temp_file.name

    try:
        with urllib.request.urlopen(wheel_url, timeout=60) as response, open(temp_path, "wb") as out:
            shutil.copyfileobj(response, out)
        for old_file in glob.glob(os.path.join(updates_dir, "yt_dlp-*.whl")):
            if old_file != target_path:
                try:
                    os.remove(old_file)
                except OSError:
                    pass
        shutil.move(temp_path, target_path)
    finally:
        if os.path.exists(temp_path):
            os.remove(temp_path)

    return target_path


def _reload_ytdlp_runtime(wheel_path):
    if wheel_path not in sys.path:
        sys.path.insert(0, wheel_path)

    for module_name in list(sys.modules.keys()):
        if module_name == "yt_dlp" or module_name.startswith("yt_dlp."):
            sys.modules.pop(module_name, None)

    global yt_dlp, YoutubeDL
    import yt_dlp as refreshed_module
    from yt_dlp import YoutubeDL as refreshed_class

    yt_dlp = refreshed_module
    YoutubeDL = refreshed_class


def _compare_versions(left, right):
    def normalize(value):
        parts = [int(part) for part in re.findall(r"\d+", value or "")]
        return parts

    left_parts = normalize(left)
    right_parts = normalize(right)
    length = max(len(left_parts), len(right_parts))
    left_parts.extend([0] * (length - len(left_parts)))
    right_parts.extend([0] * (length - len(right_parts)))

    if left_parts < right_parts:
        return -1
    if left_parts > right_parts:
        return 1
    return 0


def _friendly_error_message(url, exc):
    message = str(exc)
    lower_message = message.lower()
    lower_url = url.lower()

    if "instagram" in lower_url and (
        "login" in lower_message
        or "cookie" in lower_message
        or "private" in lower_message
        or "restricted" in lower_message
    ):
        return (
            "Instagram blocked anonymous access. Import cookies from a logged-in "
            "Instagram session in Downloader Settings and try again."
        )

    if isinstance(exc, URLError):
        return "Network error while contacting the video site. Check your connection and try again."

    if "unsupported url" in lower_message:
        return "This shared link is not supported by the current yt-dlp engine yet."

    return message


def _resolve_final_path(info, ydl, output_path):
    requested = info.get("requested_downloads") or []
    for item in requested:
        path = item.get("filepath")
        if path:
            return path

    filepath = info.get("filepath")
    if filepath:
        return filepath

    try:
        return ydl.prepare_filename(info)
    except Exception:
        return output_path


def _format_speed(value):
    if value in (None, 0):
        return None
    units = ["B/s", "KB/s", "MB/s", "GB/s"]
    speed = float(value)
    unit_index = 0
    while speed >= 1024 and unit_index < len(units) - 1:
        speed /= 1024
        unit_index += 1
    return f"{speed:.1f} {units[unit_index]}"


def _format_eta(value):
    if value in (None, ""):
        return None
    seconds = int(math.ceil(float(value)))
    minutes, seconds = divmod(seconds, 60)
    hours, minutes = divmod(minutes, 60)
    if hours > 0:
        return f"{hours}h {minutes}m"
    if minutes > 0:
        return f"{minutes}m {seconds}s"
    return f"{seconds}s"
