"""Helpers for Telegram Mini App links and WebApp buttons."""

from __future__ import annotations

from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from aiogram.types import InlineKeyboardButton, WebAppInfo

_TELEGRAM_LINK_HOSTS = {"t.me", "telegram.me"}
_START_PARAM_KEYS = {"tgWebAppStartParam", "start_param", "startapp"}


def is_telegram_mini_app_link(url: str) -> bool:
    """Return True for t.me Mini App deep links."""
    hostname = (urlsplit(url).hostname or "").lower()
    return hostname in _TELEGRAM_LINK_HOSTS


def build_mini_app_url(base_url: str | None, start_param: str | None = None) -> str | None:
    """Build a Mini App URL with a launch param.

    `MINI_APP_URL` can be configured either as:
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
    """Build an inline button that opens the Mini App."""
    url = build_mini_app_url(base_url, start_param)
    if url is None:
        return None

    if is_telegram_mini_app_link(url):
        return InlineKeyboardButton(text=text, url=url)

    return InlineKeyboardButton(text=text, web_app=WebAppInfo(url=url))
