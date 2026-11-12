"""Tests for the homework notification handler."""

from html import unescape

import pytest

from bot.services.send_queue import TelegramDeliveryFailed
from tests.business_delivery import BusinessDelivery, assert_business_delivery, fact_event


@pytest.mark.asyncio
async def test_homework_published_sends_to_students_with_telegram_id(fake_redis):
    await assert_business_delivery(fake_redis, "homework.published", expected=("ДЗ &lt;snapshot&gt;",))


@pytest.mark.asyncio
async def test_homework_published_text_contains_subject_and_title(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "homework.published",
        extra={
            "homework_id": 71,
            "title": "Лабораторная <3>",
            "description": "Решить <задачи> 1-5",
            "link": "https://private.test/published.pdf",
            "has_link": True,
            "lesson_date": "2026-10-12",
            "lesson_number": 3,
        },
        missing_fields=("subject_name", "date"),
        expected=(
            "Новое домашнее задание",
            "Лабораторная &lt;3&gt;",
            "Решить &lt;задачи&gt; 1-5",
            "12.10.2026",
            "Пара №3",
        ),
        absent=("https://private.test/published.pdf", "Решить <задачи>"),
    )


@pytest.mark.asyncio
async def test_homework_updated_sends_to_all_students_with_telegram_id(fake_redis):
    await assert_business_delivery(fake_redis, "homework.updated", expected=("ДЗ &lt;snapshot&gt;",))


@pytest.mark.asyncio
async def test_homework_updated_text_contains_subject_and_title(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "homework.updated",
        extra={
            "homework_id": 72,
            "title": "Задача <3>",
            "description": "private updated description",
            "link": "https://private.test/updated",
            "has_link": True,
            "lesson_date": "2026-10-13",
            "lesson_number": 2,
        },
        missing_fields=("subject_name", "date"),
        expected=("Домашнее задание изменено", "Задача &lt;3&gt;", "13.10.2026", "Пара №2"),
        absent=("private updated description", "https://private.test/updated"),
    )


@pytest.mark.asyncio
async def test_homework_updated_subject_fallback_on_grpc_error(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "homework.updated",
        missing_fields=("subject_name",),
        extra={"title": "Упражнение snapshot"},
        expected=("Домашнее задание изменено", "Упражнение snapshot", "Пара №2"),
        absent=("Теормех",),
    )


@pytest.mark.asyncio
async def test_homework_published_subject_fallback_on_grpc_error(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "homework.published",
        missing_fields=("subject_name",),
        extra={"description": "Описание snapshot"},
        expected=("Новое домашнее задание", "Описание snapshot", "Пара №2"),
        absent=("Теормех",),
    )


@pytest.mark.asyncio
async def test_homework_due_reminder_sends_direct_to_target_user(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "homework.due_reminder",
        extra={
            "user_id": 1,
            "group_id": 7,
            "semester_id": 9,
            "days_before_due": 1,
            "due_date": "2026-10-14",
            "homework": {
                "homework_id": 73,
                "subject_id": 8,
                "subject_name": "Math <snapshot>",
                "title": "Essay <snapshot>",
                "description": "private due description",
                "link": "https://private.test/due",
                "has_link": True,
                "lesson_date": "2026-10-14",
                "lesson_number": 4,
            },
        },
        expected=("Math &lt;snapshot&gt;", "Essay &lt;snapshot&gt;", "14.10.2026", "Пара №4"),
        absent=("private due description", "https://private.test/due"),
        expected_users=(1,),
    )


@pytest.mark.asyncio
async def test_homework_weekly_digest_formats_grouped_items(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "homework.weekly_digest",
        extra={
            "week_start": "2026-10-12",
            "week_end": "2026-10-18",
            "total_count": 2,
            "items": [
                {
                    "homework_id": 81,
                    "subject_id": 8,
                    "subject_name": "History <snapshot>",
                    "title": "Read <chapter>",
                    "description": "private digest one",
                    "link": "https://private.test/one",
                    "has_link": True,
                    "lesson_date": "2026-10-12",
                    "lesson_number": 1,
                },
                {
                    "homework_id": 82,
                    "subject_id": 9,
                    "subject_name": "Math <snapshot>",
                    "title": "Essay <two>",
                    "description": "private digest two",
                    "link": "https://private.test/two",
                    "has_link": True,
                    "lesson_date": "2026-10-13",
                    "lesson_number": 2,
                },
            ],
        },
        expected=(
            "12.10.2026",
            "18.10.2026",
            "History &lt;snapshot&gt;",
            "Read &lt;chapter&gt;",
            "13.10.2026",
            "Math &lt;snapshot&gt;",
            "Essay &lt;two&gt;",
        ),
        ordered=(
            "History &lt;snapshot&gt;",
            "Read &lt;chapter&gt;",
            "Math &lt;snapshot&gt;",
            "Essay &lt;two&gt;",
        ),
        fact_groups=(
            ("12.10.2026", "History &lt;snapshot&gt;", "Read &lt;chapter&gt;"),
            ("13.10.2026", "Math &lt;snapshot&gt;", "Essay &lt;two&gt;"),
        ),
        absent=("private digest one", "private digest two", "https://private.test/one", "https://private.test/two"),
    )


async def test_digest_resumes_all_unsent_pages_after_parking_and_redis_expiry(fake_redis):
    h = BusinessDelivery(fake_redis)
    h.roles = {1: ["STUDENT"]}
    items = [
        {
            "subject_name": "Информатика",
            "lesson_date": "2026-10-12",
            "lesson_number": i + 1,
            "title": f"Задание {i:03d} " + "😀<&>" * 80,
            "description": "private detail",
        }
        for i in range(40)
    ]
    event = fact_event("homework.weekly_digest", week_start="2026-10-12", week_end="2026-10-18", items=items)
    send = h.bot.send_message
    failed = False

    async def provider_send(**kwargs):
        nonlocal failed
        if "Страница 2 из" in kwargs["text"] and not failed:
            failed = True
            raise RuntimeError("synthetic provider failure before second page")
        return await send(**kwargs)

    h.bot.send_message = provider_send
    h.queue._RETRY_DELAYS = []
    h.queue.start()
    try:
        with pytest.raises(TelegramDeliveryFailed):
            await h.dispatch(event)
        prepared = await h.dispatcher.prepare(event)
        parked = await h.dispatcher.snapshot_progress(prepared)
        await fake_redis.delete(f"notification-bot:digest-pages:{event['event_id']}:1")
        batch = await h.dispatcher.dispatch(parked)
        await batch.wait()
        await h.dispatch(event)
        assert failed and len(h.sent) > 1
        pages = [unescape(item["text"]) for item in h.sent]
        assert all(f"Страница {i} из {len(pages)}" in page for i, page in enumerate(pages, 1))
        assert all(len(page.encode("utf-16-le")) // 2 <= 4096 for page in pages)
        display = "".join(pages)
        assert "private detail" not in display and " · " not in display
        assert all(display.count(f"Задание {i:03d}") == 1 for i in range(len(items)))
    finally:
        await h.queue.shutdown()
