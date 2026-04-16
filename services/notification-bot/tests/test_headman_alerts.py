"""Tests for the headman alert notification handler."""

from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.notifications.headman_alerts import handle_headman_alert


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
    ticket_id: str = None,
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
    request_id: str = None,
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
async def test_late_checkin_text_contains_student_name_from_payload():
    """late_checkin.requested uses student_name from payload when present."""
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
    """late_checkin.requested shows subject_name and lesson_number when payload provides them."""
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
async def test_headman_alert_student_name_fallback_from_members():
    """When payload has no student_name, resolves name from group members by user_id."""
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

    # user_id=10 matches the member with display_name "Известный Студент"
    # No student_name in payload
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
