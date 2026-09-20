"""Focused authority and failure tests for canonical headman alerts."""

from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import grpc
import pytest

from bot.grpc_client import attendance_pb2
from bot.notifications.headman_alerts import handle_headman_alert

REQUEST_ID = "0123456789abcdef01234567"


def _member(user_id: int, telegram_id: int, *, is_headman: bool) -> SimpleNamespace:
    return SimpleNamespace(user_id=user_id, telegram_id=telegram_id, is_headman=is_headman)


def _lesson(
    lesson_id: int = 101,
    *,
    lesson_number: int = 3,
    subject_name: str = "Математический анализ",
    date: str = "2026-09-08",
) -> attendance_pb2.StudentRequestLesson:
    return attendance_pb2.StudentRequestLesson(
        id=lesson_id,
        lesson_number=lesson_number,
        subject_name=subject_name,
        date=date,
    )


def _summary(
    *,
    kind: int,
    status: int = attendance_pb2.STUDENT_REQUEST_STATUS_PENDING,
    request_id: str = REQUEST_ID,
    lessons: list[attendance_pb2.StudentRequestLesson] | None = None,
) -> attendance_pb2.StudentRequestSummary:
    return attendance_pb2.StudentRequestSummary(
        id=request_id,
        kind=kind,
        status=status,
        origin=attendance_pb2.STUDENT_REQUEST_ORIGIN_MANUAL,
        lessons=lessons or [_lesson()],
    )


def _response(
    *,
    kind: int = attendance_pb2.STUDENT_REQUEST_KIND_EXCUSE,
    status: int = attendance_pb2.STUDENT_REQUEST_STATUS_PENDING,
    group_id: int = 5,
    student_id: int = 10,
    student_name: str = "Канонический студент",
    request_id: str = REQUEST_ID,
    reason: int | None = attendance_pb2.STUDENT_EXCUSE_REASON_ILLNESS,
    comment: str | None = "Канонический комментарий",
    lessons: list[attendance_pb2.StudentRequestLesson] | None = None,
    attachments: list[attendance_pb2.StudentRequestAttachment] | None = None,
) -> attendance_pb2.ResolveRequestNotificationResponse:
    summary = _summary(
        kind=kind,
        status=status,
        request_id=request_id,
        lessons=lessons,
    )
    detail = attendance_pb2.StudentRequestDetail(summary=summary)
    if reason is not None:
        detail.reason = reason
    if comment is not None:
        detail.comment = comment
    if attachments:
        detail.attachments.extend(attachments)
    return attendance_pb2.ResolveRequestNotificationResponse(
        group_id=group_id,
        student_id=student_id,
        student_name=student_name,
        detail=detail,
    )


def _attachment(
    *,
    attachment_id: str = "abcdefabcdefabcdefabcdef",
    name: str = "canonical.pdf",
    content_type: str = "application/pdf",
    size_bytes: int = 3,
    state: int = attendance_pb2.STUDENT_REQUEST_ATTACHMENT_STATE_ACTIVE,
) -> attendance_pb2.StudentRequestAttachment:
    return attendance_pb2.StudentRequestAttachment(
        id=attachment_id,
        name=name,
        content_type=content_type,
        size_bytes=size_bytes,
        state=state,
    )


def _event(
    event_type: str = "excuse.requested",
    *,
    group_id: int = 5,
    request_id: str = REQUEST_ID,
    **spoofed_fields,
) -> dict:
    payload = {"group_id": group_id}
    if event_type == "excuse.requested":
        payload["ticket_id"] = request_id
    else:
        payload["request_id"] = request_id
    payload.update(spoofed_fields)
    return {"event_type": event_type, "payload": payload}


def _clients(
    result: attendance_pb2.ResolveRequestNotificationResponse,
    members: list[SimpleNamespace] | None = None,
):
    attendance_client = MagicMock()
    attendance_client.resolve_request_notification = AsyncMock(return_value=result)
    attendance_client.fetch_excuse_attachment = AsyncMock(
        return_value=attendance_pb2.FetchExcuseAttachmentResponse(
            data=b"abc", filename="server-name.pdf", size=3
        )
    )
    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(
        return_value=members or [_member(20, 220, is_headman=True)]
    )
    academic_client.invalidate = MagicMock()
    return attendance_client, academic_client


async def _run_handler(
    event: dict,
    *,
    attendance_client,
    academic_client,
    bot=None,
):
    bot = bot or MagicMock()
    bot.send_message = AsyncMock(return_value=SimpleNamespace(message_id=1))
    bot.send_document = AsyncMock(return_value=SimpleNamespace(message_id=2))
    tasks = []
    send_queue = MagicMock()

    async def capture(task):
        tasks.append(task)

    send_queue.put = capture
    await handle_headman_alert(
        event,
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
        attendance_client=attendance_client,
    )
    return tasks, bot


@pytest.mark.asyncio
async def test_valid_excuse_uses_canonical_private_fields_and_current_headmen():
    result = _response()
    attendance_client, academic_client = _clients(
        result,
        members=[
            _member(10, 110, is_headman=False),
            _member(20, 220, is_headman=True),
            _member(30, 0, is_headman=True),
        ],
    )

    tasks, bot = await _run_handler(
        _event(
            spoofed_student_name="Поддельное имя",
            excuse_type="other",
            comment="Поддельный комментарий",
            lessons=[{"subject_name": "Поддельный предмет"}],
        ),
        attendance_client=attendance_client,
        academic_client=academic_client,
    )

    assert len(tasks) == 1
    assert attendance_client.resolve_request_notification.await_args.args == (
        attendance_pb2.STUDENT_REQUEST_KIND_EXCUSE,
        REQUEST_ID,
    )
    academic_client.invalidate.assert_called_once_with(5)
    academic_client.get_group_members.assert_awaited_once_with(5)
    await tasks[0].coroutine_factory()
    text = bot.send_message.call_args.kwargs["text"]
    assert "Канонический студент" in text
    assert "Болезнь" in text
    assert "Канонический комментарий" in text
    assert "Поддельное имя" not in text
    assert "Поддельный комментарий" not in text
    assert "Поддельный предмет" not in text
    markup = bot.send_message.call_args.kwargs["reply_markup"]
    assert markup.inline_keyboard[0][0].callback_data == f"ex:approve:{REQUEST_ID}"
    assert markup.inline_keyboard[0][1].callback_data == f"ex:reject:{REQUEST_ID}"


@pytest.mark.asyncio
async def test_wrong_event_group_enqueues_no_private_notification():
    result = _response(group_id=5)
    attendance_client, academic_client = _clients(result)

    tasks, bot = await _run_handler(
        _event(group_id=99),
        attendance_client=attendance_client,
        academic_client=academic_client,
    )

    assert tasks == []
    bot.send_message.assert_not_called()
    academic_client.get_group_members.assert_not_awaited()


@pytest.mark.asyncio
async def test_terminal_request_enqueues_nothing_and_does_not_read_members():
    result = _response(status=attendance_pb2.STUDENT_REQUEST_STATUS_APPROVED)
    attendance_client, academic_client = _clients(result)

    tasks, bot = await _run_handler(
        _event(),
        attendance_client=attendance_client,
        academic_client=academic_client,
    )

    assert tasks == []
    bot.send_message.assert_not_called()
    academic_client.get_group_members.assert_not_awaited()


@pytest.mark.asyncio
async def test_late_checkin_uses_canonical_lesson_and_buttons():
    result = _response(
        kind=attendance_pb2.STUDENT_REQUEST_KIND_LATE_CHECKIN,
        reason=None,
        comment=None,
        lessons=[_lesson(subject_name="Канонический предмет", lesson_number=7)],
    )
    attendance_client, academic_client = _clients(result)

    tasks, bot = await _run_handler(
        _event(
            "late_checkin.requested",
            spoofed_student_name="Поддельный студент",
            subject_name="Поддельный предмет",
            lesson_number=99,
            lesson_date="1900-01-01",
        ),
        attendance_client=attendance_client,
        academic_client=academic_client,
    )

    assert len(tasks) == 1
    await tasks[0].coroutine_factory()
    text = bot.send_message.call_args.kwargs["text"]
    assert "Канонический студент" in text
    assert "Канонический предмет" in text
    assert "№7" in text
    assert "Поддельный" not in text
    markup = bot.send_message.call_args.kwargs["reply_markup"]
    assert markup.inline_keyboard[0][0].callback_data == f"lcr:approve:{REQUEST_ID}"
    assert markup.inline_keyboard[0][1].callback_data == f"lcr:reject:{REQUEST_ID}"


@pytest.mark.asyncio
async def test_revoked_or_unlinked_headmen_receive_nothing():
    result = _response()
    attendance_client, academic_client = _clients(
        result,
        members=[
            _member(20, 0, is_headman=True),
            _member(30, 330, is_headman=False),
        ],
    )

    tasks, bot = await _run_handler(
        _event(),
        attendance_client=attendance_client,
        academic_client=academic_client,
    )

    assert tasks == []
    bot.send_message.assert_not_called()


@pytest.mark.asyncio
async def test_resolve_unavailable_propagates_before_any_queue_task():
    result = _response()
    attendance_client, academic_client = _clients(result)
    error = grpc.aio.AioRpcError(
        code=grpc.StatusCode.UNAVAILABLE,
        initial_metadata=MagicMock(),
        trailing_metadata=MagicMock(),
        details="attendance unavailable",
        debug_error_string="",
    )
    attendance_client.resolve_request_notification = AsyncMock(side_effect=error)
    tasks = []
    send_queue = MagicMock()
    send_queue.put = AsyncMock(side_effect=tasks.append)

    with pytest.raises(grpc.aio.AioRpcError) as exc_info:
        await handle_headman_alert(
            _event(),
            bot=MagicMock(),
            academic_client=academic_client,
            send_queue=send_queue,
            attendance_client=attendance_client,
        )

    assert exc_info.value.code() == grpc.StatusCode.UNAVAILABLE
    assert tasks == []
    academic_client.get_group_members.assert_not_awaited()


@pytest.mark.asyncio
async def test_current_membership_unavailable_propagates_before_any_queue_task():
    result = _response()
    attendance_client, academic_client = _clients(result)
    error = grpc.aio.AioRpcError(
        code=grpc.StatusCode.UNAVAILABLE,
        initial_metadata=MagicMock(),
        trailing_metadata=MagicMock(),
        details="academic unavailable",
        debug_error_string="",
    )
    academic_client.get_group_members = AsyncMock(side_effect=error)
    tasks = []
    send_queue = MagicMock()
    send_queue.put = AsyncMock(side_effect=tasks.append)

    with pytest.raises(grpc.aio.AioRpcError) as exc_info:
        await handle_headman_alert(
            _event(),
            bot=MagicMock(),
            academic_client=academic_client,
            send_queue=send_queue,
            attendance_client=attendance_client,
        )

    assert exc_info.value.code() == grpc.StatusCode.UNAVAILABLE
    assert tasks == []


@pytest.mark.asyncio
@pytest.mark.parametrize("status", [grpc.StatusCode.UNAVAILABLE, grpc.StatusCode.NOT_FOUND])
async def test_attachment_fetch_failures_propagate_before_any_queue_task(status):
    attachment = _attachment()
    result = _response(attachments=[attachment])
    attendance_client, academic_client = _clients(result)
    error = grpc.aio.AioRpcError(
        code=status,
        initial_metadata=MagicMock(),
        trailing_metadata=MagicMock(),
        details="attachment lookup failed",
        debug_error_string="",
    )
    attendance_client.fetch_excuse_attachment = AsyncMock(side_effect=error)
    tasks = []
    send_queue = MagicMock()
    send_queue.put = AsyncMock(side_effect=tasks.append)

    with pytest.raises(grpc.aio.AioRpcError) as exc_info:
        await handle_headman_alert(
            _event(file_name="payload-name.pdf"),
            bot=MagicMock(),
            academic_client=academic_client,
            send_queue=send_queue,
            attendance_client=attendance_client,
        )

    assert exc_info.value.code() == status
    assert tasks == []


@pytest.mark.asyncio
async def test_attachments_are_fetched_for_each_current_headman_before_enqueue():
    attachment = _attachment(name="canonical.pdf")
    result = _response(attachments=[attachment])
    attendance_client, academic_client = _clients(
        result,
        members=[_member(20, 220, is_headman=True), _member(30, 330, is_headman=True)],
    )

    tasks, bot = await _run_handler(
        _event(file_name="payload-name.pdf"),
        attendance_client=attendance_client,
        academic_client=academic_client,
    )

    assert len(tasks) == 2
    assert [call.args for call in attendance_client.fetch_excuse_attachment.await_args_list] == [
        (20, REQUEST_ID, attachment.id),
        (30, REQUEST_ID, attachment.id),
    ]
    assert bot.send_document.await_count == 0
    await tasks[0].coroutine_factory()
    await tasks[1].coroutine_factory()
    assert bot.send_document.await_count == 2
    assert all(call.kwargs["document"].filename == "canonical.pdf" for call in bot.send_document.await_args_list)


@pytest.mark.asyncio
async def test_invalid_canonical_attachment_descriptor_fails_before_any_queue_task():
    result = _response(attachments=[_attachment(state=attendance_pb2.STUDENT_REQUEST_ATTACHMENT_STATE_EXPIRED)])
    attendance_client, academic_client = _clients(result)
    tasks = []
    send_queue = MagicMock()
    send_queue.put = AsyncMock(side_effect=tasks.append)

    with pytest.raises(ValueError, match="attachment descriptor"):
        await handle_headman_alert(
            _event(),
            bot=MagicMock(),
            academic_client=academic_client,
            send_queue=send_queue,
            attendance_client=attendance_client,
        )

    assert tasks == []
    attendance_client.fetch_excuse_attachment.assert_not_awaited()


@pytest.mark.asyncio
async def test_missing_request_id_is_rejected_before_resolve():
    result = _response()
    attendance_client, academic_client = _clients(result)
    event = {"event_type": "excuse.requested", "payload": {"group_id": 5}}

    tasks, bot = await _run_handler(
        event,
        attendance_client=attendance_client,
        academic_client=academic_client,
    )

    assert tasks == []
    attendance_client.resolve_request_notification.assert_not_awaited()
    bot.send_message.assert_not_called()


@pytest.mark.asyncio
async def test_not_found_or_invalid_resolve_is_terminal_noqueue():
    result = _response()
    attendance_client, academic_client = _clients(result)
    for status in (grpc.StatusCode.NOT_FOUND, grpc.StatusCode.INVALID_ARGUMENT):
        error = grpc.aio.AioRpcError(
            code=status,
            initial_metadata=MagicMock(),
            trailing_metadata=MagicMock(),
            details="terminal lookup",
            debug_error_string="",
        )
        attendance_client.resolve_request_notification = AsyncMock(side_effect=error)
        tasks, bot = await _run_handler(
            _event(),
            attendance_client=attendance_client,
            academic_client=academic_client,
        )
        assert tasks == []
        bot.send_message.assert_not_called()
        academic_client.get_group_members.assert_not_awaited()
