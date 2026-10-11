"""Helpers for Telegram Mini App links and WebApp buttons."""

from __future__ import annotations

import base64
import json
import re
from datetime import date
from ipaddress import ip_address
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from aiogram.types import InlineKeyboardButton, WebAppInfo

_TELEGRAM_LINK_HOSTS = {"t.me", "telegram.me"}
_RESERVED_EXAMPLE_HOSTS = {"example.com", "example.net", "example.org"}
_START_PARAM_KEYS = {"tgWebAppStartParam", "start_param", "startapp"}


def is_telegram_mini_app_link(url: str) -> bool:
    """Return True for t.me Mini App deep links."""
    hostname = (urlsplit(url).hostname or "").lower()
    return hostname in _TELEGRAM_LINK_HOSTS


def build_mini_app_url(base_url: str | None, start_param: str | None = None) -> str | None:
    """Build a Mini App URL with a launch param.

    `MINI_APP_URL` / `MINI_APP_WEB_URL` can be configured either as:
    - a Telegram deep link, e.g. https://t.me/ruttrack_bot/ruttrack
    - a direct WebApp URL, e.g. https://ruttrack.site/mini-app/
    """
    if not isinstance(base_url, str):
        return None

    base_url = base_url.strip()
    if not base_url:
        return None

    parts = urlsplit(base_url)
    if parts.scheme != "https" or not parts.netloc:
        return None
    hostname = (parts.hostname or "").lower()
    if hostname in _RESERVED_EXAMPLE_HOSTS or hostname.endswith(".example"):
        return None

    if not start_param:
        return base_url

    query = [
        (key, value) for key, value in parse_qsl(parts.query, keep_blank_values=True) if key not in _START_PARAM_KEYS
    ]
    start_key = "startapp" if is_telegram_mini_app_link(base_url) else "tgWebAppStartParam"
    query.append((start_key, start_param))

    return urlunsplit(
        (
            parts.scheme,
            parts.netloc,
            parts.path,
            urlencode(query),
            parts.fragment,
        )
    )


def build_mini_app_button(
    *,
    text: str,
    base_url: str | None,
    start_param: str | None = None,
) -> InlineKeyboardButton | None:
    """Build an optional button; local stand URLs cannot be opened by Telegram."""
    try:
        url = build_mini_app_url(base_url, start_param)
        if url is None:
            return None
        parts = urlsplit(url)
        host = (parts.hostname or "").lower().removesuffix(".")
        # URL shape remains usable by startup validation/local stand config,
        # but unavailable app navigation must never invalidate the main text.
        if parts.username is not None or parts.password is not None or parts.port == 0:
            return None
        try:
            address = ip_address(host)
        except ValueError:
            host = host.encode("idna").decode("ascii").lower()
            labels = host.split(".")
            if (
                len(labels) < 2
                or len(host) > 253
                or re.fullmatch(r"[0-9.]+", host)
                or any(not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?", label) for label in labels)
                or host.endswith((".localhost", ".local", ".internal"))
            ):
                return None
        else:
            if not address.is_global or address.is_multicast:
                return None
    except (ValueError, TypeError):
        return None

    if is_telegram_mini_app_link(url):
        return InlineKeyboardButton(text=text, url=url)

    return InlineKeyboardButton(text=text, web_app=WebAppInfo(url=url))


_ACTION_KEYS = {
    "lesson": {"kind", "role", "lessonId", "lessonDate"},
    "request": {"kind", "role", "requestId", "requestKind"},
    "homework": {"kind", "role", "homeworkId", "lessonDate"},
    "group": {"kind", "role", "groupId"},
    "homework-week": {"kind", "role", "weekStart"},
}


def encode_notification_action(action: dict) -> str | None:
    """Encode only the frozen ID-only launch contract, never arbitrary text/URLs."""
    if (
        not isinstance(action, dict)
        or not isinstance(action.get("role"), str)
        or action.get("role") not in {"STUDENT", "HEADMAN", "TEACHER", "ADMIN"}
    ):
        return None
    kind = action.get("kind")
    if not isinstance(kind, str) or kind not in _ACTION_KEYS or not set(action).issubset(_ACTION_KEYS[kind]):
        return None
    required = _ACTION_KEYS[kind] - {"lessonDate"}
    if not required.issubset(action):
        return None
    for name in ("lessonId", "homeworkId", "groupId"):
        if name in action and (
            not isinstance(action[name], str)
            or not re.fullmatch(r"[1-9][0-9]{0,18}", action[name])
            or int(action[name]) > 2**63 - 1
        ):
            return None
    if kind == "request" and (
        not isinstance(action["requestKind"], str)
        or action["requestKind"] not in {"EXCUSE", "LATE_CHECKIN"}
        or not isinstance(action["requestId"], str)
        or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,127}", action["requestId"])
    ):
        return None
    for name in ("lessonDate", "weekStart"):
        if name in action:
            value = action[name]
            if not isinstance(value, str) or not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", value):
                return None
            try:
                if date.fromisoformat(value).isoformat() != value:
                    return None
            except ValueError:
                return None
    raw = json.dumps({"v": 2, "action": action}, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    encoded = base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")
    return encoded if len(encoded) <= 384 else None
