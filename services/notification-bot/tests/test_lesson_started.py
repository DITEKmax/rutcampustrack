"""Tests for the lesson.started notification handler."""

from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.notifications.lesson_started import handle_lesson_started


def _make_student(user_id: int, telegram_id: int, is_headman: bool = False):
    s = MagicMock()
    s.user_id = user_id
    s.telegram_id = telegram_id
    s.is_headman = is_headman
    return s


def _make_subjects_response(subject_name: str = "Математика"):
    resp = MagicMock()
    resp.subjects = [MagicMock(subject_name=subject_name)]
    return resp


def _make_event(
    lesson_id: int = 101,
    group_id: int = 5,
    subject_id: int = 42,
    start_time: str = "09:00",
    end_time: str = "10:30",
    room: str = "А-305",
):
    return {
        "event_type": "lesson.started",
        "payload": {
            "lesson_id": lesson_id,
            "group_id": group_id,
            "subject_id": subject_id,
            "start_time": start_time,
            "end_time": end_time,
            "room": room,
        },
    }


@pytest.mark.asyncio
async def test_lesson_started_sends_to_students_with_telegram_id():
    """Only students with telegram_id > 0 receive a message; telegram_id=0 is skipped."""
    students = [
        _make_student(user_id=1, telegram_id=111),
        _make_student(user_id=2, telegram_id=0),  # should be skipped
        _make_student(user_id=3, telegram_id=333, is_headman=True),
    ]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=999))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response("Математика"))

    send_queue = MagicMock()
    captured_tasks = []

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put

    redis_client = MagicMock()
    redis_client.add_message_id = AsyncMock()

    config = MagicMock()
    config.mini_app_url = "https://t.me/RutTrackBot/checkin"

    await handle_lesson_started(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
        redis_client=redis_client,
        config=config,
    )

    # 2 students have telegram_id > 0
    assert len(captured_tasks) == 2

    # Execute each coroutine_factory to trigger the actual send+store
    for task in captured_tasks:
        await task.coroutine_factory()

    assert bot.send_message.call_count == 2
    assert redis_client.add_message_id.call_count == 2


@pytest.mark.asyncio
async def test_lesson_started_message_contains_inline_keyboard_with_web_app():
    """Sent message has InlineKeyboardMarkup with WebAppInfo button."""
    from aiogram.types import InlineKeyboardMarkup, WebAppInfo

    students = [_make_student(user_id=1, telegram_id=111)]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=42))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response("Физика"))

    send_queue = MagicMock()
    captured_tasks = []

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put
    redis_client = MagicMock()
    redis_client.add_message_id = AsyncMock()

    config = MagicMock()
    config.mini_app_url = "https://t.me/RutTrackBot/checkin"

    await handle_lesson_started(
        _make_event(lesson_id=77),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
        redis_client=redis_client,
        config=config,
    )

    assert len(captured_tasks) == 1
    await captured_tasks[0].coroutine_factory()

    _, kwargs = bot.send_message.call_args
    keyboard = kwargs.get("reply_markup") or bot.send_message.call_args[1].get("reply_markup")
    assert isinstance(keyboard, InlineKeyboardMarkup)

    # Verify the button has WebAppInfo with the correct URL
    button = keyboard.inline_keyboard[0][0]
    assert isinstance(button.web_app, WebAppInfo)
    assert "lesson_id=77" in button.web_app.url
    assert "checkin" in button.web_app.url


@pytest.mark.asyncio
async def test_lesson_started_stores_message_id_in_redis():
    """add_message_id is called for each successfully sent message."""
    students = [
        _make_student(user_id=10, telegram_id=1010),
        _make_student(user_id=20, telegram_id=2020),
    ]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=555))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response())

    send_queue = MagicMock()
    captured_tasks = []

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put
    redis_client = MagicMock()
    redis_client.add_message_id = AsyncMock()

    config = MagicMock()
    config.mini_app_url = "https://t.me/RutTrackBot/checkin"

    event = _make_event(lesson_id=200)
    await handle_lesson_started(
        event,
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
        redis_client=redis_client,
        config=config,
    )

    for task in captured_tasks:
        await task.coroutine_factory()

    redis_client.add_message_id.assert_any_call(200, 10, 555)
    redis_client.add_message_id.assert_any_call(200, 20, 555)


@pytest.mark.asyncio
async def test_lesson_started_resolves_subject_name():
    """Subject name from gRPC appears in the message text."""
    students = [_make_student(user_id=1, telegram_id=111)]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(return_value=_make_subjects_response("Линейная алгебра"))

    send_queue = MagicMock()
    captured_tasks = []

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put
    redis_client = MagicMock()
    redis_client.add_message_id = AsyncMock()

    config = MagicMock()
    config.mini_app_url = "https://t.me/RutTrackBot/checkin"

    await handle_lesson_started(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
        redis_client=redis_client,
        config=config,
    )

    await captured_tasks[0].coroutine_factory()

    # text is a keyword argument
    call_kwargs = bot.send_message.call_args.kwargs
    assert "Линейная алгебра" in call_kwargs.get("text", "")


@pytest.mark.asyncio
async def test_lesson_started_fallback_subject_name_on_grpc_error():
    """Falls back to 'Пара' when get_subjects_by_ids raises an exception."""
    students = [_make_student(user_id=1, telegram_id=111)]

    bot = MagicMock()
    bot.send_message = AsyncMock(return_value=MagicMock(message_id=1))

    academic_client = MagicMock()
    academic_client.get_group_members = AsyncMock(return_value=students)
    academic_client.get_subjects_by_ids = AsyncMock(side_effect=Exception("gRPC down"))

    send_queue = MagicMock()
    captured_tasks = []

    async def capture_put(task):
        captured_tasks.append(task)

    send_queue.put = capture_put
    redis_client = MagicMock()
    redis_client.add_message_id = AsyncMock()

    config = MagicMock()
    config.mini_app_url = "https://t.me/RutTrackBot/checkin"

    await handle_lesson_started(
        _make_event(),
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
        redis_client=redis_client,
        config=config,
    )

    await captured_tasks[0].coroutine_factory()

    call_kwargs = bot.send_message.call_args.kwargs
    assert "Пара" in call_kwargs.get("text", "")


@pytest.mark.asyncio
async def test_lesson_started_skips_missing_payload_fields():
    """Handler logs a warning and returns early when required payload fields are missing."""
    bot = MagicMock()
    academic_client = MagicMock()
    send_queue = MagicMock()
    send_queue.put = AsyncMock()
    redis_client = MagicMock()
    config = MagicMock()

    incomplete_event = {"event_type": "lesson.started", "payload": {"group_id": 5}}

    # Should not raise
    await handle_lesson_started(
        incomplete_event,
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
        redis_client=redis_client,
        config=config,
    )

    send_queue.put.assert_not_called()
