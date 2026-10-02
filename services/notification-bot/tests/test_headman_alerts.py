"""Tests for the headman alert notification handler."""

from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.grpc_client import attendance_pb2 as pb
from bot.notifications.headman_alerts import handle_headman_alert as _handle_headman_alert


async def handle_headman_alert(event, **kwargs):
    """Legacy test inputs describe a fixture; deliver actual canonical RPC detail."""
    payload = event["payload"]
    excuse = event["event_type"] == "excuse.requested"
    members = kwargs["academic_client"].get_group_members.return_value
    kwargs["academic_client"].get_current_group_members = AsyncMock(return_value=members)
    name = payload.get("student_name") or next(
        (member.display_name for member in members if member.user_id == payload["user_id"]), "Студент"
    )
    detail = pb.StudentRequestDetail(summary=pb.StudentRequestSummary(
        id=payload["ticket_id" if excuse else "request_id"],
        kind=pb.STUDENT_REQUEST_KIND_EXCUSE if excuse else pb.STUDENT_REQUEST_KIND_LATE_CHECKIN,
        status=pb.STUDENT_REQUEST_STATUS_PENDING,
        lessons=[pb.StudentRequestLesson(id=payload.get("lesson_id", 101),
            date=payload.get("lesson_date", ""), subject_name=payload.get("subject_name", ""),
            lesson_number=payload.get("lesson_number", 0))],
    ))
    if excuse:
        detail.reason = pb.STUDENT_EXCUSE_REASON_ILLNESS
    attendance = MagicMock()
    attendance.resolve_request_notification = AsyncMock(return_value=pb.ResolveRequestNotificationResponse(
        group_id=payload["group_id"], student_id=payload["user_id"], student_name=name, detail=detail,
    ))
    await _handle_headman_alert(event, attendance_client=attendance, **kwargs)


def _make_member(user_id: int, telegram_id: int, is_headman: bool = False, display_name: str = "Member"):
    m = MagicMock()
    m.user_id = user_id
    m.telegram_id = telegram_id
    m.is_headman = is_headman
    m.display_name = display_name
    return m


def _make_excuse_event(
    user_id: int = 10,
    group_id: int = 5,
    excuse_type: str = "illness",
    ticket_id: str = "excuse-fixture-1",
    lesson_ids: list = None,
    has_attachments: bool = False,
    student_name: str = None,
):
    payload = {
        "user_id": user_id,
        "group_id": group_id,
        "excuse_type": excuse_type,
        "has_attachments": has_attachments,
    }
    if ticket_id is not None:
        payload["ticket_id"] = ticket_id
    if lesson_ids is not None:
        payload["lesson_ids"] = lesson_ids
    if student_name is not None:
        payload["student_name"] = student_name
    return {
        "event_type": "excuse.requested",
        "payload": payload,
    }


def _make_late_checkin_event(
    user_id: int = 10,
    group_id: int = 5,
    lesson_id: int = 101,
    student_name: str = None,
    lesson_date: str = None,
    subject_name: str = None,
    lesson_number: int = None,
    request_id: str = "late-fixture-1",
):
    payload = {
        "user_id": user_id,
        "group_id": group_id,
        "lesson_id": lesson_id,
    }
    if student_name is not None:
        payload["student_name"] = student_name
    if lesson_date is not None:
        payload["lesson_date"] = lesson_date
    if subject_name is not None:
        payload["subject_name"] = subject_name
    if lesson_number is not None:
        payload["lesson_number"] = lesson_number
    if request_id is not None:
        payload["request_id"] = request_id
    return {
        "event_type": "late_checkin.requested",
        "payload": payload,
    }


@pytest.mark.asyncio
async def test_excuse_requested_sends_only_to_headman():
    """excuse.requested is sent only to headman (is_headman=True), not to regular students."""
    members = [
        _make_member(user_id=1, telegram_id=111, is_headman=False, display_name="Обычный студент"),
        _make_member(user_id=2, telegram_id=222, is_headman=True, display_name="Староста"),
    ]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=members)

    captured_tasks = []
    send_queue = MagicMock()

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_headman_alert(
        _make_excuse_event(user_id=1, excuse_type="illness"),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    # Only 1 headman should receive the message
    assert len(captured_tasks) == 1


@pytest.mark.asyncio
async def test_excuse_requested_text_contains_student_name_and_excuse_type():
    """excuse.requested message text contains student name and excuse type."""
    members = [
        _make_member(user_id=1, telegram_id=111, is_headman=False, display_name="Иван Иванов"),
        _make_member(user_id=2, telegram_id=222, is_headman=True, display_name="Пётр Петров"),
    ]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=members)

    captured_tasks = []
    send_queue = MagicMock()

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_headman_alert(
        _make_excuse_event(user_id=1, group_id=5, excuse_type="illness", student_name="Иван Иванов"),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    assert len(captured_tasks) == 1
    await captured_tasks[0].coroutine_factory()

    call_kwargs = bot.send_message.call_args.kwargs
    text = call_kwargs.get("text", "")
    assert "Иван Иванов" in text
    # handler localizes the enum to a Russian label (headman_alerts._excuse_type_label)
    assert "Болезнь" in text


@pytest.mark.asyncio
async def test_late_checkin_requested_sends_only_to_headman():
    """late_checkin.requested is sent only to headman."""
    members = [
        _make_member(user_id=1, telegram_id=111, is_headman=False),
        _make_member(user_id=2, telegram_id=222, is_headman=True, display_name="Алексей Старостин"),
    ]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=members)

    captured_tasks = []
    send_queue = MagicMock()

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_headman_alert(
        _make_late_checkin_event(user_id=1, student_name="Василий Васильев"),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    assert len(captured_tasks) == 1


@pytest.mark.asyncio
async def test_late_checkin_text_contains_canonical_student_name():
    """The Attendance request fixture supplies the authoritative student name."""
    members = [
        _make_member(user_id=1, telegram_id=111, is_headman=False, display_name="Другое имя"),
        _make_member(user_id=2, telegram_id=222, is_headman=True, display_name="Старостин"),
    ]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=members)

    captured_tasks = []
    send_queue = MagicMock()

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_headman_alert(
        _make_late_checkin_event(user_id=1, student_name="Василий Васильев", lesson_date="2024-03-15"),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    assert len(captured_tasks) == 1
    await captured_tasks[0].coroutine_factory()

    call_kwargs = bot.send_message.call_args.kwargs
    text = call_kwargs.get("text", "")
    assert "Василий Васильев" in text
    # lesson_date should be present
    assert "2024-03-15" in text


@pytest.mark.asyncio
async def test_late_checkin_text_contains_subject_and_lesson_number():
    """late_checkin.requested shows subject and number from the canonical request fixture."""
    members = [
        _make_member(user_id=1, telegram_id=111, is_headman=False),
        _make_member(user_id=2, telegram_id=222, is_headman=True, display_name="Старостин"),
    ]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=members)

    captured_tasks = []
    send_queue = MagicMock()

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_headman_alert(
        _make_late_checkin_event(
            user_id=1,
            student_name="Василий Васильев",
            lesson_date="2024-03-15",
            subject_name="Математический анализ",
            lesson_number=3,
        ),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    assert len(captured_tasks) == 1
    await captured_tasks[0].coroutine_factory()

    text = bot.send_message.call_args.kwargs.get("text", "")
    assert "Математический анализ" in text
    assert "№3" in text


@pytest.mark.asyncio
async def test_headman_alert_student_name_from_canonical_request_fixture():
    """Canonical Attendance detail provides the name, independently of event metadata."""
    members = [
        _make_member(user_id=10, telegram_id=111, is_headman=False, display_name="Известный Студент"),
        _make_member(user_id=2, telegram_id=222, is_headman=True, display_name="Старостин"),
    ]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=members)

    captured_tasks = []
    send_queue = MagicMock()

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    # Fixture resolves user_id=10 to the canonical request's student name.
    await handle_headman_alert(
        _make_late_checkin_event(user_id=10),  # no student_name
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    assert len(captured_tasks) == 1
    await captured_tasks[0].coroutine_factory()

    call_kwargs = bot.send_message.call_args.kwargs
    text = call_kwargs.get("text", "")
    assert "Известный Студент" in text


@pytest.mark.asyncio
async def test_headman_alert_no_headman_with_telegram_skips_send():
    """When no headman has telegram_id, send_queue.put is never called."""
    members = [
        _make_member(user_id=1, telegram_id=111, is_headman=False),
        _make_member(user_id=2, telegram_id=0, is_headman=True),  # headman but no telegram
    ]

    bot = MagicMock()
    bot.send_message = AsyncMock()

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=members)

    captured_tasks = []
    send_queue = MagicMock()

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    await handle_headman_alert(
        _make_excuse_event(user_id=1),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
    )

    assert len(captured_tasks) == 0
    bot.send_message.assert_not_called()
