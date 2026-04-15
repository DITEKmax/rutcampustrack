"""Handler for student alert events — sends direct notifications to individual students.

Handles the excuse.decided event (59-06 / D-28): when a headman approves or rejects
a student's excuse ticket, the student receives a Telegram message with the verdict
and the headman's optional comment.

Pattern mirrors headman_alerts.handle_headman_alert — same signature, same
send_queue.put(SendTask(...)) dispatch discipline, graceful KeyError handling
(threat T-59-06-03: malformed payload must not crash the consumer).
"""

import logging

from aiogram import Bot

from bot.services.send_queue import SendTask, TelegramSendQueue

logger = logging.getLogger(__name__)


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
    has no linked Telegram account, logs a warning and exits silently —
    consumer must still ack the message (no infinite requeue).
    """
    event_type = event.get("event_type")
    payload = event.get("payload", {})

    try:
        user_id = payload["user_id"]
        status = payload["status"]
    except KeyError as exc:
        logger.warning("student alert event missing required field: %s", exc)
        return

    decision_comment = payload.get("decision_comment") or ""

    if event_type == "late_checkin.decided":
        if status == "approved":
            text = "✅ Староста подтвердил ваше присутствие на паре."
        elif status == "rejected":
            text = "❌ Староста отклонил запрос на подтверждение присутствия."
        else:
            logger.debug("late_checkin.decided with unexpected status=%s", status)
            return
    elif status == "approved":
        text = "✅ Ваш запрос на уважительную причину одобрен."
        if decision_comment:
            text += f"\n\nКомментарий: {decision_comment}"
    elif status == "rejected":
        comment_line = decision_comment if decision_comment else "без комментария"
        text = f"❌ Ваш запрос на уважительную причину отклонён.\n\nКомментарий: {comment_line}"
    else:
        logger.debug(
            "handle_student_alert called with unexpected status=%s event_type=%s",
            status,
            event_type,
        )
        return

    # Resolve student's telegram_id via gRPC (no local cache — fires rarely).
    try:
        user = await academic_client.get_user_by_id(user_id)
    except Exception as exc:
        logger.warning(
            "Failed to resolve user_id=%s for %s: %s",
            user_id,
            event_type,
            exc,
        )
        return

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
            coroutine_factory=lambda chat=telegram_id, t=text: bot.send_message(chat_id=chat, text=t),
            user_id=user_id,
            chat_id=telegram_id,
        )
    )
