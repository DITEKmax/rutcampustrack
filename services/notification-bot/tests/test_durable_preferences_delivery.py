"""Authority outage/retry and strict response/identity boundaries; no live provider/broker."""
import asyncio
from copy import deepcopy
from types import SimpleNamespace
from unittest.mock import AsyncMock
import pytest
from bot.services.notification_prefs import CATEGORIES, NotificationPrefsClient, NotificationPreferencesUnavailable, _snapshot
from bot.services.send_queue import SendTask, TelegramSendQueue

def task(factory, uid=11, chat=101):
    return SendTask(factory, user_id=uid, chat_id=chat, category="homework")

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

async def test_worker_retains_task_during_outage_then_sends_once():
    recovered = asyncio.Event()
    queried = asyncio.Event()
    async def eligibility(*args, **kwargs):
        queried.set()
        if not recovered.is_set():
            raise NotificationPreferencesUnavailable()
        return True
    queue = TelegramSendQueue(SimpleNamespace(is_enabled=eligibility))
    factory = AsyncMock()
    queue.start()
    try:
        await queue.put(task(factory))
        await asyncio.wait_for(queried.wait(), 1)
        factory.assert_not_awaited()
        assert queue._queue._unfinished_tasks == 1
        assert not queue._worker_task.done()
        recovered.set()
        await asyncio.wait_for(queue._queue.join(), 3)
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
    assert await client.is_enabled(101, "homework", user_id=11) is False
    session.request.assert_not_called()

@pytest.mark.parametrize("corruption", ["identity", "missing_category", "invalid_flag", "missing_mute", "no_eligibility"])
def test_incomplete_or_wrong_identity_snapshot_defers(corruption):
    raw = {"userId": 11, "telegramId": 101, "globalEnabled": True,
           "categories": dict.fromkeys(CATEGORIES, True), "mutedUntil": None,
           "canonicalCategories": dict.fromkeys(CATEGORIES, True), "canonicalMutedUntil": None, "eligible": True}
    if corruption == "identity": raw["userId"] = 12
    elif corruption == "missing_category": del raw["categories"]["homework"]
    elif corruption == "invalid_flag": raw["globalEnabled"] = "true"
    elif corruption == "missing_mute": del raw["mutedUntil"]
    else: del raw["eligible"]
    with pytest.raises(NotificationPreferencesUnavailable):
        _snapshot(raw, 11, 101, "homework")

async def test_headman_tracker_records_original_user_for_later_exact_binding():
    from bot.grpc_client import attendance_pb2 as pb
    from bot.notifications.headman_alerts import handle_headman_alert
    result = pb.ResolveRequestNotificationResponse(group_id=7, student_id=11, student_name="Студент",
        detail=pb.StudentRequestDetail(reason=pb.STUDENT_EXCUSE_REASON_ILLNESS,
            summary=pb.StudentRequestSummary(id="request-1", kind=pb.STUDENT_REQUEST_KIND_EXCUSE,
                status=pb.STUDENT_REQUEST_STATUS_PENDING, lessons=[pb.StudentRequestLesson(id=5, lesson_number=2)])))
    academic = SimpleNamespace(get_group_members=AsyncMock(return_value=[
        SimpleNamespace(user_id=12, telegram_id=102, is_headman=True)]))
    attendance = SimpleNamespace(resolve_request_notification=AsyncMock(return_value=result))
    bot = SimpleNamespace(send_message=AsyncMock(return_value=SimpleNamespace(message_id=20)))
    tracker = SimpleNamespace(add=AsyncMock())
    queue = TelegramSendQueue(SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    async with queue.staged():
        await handle_headman_alert({"event_type": "excuse.requested", "payload": {"group_id": 7, "ticket_id": "request-1"}},
            bot=bot, academic_client=academic, attendance_client=attendance, send_queue=queue, request_tracker=tracker)
        bot.send_message.assert_not_awaited()
    pending = queue._queue.get_nowait()
    assert (pending.user_id, pending.chat_id, pending.category) == (12, 102, "tickets")
    sent = await pending.coroutine_factory()
    await pending.on_sent(sent)
    tracker.add.assert_awaited_once_with("excuse", "request-1", 102, 20, 12)

async def test_http_retry_reuses_absolute_command_body_and_callback_identity():
    raw = {"userId": 11, "telegramId": 101, "globalEnabled": True,
           "categories": dict.fromkeys(CATEGORIES, True), "mutedUntil": None,
           "canonicalCategories": dict.fromkeys(CATEGORIES, True), "canonicalMutedUntil": None, "eligible": None}
    calls = []
    class Response:
        def __init__(self, status): self.status = status
        async def __aenter__(self): return self
        async def __aexit__(self, *args): return False
        async def json(self): return deepcopy(raw)
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
    from bot.consumers.event_consumer import start_consumer
    import bot.consumers.event_consumer as consumer
    class Message:
        body = json.dumps({"event_id": "event-1", "event_type": "homework.published", "payload": {}}).encode()
        def __init__(self): self.acked = False; self.nacked = False
        @asynccontextmanager
        async def process(self, **kwargs):
            assert kwargs == {"requeue": False, "ignore_processed": True}
            yield
            if not self.nacked: self.acked = True
        async def nack(self, requeue):
            assert requeue is True
            self.nacked = True
    messages = [Message() for _ in range(4)]
    class Iterator:
        async def __aenter__(self): return self
        async def __aexit__(self, *args): return False
        def __aiter__(self): return self
        async def __anext__(self):
            if not self.remaining: raise StopAsyncIteration
            return self.remaining.pop(0)
        def __init__(self): self.remaining = list(messages)
    queue = SimpleNamespace(bind=AsyncMock(), iterator=lambda: Iterator())
    channel = SimpleNamespace(set_qos=AsyncMock(), declare_exchange=AsyncMock(), declare_queue=AsyncMock(return_value=queue))
    connection = SimpleNamespace(channel=AsyncMock(return_value=channel))
    monkeypatch.setattr(consumer.aio_pika, "connect_robust", AsyncMock(return_value=connection))
    monkeypatch.setattr(consumer.asyncio, "sleep", AsyncMock())
    guard = SimpleNamespace(claim=AsyncMock(side_effect=["lease-1", None, "lease-2", None]),
        is_completed=AsyncMock(side_effect=[False, True]), release=AsyncMock(side_effect=RuntimeError("synthetic release failure")),
        complete=AsyncMock(return_value=True))
    prefs = SimpleNamespace(is_enabled=AsyncMock(side_effect=[True, NotificationPreferencesUnavailable(), True, True]))
    sends = TelegramSendQueue(prefs)
    factory = AsyncMock()
    async def dispatch(event):
        async with sends.staged():
            await sends.put(task(factory))
            await sends.put(task(factory, 12, 102))
    await start_consumer("amqp://synthetic", SimpleNamespace(dispatch=dispatch), guard)
    assert [(m.acked, m.nacked) for m in messages] == [(False, True), (False, True), (True, False), (True, False)]
    guard.complete.assert_awaited_once_with("event-1", "lease-2")
    assert sends._queue.qsize() == 2
    factory.assert_not_awaited()
