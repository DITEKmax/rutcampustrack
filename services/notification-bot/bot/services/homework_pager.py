"""Lossless Telegram text pages and private, expiring homework navigation."""

import re
import secrets
import time
from collections import defaultdict
from dataclasses import dataclass

from aiogram.exceptions import TelegramBadRequest
from aiogram.types import InlineKeyboardButton, InlineKeyboardMarkup, LinkPreviewOptions

from bot.services.bot_menu import MenuScope, resolve_menu_scope

CALLBACK_PREFIX = "homework-page:"
_TEXT_LIMIT = 3900
_NO_PREVIEW = LinkPreviewOptions(is_disabled=True)
_URL = re.compile(r"https?://[^\s<>]+", re.IGNORECASE)


def split_message(text: str) -> tuple[str, ...]:
    """Keep every code point within the UTF-16 budget and avoid splitting links that fit."""
    pages = []
    start = 0
    links = iter(_URL.finditer(text))
    link = next(links, None)
    while start < len(text):
        end = min(start + _TEXT_LIMIT, len(text))
        units = len(text[start:end].encode("utf-16-le")) // 2
        while units > _TEXT_LIMIT:
            end -= 1
            units -= 1 + (ord(text[end]) > 0xFFFF)
        if end < len(text):
            minimum = start + (end - start) // 2
            for separator in ("\n\n", "\n", " "):
                cut = text.rfind(separator, minimum, end)
                if cut >= minimum:
                    end = cut + len(separator)
                    break
        while link is not None and link.end() <= end:
            link = next(links, None)
        if (
            link is not None
            and start < link.start() < end < link.end()
            and len(link.group().encode("utf-16-le")) // 2 <= _TEXT_LIMIT
        ):
            end = link.start()
        pages.append(text[start:end])
        start = end
    return tuple(pages)


def homework_pages(
    title: str, period: str, empty: str, items: list[dict], *, show_completion: bool = True
) -> tuple[str, ...]:
    header = f"{title}\n{period}" if period else title
    if not items:
        return split_message(f"{header}\n\n{empty}")

    by_date = defaultdict(list)
    for item in items:
        lesson_date = str(_field(item, "lesson_date", "lessonDate", "dueDate") or "")
        by_date[lesson_date[:10]].append(item)

    sections = [header]
    for lesson_date, day_items in sorted(by_date.items()):
        sections.append(
            "\n\n".join(
                [
                    format_homework_date(lesson_date) or "Дата не указана",
                    *(
                        _task_text(item, show_completion)
                        for item in sorted(
                            day_items,
                            key=lambda item: (
                                _field(item, "lesson_number", "lessonNumber") or 0,
                                _field(item, "title") or "",
                            ),
                        )
                    ),
                ]
            )
        )
    return split_message("\n\n".join(sections))


def _task_text(item: dict, show_completion: bool) -> str:
    subject = _compact(_field(item, "subject_name", "subjectName")) or "Предмет"
    number = _field(item, "lesson_number", "lessonNumber")
    lesson = f"{subject}, пара №{number}" if number else subject
    if show_completion:
        marker = "✅" if _field(item, "completed") else "⬜"
        lesson = f"{marker} {lesson}"
    title = _compact(_field(item, "title")) or "Домашнее задание"
    description = _field(item, "description")
    if not isinstance(description, str) or not description.strip():
        description = ""
    link = _compact(_field(item, "link"))
    return "\n".join(line for line in (lesson, title, description, link) if line)


def _field(item: dict, *names: str):
    return next((item[name] for name in names if name in item), None)


def _compact(value) -> str:
    return " ".join(value.split()) if isinstance(value, str) else ""


def format_homework_date(value) -> str:
    raw = str(value or "")
    if len(raw) >= 10 and raw[4] == "-" and raw[7] == "-":
        return f"{raw[8:10]}.{raw[5:7]}.{raw[:4]}"
    return raw


@dataclass(frozen=True)
class _Page:
    index: int
    revision: str


@dataclass
class _Snapshot:
    sender: int
    message_id: int
    scope: MenuScope
    pages: tuple[str, ...]
    expires: float
    current: _Page
    pending: _Page | None = None
    editing: bool = False


class HomeworkPager:
    """Bounded process-local state; old buttons become stale after restart or expiry."""

    def __init__(self, *, ttl_seconds: float = 900, max_cached_pages: int = 1024, clock=time.monotonic):
        if ttl_seconds <= 0 or max_cached_pages < 2:
            raise ValueError("Positive homework paging limits required")
        self._ttl = ttl_seconds
        self._limit = max_cached_pages
        self._clock = clock
        self._entries: dict[str, _Snapshot] = {}

    def can_paginate(self, pages: tuple[str, ...]) -> bool:
        return 1 <= len(pages) <= self._limit

    async def send(self, send_message, *, sender: int, scope: MenuScope, pages: tuple[str, ...]):
        if not self.can_paginate(pages):
            raise ValueError("Homework snapshot exceeds the page cache")
        if len(pages) == 1:
            return await send_message(pages[0], parse_mode=None, link_preview_options=_NO_PREVIEW)

        token = secrets.token_urlsafe(12)
        first = _Page(0, secrets.token_urlsafe(9))
        sent = await send_message(
            _page_text(pages, 0),
            parse_mode=None,
            link_preview_options=_NO_PREVIEW,
            reply_markup=_buttons(token, first, len(pages)),
        )
        message_id = getattr(sent, "message_id", None)
        chat = getattr(sent, "chat", None)
        if type(message_id) is not int or message_id <= 0 or not _private_chat(chat, sender):
            raise RuntimeError("Homework page receipt unavailable")
        now = self._clock()
        self._entries = {token: entry for token, entry in self._entries.items() if entry.expires > now}
        cached_pages = sum(len(entry.pages) for entry in self._entries.values())
        while cached_pages + len(pages) > self._limit:
            evicted = self._entries.pop(next(iter(self._entries)))
            cached_pages -= len(evicted.pages)
        self._entries[token] = _Snapshot(sender, message_id, scope, pages, now + self._ttl, first)
        return sent

    async def turn(self, callback, bot_menu_client) -> bool:
        message = callback.message
        sender = getattr(callback.from_user, "id", None)
        message_id = getattr(message, "message_id", None)
        if (
            type(sender) is not int
            or sender <= 0
            or type(message_id) is not int
            or not _private_chat(getattr(message, "chat", None), sender)
            or not callable(getattr(message, "edit_text", None))
        ):
            return False
        try:
            prefix, token, revision, direction = callback.data.split(":")
            offset = {"back": -1, "next": 1}[direction]
        except (AttributeError, KeyError, ValueError):
            return False
        entry = self._get(token)
        if (
            entry is None
            or prefix != CALLBACK_PREFIX[:-1]
            or entry.sender != sender
            or entry.message_id != message_id
            or entry.editing
        ):
            return False
        source = entry.current
        if entry.pending is not None and revision == entry.pending.revision:
            source = entry.pending
        if revision != source.revision:
            return False
        index = source.index + offset
        if not 0 <= index < len(entry.pages):
            return False
        if source is entry.current and entry.pending is not None and entry.pending.index != index:
            return False

        # Claim synchronously so simultaneous callbacks from the same markup cannot both edit.
        entry.editing = True
        try:
            scope = await resolve_menu_scope(bot_menu_client, sender, raise_unavailable=True)
            if self._get(token) is not entry:
                return False
            if scope != entry.scope or not scope.homework_enabled:
                self._entries.pop(token, None)
                return False
            if source is entry.pending:
                # The random revision could only come from a keyboard installed by the previous edit.
                entry.current = source
                entry.pending = None
            target = entry.pending or _Page(index, secrets.token_urlsafe(9))
            entry.pending = target
            try:
                await message.edit_text(
                    _page_text(entry.pages, target.index),
                    parse_mode=None,
                    link_preview_options=_NO_PREVIEW,
                    reply_markup=_buttons(token, target, len(entry.pages)),
                )
            except TelegramBadRequest as error:
                # A retry of an edit that reached Telegram can already be on this exact page.
                if "message is not modified" not in error.message.lower():
                    entry.pending = None
                    raise
            entry.current, entry.pending = target, None
            return True
        finally:
            entry.editing = False

    def _get(self, token: str) -> _Snapshot | None:
        entry = self._entries.get(token)
        if entry is not None and entry.expires <= self._clock():
            self._entries.pop(token)
            return None
        return entry


def _private_chat(chat, sender: int) -> bool:
    chat_id = getattr(chat, "id", None)
    return type(chat_id) is int and chat_id == sender and getattr(chat, "type", None) == "private"


def _page_text(pages: tuple[str, ...], index: int) -> str:
    return f"{pages[index]}\n\nСтраница {index + 1} из {len(pages)}"


def _buttons(token: str, page: _Page, size: int) -> InlineKeyboardMarkup:
    prefix = f"{CALLBACK_PREFIX}{token}:{page.revision}:"
    row = []
    if page.index > 0:
        row.append(InlineKeyboardButton(text="Назад", callback_data=prefix + "back"))
    if page.index + 1 < size:
        row.append(InlineKeyboardButton(text="Далее", callback_data=prefix + "next"))
    return InlineKeyboardMarkup(inline_keyboard=[row])
