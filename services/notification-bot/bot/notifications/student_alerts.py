"""Handler for student alert events — sends direct notifications to individual students.

Handles the excuse.decided event (59-06 / D-28): when a headman approves or rejects
a student's excuse ticket, the student receives a Telegram message with the verdict
and the headman's optional comment.

Pattern mirrors headman_alerts.handle_headman_alert — same signature, same
send_queue.put(SendTask(...)) dispatch discipline, graceful KeyError handling
(threat T-59-06-03: malformed payload must not crash the consumer).
"""

import logging
from datetime import date

from aiogram import Bot

from bot.notifications.presentation import message_options
from bot.services.notification_prefs import type_for_event
from bot.services.send_queue import SendTask, TelegramSendQueue

logger = logging.getLogger(__name__)


def _excluded_lessons(payload: dict) -> list:
    lessons = payload.get("excluded_lessons")
    if isinstance(lessons, list) and lessons:
        return lessons
    ids = payload.get("excluded_lesson_ids")
    if isinstance(ids, list) and ids:
        return ids
    occurrences = payload.get("excluded_occurrence_ids")
    # An occurrence ID cannot be displayed as a lesson ID.
    return [{} for _ in occurrences] if isinstance(occurrences, list) else []


def _positive_integer(value) -> str:
    return str(value) if isinstance(value, int) and not isinstance(value, bool) and 0 < value <= 2**63 - 1 else ""


def _bounded_text(value: str, limit: int) -> str:
    line = " ".join(value.split())
    return line[:limit] + "…" if len(line) > limit else line


def _excluded_lesson_ref(raw) -> str:
    lesson = raw if isinstance(raw, dict) else {}
    lesson_id = _positive_integer(lesson.get("lesson_id") if lesson else raw)
    parts = []
    lesson_date = lesson.get("date")
    if isinstance(lesson_date, str) and len(lesson_date) == 10:
        try:
            parsed = date.fromisoformat(lesson_date).isoformat()
            if parsed == lesson_date:
                parts.append(parsed)
        except ValueError:
            pass  # Malformed optional metadata must not prevent decision delivery.
    number = _positive_integer(lesson.get("lesson_number"))
    if number:
        parts.append(f"№{number}")
    if not parts:
        parts.append(f"Пара ID {lesson_id}" if lesson_id else "Данные пары недоступны")
    subject = lesson.get("subject_name")
    if isinstance(subject, str) and subject.strip():
        parts.append(_bounded_text(subject, 80))
    return " · ".join(parts)


def _partial_approval_text(lessons: list) -> str:
    lines = ["✅ Уважительная причина одобрена частично", "", "Исключённые пары:"]
    lines.extend(f"• {_excluded_lesson_ref(lesson)}" for lesson in lessons[:3])
    if len(lessons) > 3:
        lines.append(f"Ещё исключено: {len(lessons) - 3}")
    lines.append("Полный состав решения — в заявке в приложении.")
    return "\n".join(lines)


async def handle_student_alert(
    event: dict,
    bot: Bot,
    academic_client,
    send_queue: TelegramSendQueue,
    **kwargs,
) -> None:
    """Notify a single student about a decision on their excuse ticket.

    Reads payload.user_id, payload.status ('approved' | 'rejected'),
    payload.decision_comment (optional ru text). Resolves the student's
    telegram_id via AcademicGrpcClient.get_user_by_id (gRPC). If the student
    has no linked Telegram account, logs a warning and exits silently. Lookup
    failures propagate to the consumer boundary so transient failures can be
    rejected/dead-lettered.
    """
    event_type = event.get("event_type")
    payload = event.get("payload", {})

    try:
        user_id = payload["user_id"]
        payload["status"]
    except KeyError as exc:
        logger.warning("student alert event missing required field: %s", exc)
        return

    if type_for_event(event) is None:
        return
    options = message_options(event, config=kwargs.get("config"))

    # Resolve student's telegram_id via gRPC (no local cache — fires rarely).
    # Let lookup failures reach the consumer boundary for reject/DLQ handling.
    user = await academic_client.get_user_by_id(user_id)

    telegram_id = getattr(user, "telegram_id", 0) if user is not None else 0
    if not telegram_id:
        logger.warning(
            "No telegram_id for user_id=%s (%s), skipping notification",
            user_id,
            event_type,
        )
        return

    await send_queue.put(
        SendTask(
            coroutine_factory=lambda chat=telegram_id: bot.send_message(chat_id=chat, **options),
            user_id=user_id,
            chat_id=telegram_id,
            category="tickets",
            notification_type=type_for_event(event),
        )
    )
