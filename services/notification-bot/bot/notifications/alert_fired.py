"""M04 Группа 9 — handler для alert.fired событий от Alertmanager.

Forwarding chain: Prometheus alert rule fires → Alertmanager routing →
notification-web webhook POST /internal/alert → RabbitMQ alert.fired →
*этот handler* → Telegram message админу(ам).

Список админов берём из ``Settings.admin_telegram_ids`` (string, comma-
separated) — "0" (default) означает "not configured, skip".
"""

from __future__ import annotations

import html
import logging
from typing import Iterable

from aiogram import Bot

from bot.services.send_queue import SendTask, TelegramSendQueue

logger = logging.getLogger(__name__)

SEVERITY_EMOJI = {
    "critical": "🔴",
    "warning": "🟡",
    "info": "🔵",
}

STATUS_EMOJI = {
    "firing": "🔔",
    "resolved": "✅",
}


def _parse_admin_ids(raw: str | None) -> list[int]:
    if not raw:
        return []
    out: list[int] = []
    for token in raw.split(","):
        token = token.strip()
        if not token:
            continue
        try:
            value = int(token)
            if value > 0:
                out.append(value)
        except ValueError:
            logger.warning("Invalid admin_telegram_ids token: %r", token)
    return out


# G11 M4 fix: Telegram sendMessage лимит 4096. Длинный Alertmanager
# description (runbook + stack trace) разрывал формирование сообщения.
# Ограничение 3500 оставляет буфер на header + summary + markup.
_MAX_DESCRIPTION_CHARS = 3500
_TRUNCATION_SUFFIX = "…\n<i>[truncated]</i>"


def _truncate(text: str, limit: int) -> str:
    if len(text) <= limit:
        return text
    return text[: limit - len(_TRUNCATION_SUFFIX)] + _TRUNCATION_SUFFIX


def _format_message(payload: dict) -> str:
    name = html.escape(str(payload.get("name", "unknown")))
    severity = str(payload.get("severity", "unknown")).lower()
    status = str(payload.get("status", "unknown")).lower()
    summary = html.escape(str(payload.get("summary") or ""))
    description = _truncate(
        html.escape(str(payload.get("description") or "")),
        _MAX_DESCRIPTION_CHARS,
    )

    status_prefix = STATUS_EMOJI.get(status, "❓")
    severity_marker = SEVERITY_EMOJI.get(severity, "⚪")

    lines = [
        f"{status_prefix} <b>Alert: {severity_marker} {html.escape(severity.upper())}</b>",
        f"Название: {name}",
        f"Статус: {html.escape(status)}",
    ]
    if summary:
        lines.extend(["", f"<b>Кратко:</b>\n{summary}"])
    if description:
        lines.extend(["", f"<b>Описание:</b>\n{description}"])
    return "\n".join(lines)


async def handle_alert_fired(
    event: dict,
    *,
    bot: Bot,
    send_queue: TelegramSendQueue,
    admin_ids: Iterable[int],
) -> None:
    payload = event.get("payload") or {}
    admins = list(admin_ids)
    if not admins:
        logger.debug("alert.fired dropped: admin_telegram_ids not configured")
        return

    text = _format_message(payload)
    for chat_id in admins:
        # Используем send_queue чтобы уважать rate-limit Telegram и единую
        # очередь отправки, а не bot.send_message напрямую. Closure
        # "замораживает" chat_id/text per-loop-iter.
        cid = chat_id

        async def _send(cid=cid, text=text):
            return await bot.send_message(
                chat_id=cid,
                text=text,
                parse_mode="HTML",
                disable_web_page_preview=True,
            )

        await send_queue.put(
            SendTask(
                coroutine_factory=_send,
                chat_id=cid,
                # category=None — системное сообщение: prefs не применяется,
                # глобальный notification toggle не отключает.
            )
        )
