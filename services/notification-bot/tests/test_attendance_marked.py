"""Attendance confirmation and reminder retirement through business dispatch."""

import asyncio
import json
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

import pytest
from aiogram.exceptions import TelegramBadRequest, TelegramRetryAfter
from redis.exceptions import ConnectionError as RedisConnectionError

from bot.services.redis_client import ReminderLifecycleUnavailable
from tests.business_delivery import BusinessDelivery, assert_business_delivery, fact_event


@pytest.mark.asyncio
async def test_attendance_marked_headman_present_identifies_mark_and_author(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "attendance.marked",
        status="present",
        extra={"old_status": "absent"},
        expected=("отсутствует → присутствует", "Автор: староста"),
        absent=("(б)",),
    )


@pytest.mark.asyncio
async def test_attendance_marked_present_deletes_messages_and_clears_redis(fake_redis):
    await assert_business_delivery(fake_redis, "attendance.marked", status="present", expected=("присутствует",))


@pytest.mark.asyncio
async def test_attendance_marked_absent_deletes_messages(fake_redis):
    await assert_business_delivery(fake_redis, "attendance.marked", status="absent", expected=("отсутствует",))


@pytest.mark.asyncio
async def test_attendance_marked_excused_deletes_messages(fake_redis):
    await assert_business_delivery(
        fake_redis, "attendance.marked", status="excused", expected=("уважительная причина",)
    )


@pytest.mark.asyncio
async def test_attendance_marked_free_attendance_deletes_messages(fake_redis):
    await assert_business_delivery(
        fake_redis, "attendance.marked", status="free_attendance", expected=("свободное посещение",)
    )


@pytest.mark.asyncio
async def test_attendance_marked_present_silently_ignores_telegram_bad_request(fake_redis):
    await assert_business_delivery(
        fake_redis, "attendance.marked", bad_delete=True, status="present", expected=("присутствует",)
    )


@pytest.mark.asyncio
async def test_attendance_marked_present_no_messages_is_noop(fake_redis):
    await assert_business_delivery(
        fake_redis, "attendance.marked", no_messages=True, status="present", expected=("присутствует",)
    )


@pytest.mark.asyncio
async def test_attendance_marked_missing_user_id_returns_early(fake_redis):
    await assert_business_delivery(fake_redis, "attendance.marked", missing_fields=("user_id",), reject=True)


@pytest.mark.asyncio
async def test_attendance_marked_student_not_in_group_members_is_noop(fake_redis):
    await assert_business_delivery(
        fake_redis, "attendance.marked", empty=True, status="present", expected=("присутствует",)
    )


@pytest.mark.asyncio
@pytest.mark.parametrize("kind", ["lesson.started", "lesson.reminder"])
async def test_mark_retires_reminder_sent_during_mark_and_suppresses_later_replay(fake_redis, kind):
    h = BusinessDelivery(fake_redis)
    h.authority_by_event["attendance.marked"] = {1: ["STUDENT"]}
    pending_send = asyncio.Event()
    release_send = asyncio.Event()
    late_message = None
    original_send = h.bot.send_message

    async def send(**kwargs):
        nonlocal late_message
        is_target_reminder = kwargs["chat_id"] == 101 and "Автор: староста" not in kwargs["text"]
        if is_target_reminder:
            pending_send.set()
            await release_send.wait()
        result = await original_send(**kwargs)
        if is_target_reminder:
            late_message = result.message_id
        return result

    h.bot.send_message = send
    h.queue.start()
    reminder = asyncio.create_task(h.dispatch(fact_event(kind)))
    try:
        await asyncio.wait_for(pending_send.wait(), 1)
        mark = fact_event("attendance.marked")
        await h.dispatch(mark)
        assert h.deleted == []
        release_send.set()
        await reminder
        assert late_message is not None
        assert h.deleted == [(101, late_message)]
        assert await h.reminders.get_message_ids(11, 1) == []
        assert len(await h.reminders.get_message_ids(11, 2)) == 1
        await h.dispatch(mark)
        await h.dispatch(fact_event(kind))
        to_marked = [item["text"] for item in h.sent if item["chat_id"] == 101]
        assert len(to_marked) == 2
        assert sum("Автор: староста" in text for text in to_marked) == 1
    finally:
        release_send.set()
        await asyncio.gather(reminder, return_exceptions=True)
        await h.queue.shutdown()


@pytest.mark.asyncio
@pytest.mark.parametrize("kind", ["lesson.started", "lesson.reminder"])
async def test_lesson_close_does_not_allow_deferred_reminder_after_mark(fake_redis, kind):
    h = BusinessDelivery(fake_redis)
    h.authority_by_event["attendance.marked"] = {1: ["STUDENT"]}
    deferred = await h.dispatcher.prepare(fact_event(kind))
    h.queue.start()
    try:
        await h.dispatch(fact_event("attendance.marked"))
        await h.dispatch(fact_event("lesson.closed"))
        h.sent.clear()
        await (await h.dispatcher.dispatch(deferred)).wait()
        assert [item["chat_id"] for item in h.sent] == [202]
        assert await h.reminders.get_message_ids(11, 1) == []
    finally:
        await h.queue.shutdown()


@pytest.mark.asyncio
async def test_transient_retirement_failure_replays_cleanup_without_repeating_confirmation(fake_redis):
    h = BusinessDelivery(fake_redis)
    h.authority_by_event["attendance.marked"] = {1: ["STUDENT"]}
    await h.reminders.add_message_id(11, 1, 111, chat_id=100)
    h.bot.delete_message = AsyncMock(
        side_effect=[TelegramRetryAfter(method=SimpleNamespace(), message="rate limit", retry_after=1), True]
    )
    event = fact_event("attendance.marked")
    h.queue.start()
    try:
        with pytest.raises(ReminderLifecycleUnavailable):
            await h.dispatch(event)
        assert await h.reminders.get_message_ids(11, 1) == [111]
        assert len(h.sent) == 1
        assert h.sent[0]["chat_id"] == 101
        await h.dispatch(event)
        assert len(h.sent) == 1
        assert await h.reminders.get_message_ids(11, 1) == []
        assert all(call.kwargs == {"chat_id": 100, "message_id": 111} for call in h.bot.delete_message.await_args_list)
    finally:
        await h.queue.shutdown()


@pytest.mark.asyncio
async def test_mark_strips_cta_if_delete_is_unavailable_even_with_confirmation_disabled(fake_redis):
    h = BusinessDelivery(fake_redis)
    h.authority_by_event["attendance.marked"] = {1: ["STUDENT"]}
    h.prefs_on = False
    await h.reminders.add_message_id(11, 1, 111, chat_id=101)
    h.bot.delete_message = AsyncMock(
        side_effect=TelegramBadRequest(method=SimpleNamespace(), message="message can't be deleted")
    )
    h.queue.start()
    try:
        await h.dispatch(fact_event("attendance.marked"))
        assert h.sent == []
        assert h.edited == [(101, 111, None)]
        assert await h.reminders.get_message_ids(11, 1) == []
    finally:
        await h.queue.shutdown()


@pytest.mark.asyncio
async def test_numeric_legacy_reminder_does_not_target_rebound_chat(fake_redis):
    h = BusinessDelivery(fake_redis)
    h.authority_by_event["attendance.marked"] = {1: ["STUDENT"]}
    await h.reminders.add_message_id(11, 1, 111)
    h.chats[1] = 303
    h.queue.start()
    try:
        await h.dispatch(fact_event("attendance.marked"))
        assert [item["chat_id"] for item in h.sent] == [303]
        assert h.deleted == h.edited == []
        assert await h.reminders.get_message_ids(11, 1) == [111]
    finally:
        await h.queue.shutdown()


@pytest.mark.asyncio
async def test_reminder_retry_retains_provider_id_when_tracking_and_retirement_fail(fake_redis):
    h = BusinessDelivery(fake_redis)
    h.roles = {1: ["STUDENT"], 3: ["STUDENT"]}
    original_send = h.bot.send_message

    async def send(**kwargs):
        result = await original_send(**kwargs)
        await h.reminders.mark_student_marked(11, 1, "present")
        return result

    h.bot.send_message = send
    h.bot.delete_message = AsyncMock(
        side_effect=[TelegramRetryAfter(method=SimpleNamespace(), message="rate limit", retry_after=1), True]
    )
    event = await h.dispatcher.prepare(fact_event("lesson.started"))
    h.queue.start()
    try:
        with (
            patch.object(fake_redis, "pipeline", side_effect=RedisConnectionError("tracking unavailable")),
            patch.object(fake_redis, "mget", side_effect=RedisConnectionError("receipts unavailable")),
        ):
            with pytest.raises(ReminderLifecycleUnavailable):
                await (await h.dispatcher.dispatch(event)).wait()
            event = json.loads(json.dumps(await h.dispatcher.snapshot_progress(event)))
        assert event["_bot_prepared"]["reminder_recovery"] == [{"user_id": 1, "chat_id": 101, "message_id": 1001}]
        with pytest.raises(ReminderLifecycleUnavailable):
            await h.dispatcher.dispatch(event)
        event = json.loads(json.dumps(await h.dispatcher.snapshot_progress(event)))
        await (await h.dispatcher.dispatch(event)).wait()
        assert len(h.sent) == 1
        assert h.bot.delete_message.await_count == 2
        assert await h.reminders.get_message_ids(11, 1) == []
        assert "reminder_recovery" not in event["_bot_prepared"]
    finally:
        await h.queue.shutdown()
