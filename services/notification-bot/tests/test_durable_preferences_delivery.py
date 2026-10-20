"""Authority outage/retry and strict response/identity boundaries; no live provider/broker."""

import asyncio
from copy import deepcopy
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from bot.services.notification_prefs import (
    CATEGORIES,
    NotificationPreferencesUnavailable,
    NotificationPrefsClient,
    _snapshot,
)
from bot.services.send_queue import SendTask, TelegramSendQueue
from tests.business_delivery import BusinessDelivery, fact_event


def task(factory, uid=11, chat=101):
    return SendTask(factory, user_id=uid, chat_id=chat, category="homework", notification_type="HOMEWORK_UPDATED")


async def test_staged_batch_outage_flushes_nothing_and_retry_admits_once():
    prefs = SimpleNamespace(is_enabled=AsyncMock(side_effect=[True, NotificationPreferencesUnavailable(), True, True]))
    queue = TelegramSendQueue(prefs)
    one, two = AsyncMock(), AsyncMock()
    with pytest.raises(NotificationPreferencesUnavailable):
        async with queue.staged():
            await queue.put(task(one))
            await queue.put(task(two, 12, 102))
    assert queue._queue.qsize() == 0
    one.assert_not_awaited()
    two.assert_not_awaited()
    async with queue.staged():
        await queue.put(task(one))
        await queue.put(task(two, 12, 102))
    assert queue._queue.qsize() == 2


async def test_worker_outage_defers_batch_and_retry_sends_once():
    prefs = SimpleNamespace(is_enabled=AsyncMock(return_value=True))
    queue = TelegramSendQueue(prefs)
    factory = AsyncMock()
    queue.start()
    try:
        async with queue.staged() as batch:
            await queue.put(task(factory))
        prefs.is_enabled.side_effect = NotificationPreferencesUnavailable()
        with pytest.raises(NotificationPreferencesUnavailable):
            await batch.wait()
        factory.assert_not_awaited()
        prefs.is_enabled.side_effect = None
        async with queue.staged() as retry:
            await queue.put(task(factory))
        await retry.wait()
        factory.assert_awaited_once()
    finally:
        await queue.shutdown()


async def test_unknown_task_is_not_inferred_system_exemption_and_explicit_alert_is():
    queue = TelegramSendQueue()
    generic = SendTask(AsyncMock())
    with pytest.raises(NotificationPreferencesUnavailable):
        async with queue.staged():
            await queue.put(generic)
    assert queue._queue.qsize() == 0
    alert = SendTask(AsyncMock(), system_alert=True)
    queue.start()
    try:
        await queue.put(alert)
        await asyncio.wait_for(queue._queue.join(), 1)
        alert.coroutine_factory.assert_awaited_once()
    finally:
        await queue.shutdown()


async def test_exact_user_mismatch_never_requests_another_accounts_preferences():
    academic = SimpleNamespace(get_user_by_telegram_id=AsyncMock(return_value=SimpleNamespace(found=True, user_id=12)))
    session = SimpleNamespace(request=AsyncMock())
    client = NotificationPrefsClient("http://synthetic", "A" * 43, academic, session)
    assert await client.is_enabled(101, "homework", user_id=11, notification_type="HOMEWORK_UPDATED") is False
    session.request.assert_not_called()


@pytest.mark.parametrize(
    "corruption", ["identity", "missing_category", "invalid_flag", "missing_mute", "no_eligibility"]
)
def test_incomplete_or_wrong_identity_snapshot_defers(corruption):
    raw = {
        "userId": 11,
        "telegramId": 101,
        "globalEnabled": True,
        "categories": dict.fromkeys(CATEGORIES, True),
        "mutedUntil": None,
        "canonicalCategories": dict.fromkeys(CATEGORIES, True),
        "canonicalMutedUntil": None,
        "eligible": True,
    }
    if corruption == "identity":
        raw["userId"] = 12
    elif corruption == "missing_category":
        del raw["categories"]["homework"]
    elif corruption == "invalid_flag":
        raw["globalEnabled"] = "true"
    elif corruption == "missing_mute":
        del raw["mutedUntil"]
    else:
        del raw["eligible"]
    with pytest.raises(NotificationPreferencesUnavailable):
        _snapshot(raw, 11, 101, "homework")


async def test_headman_tracker_records_original_user_for_later_exact_binding(fake_redis):
    h = BusinessDelivery(fake_redis)
    h.roles = {2: ["HEADMAN"]}
    h.queue.start()
    try:
        await h.dispatch(fact_event("excuse.requested"))
        assert await h.tracker.get_all("excuse", "request-1") == [{"user_id": 2, "chat_id": 202, "message_id": 1001}]
    finally:
        await h.queue.shutdown()


async def test_http_retry_reuses_absolute_command_body_and_callback_identity():
    raw = {
        "userId": 11,
        "telegramId": 101,
        "globalEnabled": True,
        "categories": dict.fromkeys(CATEGORIES, True),
        "mutedUntil": None,
        "canonicalCategories": dict.fromkeys(CATEGORIES, True),
        "canonicalMutedUntil": None,
        "eligible": None,
    }
    calls = []

    class Response:
        def __init__(self, status):
            self.status = status

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return False

        async def json(self):
            return deepcopy(raw)

    class Session:
        def request(self, method, url, **kwargs):
            calls.append((method, url, deepcopy(kwargs)))
            return Response(503 if len(calls) == 1 else 200)

    academic = SimpleNamespace(get_user_by_telegram_id=AsyncMock(return_value=SimpleNamespace(found=True, user_id=11)))
    client = NotificationPrefsClient("http://synthetic", "A" * 43, academic, Session())
    await client.command(101, "callback-1", "MUTE_FOR", durationSeconds=86400)
    assert len(calls) == 2
    assert calls[0] == calls[1]
    assert calls[0][2]["json"] == {"requestKey": "callback-1", "operation": "MUTE_FOR", "durationSeconds": 86400}


async def test_consumer_outage_release_failure_and_busy_lease_requeue_until_one_completion(monkeypatch):
    import json
    from contextlib import asynccontextmanager

    import bot.consumers.event_consumer as consumer
    from bot.consumers.event_consumer import start_consumer

    class Message:
        body = json.dumps({"event_id": "event-1", "event_type": "homework.published", "payload": {}}).encode()

        def __init__(self):
            self.acked = False
            self.nacked = False

        @property
        def processed(self):
            return self.acked or self.nacked

        @asynccontextmanager
        async def process(self, **kwargs):
            assert kwargs == {"requeue": False, "ignore_processed": True}
            yield
            if not self.nacked:
                self.acked = True
            if all(item.processed for item in messages):
                finished.set()

        async def nack(self, requeue):
            assert requeue is True
            self.nacked = True

    messages = [Message() for _ in range(4)]
    finished = asyncio.Event()

    class Iterator:
        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return False

        def __aiter__(self):
            return self

        async def __anext__(self):
            if not self.remaining:
                await finished.wait()
                raise StopAsyncIteration
            return self.remaining.pop(0)

        def __init__(self):
            self.remaining = list(messages)

    queue = SimpleNamespace(bind=AsyncMock(), iterator=lambda: Iterator())
    channel = SimpleNamespace(
        set_qos=AsyncMock(), declare_exchange=AsyncMock(), declare_queue=AsyncMock(return_value=queue)
    )
    channel.transaction = lambda: SimpleNamespace(select=AsyncMock(), commit=AsyncMock(), rollback=AsyncMock())
    connection = SimpleNamespace(channel=AsyncMock(return_value=channel), close=AsyncMock())
    monkeypatch.setattr(consumer.aio_pika, "connect_robust", AsyncMock(return_value=connection))
    monkeypatch.setattr(consumer.asyncio, "sleep", AsyncMock())
    guard = SimpleNamespace(
        claim=AsyncMock(side_effect=["lease-1", None, "lease-2", None]),
        is_completed=AsyncMock(side_effect=[False, True]),
        release=AsyncMock(side_effect=RuntimeError("synthetic release failure")),
        complete=AsyncMock(return_value=True),
    )
    prefs = SimpleNamespace(
        is_enabled=AsyncMock(side_effect=[True, NotificationPreferencesUnavailable(), True, True, True, True])
    )
    sends = TelegramSendQueue(prefs)
    sends.start()
    factory = AsyncMock()

    async def dispatch(event):
        async with sends.staged() as batch:
            await sends.put(task(factory))
            await sends.put(task(factory, 12, 102))
        return batch

    await start_consumer("amqp://synthetic", SimpleNamespace(dispatch=dispatch), guard)
    assert [(m.acked, m.nacked) for m in messages] == [(False, True), (False, True), (True, False), (True, False)]
    guard.complete.assert_awaited_once_with("event-1", "lease-2")
    assert sends._queue.qsize() == 0
    assert factory.await_count == 2
    await sends.shutdown()


@pytest.mark.parametrize("eligible", [True, False])
async def test_exact_type_authority_ignores_app_category_and_preserves_explicit_off(eligible):
    raw = {
        "userId": 11,
        "telegramId": 101,
        "globalEnabled": True,
        "categories": dict.fromkeys(CATEGORIES, True),
        "mutedUntil": None,
        "canonicalCategories": dict.fromkeys(CATEGORIES, False),
        "canonicalMutedUntil": None,
        "eligible": eligible,
    }
    calls = []

    class Response:
        status = 200

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return False

        async def json(self):
            return deepcopy(raw)

    class Session:
        def request(self, method, url, **kwargs):
            calls.append(kwargs)
            return Response()

    academic = SimpleNamespace(get_user_by_telegram_id=AsyncMock(return_value=SimpleNamespace(found=True, user_id=11)))
    client = NotificationPrefsClient("http://synthetic", "A" * 43, academic, Session())
    assert await client.is_enabled(101, "homework", user_id=11, notification_type="HOMEWORK_UPDATED") is eligible
    assert calls[0]["params"] == {"type": "HOMEWORK_UPDATED"}
    # Legacy category alone cannot bypass a per-type OFF.
    with pytest.raises(NotificationPreferencesUnavailable):
        await client.is_enabled(101, "homework", user_id=11)
    assert len(calls) == 1
