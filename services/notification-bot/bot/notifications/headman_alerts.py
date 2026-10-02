"""Headman request alerts built from canonical Attendance request context."""

import logging
from typing import Any

import grpc
from aiogram import Bot
from aiogram.types import BufferedInputFile, InlineKeyboardButton, InlineKeyboardMarkup

from bot.grpc_client import attendance_pb2
from bot.services.send_queue import SendTask, TelegramSendQueue

logger = logging.getLogger(__name__)

# Java Attendance caps each retained attachment at 10 MiB.
MAX_FORWARDED_FILE_BYTES = 10 * 1024 * 1024

_EVENT_KIND = {
    "excuse.requested": attendance_pb2.STUDENT_REQUEST_KIND_EXCUSE,
    "late_checkin.requested": attendance_pb2.STUDENT_REQUEST_KIND_LATE_CHECKIN,
}
_EXCUSE_REASON_LABELS = {
    attendance_pb2.STUDENT_EXCUSE_REASON_ILLNESS: "Болезнь",
    attendance_pb2.STUDENT_EXCUSE_REASON_MEDICAL_EXAMINATION: "Медицинское обследование",
    attendance_pb2.STUDENT_EXCUSE_REASON_COMPETITION_PARTICIPATION: "Участие в соревнованиях",
    attendance_pb2.STUDENT_EXCUSE_REASON_FAMILY_CIRCUMSTANCES: "Семейные обстоятельства",
    attendance_pb2.STUDENT_EXCUSE_REASON_OTHER: "Другое",
}
_ACTIVE_ATTACHMENT_STATE = attendance_pb2.STUDENT_REQUEST_ATTACHMENT_STATE_ACTIVE
_PENDING_STATUS = attendance_pb2.STUDENT_REQUEST_STATUS_PENDING
_TERMINAL_LOOKUP_CODES = frozenset({grpc.StatusCode.NOT_FOUND, grpc.StatusCode.INVALID_ARGUMENT})


async def handle_headman_alert(
    event: dict,
    bot: Bot,
    academic_client,
    send_queue: TelegramSendQueue,
    request_tracker=None,
    **kwargs,
) -> None:
    """Queue a pending request alert for current headmen of its stored group.

    The event kind and request id are lookup keys only. Every private field,
    recipient group and action button comes from ``ResolveRequestNotification``
    and a fresh Academic membership read.
    """
    event_type = event.get("event_type") if isinstance(event, dict) else None
    payload = event.get("payload") if isinstance(event, dict) else None
    attendance_client = kwargs.get("attendance_client")
    context = await _resolve_notification_context(
        event_type,
        payload,
        attendance_client=attendance_client,
        academic_client=academic_client,
    )
    if context is None:
        return

    result, headmen = context
    detail = result.detail
    request_id = str(detail.summary.id)
    tracking_kind = "excuse" if event_type == "excuse.requested" else "late_checkin"
    tracking_id = request_id

    if event_type == "excuse.requested":
        text = _build_excuse_text(result)
        if text is None:
            return
        reply_markup = InlineKeyboardMarkup(
            inline_keyboard=[
                [
                    InlineKeyboardButton(text="✅ Одобрить", callback_data=f"ex:approve:{request_id}"),
                    InlineKeyboardButton(text="❌ Отклонить", callback_data=f"ex:reject:{request_id}"),
                ]
            ]
        )
    else:
        text = _build_late_checkin_text(result)
        if text is None:
            return
        reply_markup = InlineKeyboardMarkup(
            inline_keyboard=[
                [
                    InlineKeyboardButton(text="✅ Подтвердить", callback_data=f"lcr:approve:{request_id}"),
                    InlineKeyboardButton(text="❌ Отклонить", callback_data=f"lcr:reject:{request_id}"),
                ]
            ]
        )

    attachments_by_user: dict[int, list[tuple[bytes, str]]] = {}
    descriptors = list(detail.attachments)
    if descriptors:
        if attendance_client is None:
            return
        for headman in headmen:
            actor_id = _positive_int(getattr(headman, "user_id", None))
            if actor_id is None:
                return
            fetched: list[tuple[bytes, str]] = []
            for descriptor in descriptors:
                if not _valid_attachment_descriptor(descriptor):
                    return
                # Fetch failures deliberately escape the handler. The event
                # consumer then releases the processing lease and sends the
                # message to DLQ; silently omitting an attachment would ACK a
                # private notification that has not been fully materialized.
                response = await attendance_client.fetch_excuse_attachment(
                    actor_id,
                    request_id,
                    str(descriptor.id),
                )
                data = bytes(getattr(response, "data", b""))
                if not data or len(data) > MAX_FORWARDED_FILE_BYTES:
                    raise ValueError("canonical excuse attachment has an invalid size")
                if len(data) != descriptor.size_bytes:
                    raise ValueError("canonical excuse attachment size does not match its descriptor")
                # The filename and all displayed attachment metadata come
                # from the canonical descriptor, never from event payload.
                fetched.append((data, str(descriptor.name)))
            attachments_by_user[actor_id] = fetched

    def _build_on_sent(chat_id_value: int, owner_id: int):
        if request_tracker is None:
            return None

        async def _on_sent(result_value):
            message_id = getattr(result_value, "message_id", None)
            if message_id is None:
                return
            await request_tracker.add(tracking_kind, tracking_id, chat_id_value, int(message_id), owner_id)

        return _on_sent

    for headman in headmen:
        actor_id = _positive_int(getattr(headman, "user_id", None))
        telegram_id = _positive_int(getattr(headman, "telegram_id", None))
        if actor_id is None or telegram_id is None:
            continue
        headman_attachments = attachments_by_user.get(actor_id, [])
        if headman_attachments:
            for file_bytes, file_name in headman_attachments:
                # Telegram caption limit is 1024 chars.
                caption = text if len(text) <= 1024 else text[:1020] + "…"

                async def _send_document(
                    h=headman,
                    markup=reply_markup,
                    payload_bytes=file_bytes,
                    payload_name=file_name,
                    payload_caption=caption,
                ):
                    return await bot.send_document(
                        chat_id=h.telegram_id,
                        document=BufferedInputFile(payload_bytes, filename=payload_name),
                        caption=payload_caption,
                        reply_markup=markup,
                    )

                await send_queue.put(
                    SendTask(
                        coroutine_factory=_send_document,
                        user_id=actor_id,
                        chat_id=telegram_id,
                        on_sent=_build_on_sent(telegram_id, actor_id),
                        category="tickets",
                    )
                )
        else:
            await send_queue.put(
                SendTask(
                    coroutine_factory=lambda chat_id=telegram_id, markup=reply_markup: bot.send_message(
                        chat_id=chat_id,
                        text=text,
                        reply_markup=markup,
                    ),
                    user_id=actor_id,
                    chat_id=telegram_id,
                    on_sent=_build_on_sent(telegram_id, actor_id),
                    category="tickets",
                )
            )


async def _resolve_notification_context(
    event_type: str | None,
    payload: Any,
    *,
    attendance_client,
    academic_client,
) -> tuple[Any, list[Any]] | None:
    kind = _EVENT_KIND.get(event_type)
    if kind is None or not isinstance(payload, dict) or attendance_client is None:
        return None

    request_field = "ticket_id" if event_type == "excuse.requested" else "request_id"
    request_id = payload.get(request_field)
    if not isinstance(request_id, str) or not request_id or request_id != request_id.strip():
        return None
    payload_group_id = _positive_int(payload.get("group_id"))
    if payload_group_id is None:
        return None

    try:
        result = await attendance_client.resolve_request_notification(kind, request_id)
    except grpc.aio.AioRpcError as error:
        if error.code() in _TERMINAL_LOOKUP_CODES:
            return None
        raise

    if result is None:
        return None
    group_id = _positive_int(getattr(result, "group_id", None))
    student_id = _positive_int(getattr(result, "student_id", None))
    student_name = getattr(result, "student_name", None)
    detail = getattr(result, "detail", None)
    summary = getattr(detail, "summary", None)
    if (
        group_id is None
        or student_id is None
        or not isinstance(student_name, str)
        or not student_name.strip()
        or detail is None
        or summary is None
        or not summary.id
        or summary.id != request_id
        or summary.kind != kind
        or summary.status != _PENDING_STATUS
        or not summary.lessons
        or any(_positive_int(getattr(lesson, "id", None)) is None for lesson in summary.lessons)
        or payload_group_id != group_id
    ):
        return None
    if event_type == "excuse.requested":
        if not _has_field(detail, "reason") or detail.reason not in _EXCUSE_REASON_LABELS:
            return None

    try:
        # AcademicGrpcClient caches members for ordinary reminders. Invalidate
        # this group before an authority-sensitive request notification so a
        # revoked headman is not treated as current.
        invalidate = getattr(academic_client, "invalidate", None)
        if callable(invalidate):
            invalidate(group_id)
        members = await academic_client.get_group_members(group_id)
    except grpc.aio.AioRpcError as error:
        if error.code() in _TERMINAL_LOOKUP_CODES:
            return None
        raise

    headmen = [
        member
        for member in (members or [])
        if bool(getattr(member, "is_headman", False))
        and _positive_int(getattr(member, "user_id", None)) is not None
        and _positive_int(getattr(member, "telegram_id", None)) is not None
    ]
    if not headmen:
        return None
    return result, headmen


def _build_excuse_text(result: Any) -> str | None:
    detail = result.detail
    summary = detail.summary
    reason_label = _EXCUSE_REASON_LABELS.get(detail.reason)
    if reason_label is None:
        raise ValueError("canonical excuse reason is invalid")
    text = f"🧾 Запрос на уважительную причину\n\nСтудент: {result.student_name}\nТип: {reason_label}"
    text += "\n\nПары:\n" + "\n".join(f"• {_format_lesson(lesson)}" for lesson in summary.lessons)
    if _has_field(detail, "comment") and detail.comment.strip():
        text += f"\n\nКомментарий:\n{detail.comment}"
    if detail.attachments:
        metadata: list[str] = []
        for descriptor in detail.attachments:
            if not _valid_attachment_descriptor(descriptor):
                raise ValueError("canonical excuse attachment descriptor is invalid")
            metadata.append(
                f"• {descriptor.name} ({descriptor.content_type}, {descriptor.size_bytes} Б)"
            )
        text += "\n\nВложения:\n" + "\n".join(metadata)
    return text


def _build_late_checkin_text(result: Any) -> str | None:
    summary = result.detail.summary
    if not summary.lessons:
        return None
    text = f"✅ Запрос подтверждения присутствия\n\nСтудент: {result.student_name}"
    text += "\n\nПары:\n" + "\n".join(f"• {_format_lesson(lesson)}" for lesson in summary.lessons)
    return text


def _valid_attachment_descriptor(descriptor: Any) -> bool:
    return bool(
        descriptor is not None
        and isinstance(getattr(descriptor, "id", None), str)
        and descriptor.id
        and isinstance(getattr(descriptor, "name", None), str)
        and descriptor.name.strip()
        and isinstance(getattr(descriptor, "content_type", None), str)
        and descriptor.content_type
        and descriptor.state == _ACTIVE_ATTACHMENT_STATE
        and 0 < descriptor.size_bytes <= MAX_FORWARDED_FILE_BYTES
    )


def _has_field(message: Any, field: str) -> bool:
    try:
        return message.HasField(field)
    except (AttributeError, ValueError):
        value = getattr(message, field, None)
        return value is not None and value != ""


def _positive_int(value: Any) -> int | None:
    if isinstance(value, bool):
        return None
    try:
        result = int(value)
    except (TypeError, ValueError):
        return None
    return result if result > 0 else None


def _format_lesson(lesson: Any) -> str:
    """Format one lesson using only fields from the canonical proto detail."""
    lesson_number = getattr(lesson, "lesson_number", 0)
    subject_name = getattr(lesson, "subject_name", "")
    date_value = getattr(lesson, "date", "")
    parts: list[str] = []
    if lesson_number:
        parts.append(f"№{lesson_number}")
    if subject_name:
        parts.append(str(subject_name))
    if date_value:
        parts.append(str(date_value))
    return " ".join(parts) if parts else f"Пара #{getattr(lesson, 'id', '?')}"
