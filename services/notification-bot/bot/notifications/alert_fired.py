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

SEVERITY_DISPLAY = {
    "critical": ("🔴", "Критическое"),
    "warning": ("🟡", "Предупреждение"),
    "info": ("🔵", "Информация"),
    "unknown": ("⚪", "Важность не указана"),
}

STATUS_DISPLAY = {
    "firing": ("🔔", "Активно"),
    "resolved": ("✅", "Закрыто"),
    "succeeded": ("❓", "Успешно"),
    "unknown": ("❓", "Не указан"),
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


# All plain fields and fixed labels together fit Telegram's 4096-unit limit.
# Bound before escaping so entities and our HTML tags always remain complete.
_MAX_DESCRIPTION_CHARS = 3500
_TRUNCATION_SUFFIX = "…\n[текст сокращён]"


def _truncate(text: str, limit: int) -> str:
    encoded = text.encode("utf-16-le")
    if len(encoded) <= limit * 2:
        return text
    return encoded[: (limit - len(_TRUNCATION_SUFFIX)) * 2].decode("utf-16-le", errors="ignore") + _TRUNCATION_SUFFIX


def _format_message(payload: dict) -> str:
    name = html.escape(_truncate(str(payload.get("name", "unknown")), 200))
    severity = str(payload.get("severity", "unknown")).lower()
    status = str(payload.get("status", "unknown")).lower()
    severity_marker, severity_label = SEVERITY_DISPLAY.get(severity, ("⚪", severity.upper()))
    status_prefix, status_label = STATUS_DISPLAY.get(status, ("❓", status))
    severity_text = html.escape(_truncate(severity_label, 32))
    status_text = html.escape(_truncate(status_label, 32))
    summary = html.escape(_truncate(str(payload.get("summary") or ""), 250))
    description = html.escape(_truncate(str(payload.get("description") or ""), _MAX_DESCRIPTION_CHARS))

    if name == "DeploymentSucceeded" and status == "succeeded":
        return f"✅ <b>RutCampusTrack успешно развёрнут</b>\n{description}"

    lines = [
        f"{status_prefix} <b>Оповещение: {severity_marker} {severity_text}</b>",
        f"Название: {name}",
        f"Статус: {status_text}",
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
                system_alert=True,  # Explicit exemption of this trusted system producer only.
            )
        )
