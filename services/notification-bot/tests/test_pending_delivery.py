"""Broker-owned batch boundaries and fresh audience; no live provider/broker."""
import asyncio
import json
import grpc
from contextlib import asynccontextmanager
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from bot.consumers.event_consumer import start_consumer
from bot.notifications.headman_alerts import request_audience
from bot.grpc_client import attendance_pb2 as pb
from bot.services.notification_prefs import NotificationPreferencesUnavailable
from bot.services.send_queue import SendTask, TelegramSendQueue, TelegramDeliveryFailed


def recipient(factory, user=11, audience=None, hook=None):
    return SendTask(factory, user_id=user, chat_id=100 + user, category="tickets",
                    audience_check=audience, on_sent=hook)


def broker(monkeypatch, count=1):
    """An open iterator, explicit ACK/NACK, no automatic ACK of an active batch."""
    import bot.consumers.event_consumer as module
    finished = asyncio.Event()
    messages = []

    class Message:
        def __init__(self, index):
            self.body = json.dumps({"event_id": str(index), "event_type": "excuse.requested"}).encode()
            self.acked = self.nacked = self.dlq = False
        @property
        def processed(self): return self.acked or self.nacked or self.dlq
        @asynccontextmanager
        async def process(self, **kwargs):
            try:
                yield
            except BaseException:
                if not self.processed: self.dlq = True
                raise
            else:
                if not self.processed: self.acked = True
            finally:
                if all(item.processed for item in messages): finished.set()
        async def nack(self, requeue):
            assert requeue
            self.nacked = True

    messages.extend(Message(i) for i in range(count))
    class Iterator:
        def __init__(self): self.remaining = list(messages)
        async def __aenter__(self): return self
        async def __aexit__(self, *args): return False
        def __aiter__(self): return self
        async def __anext__(self):
            if self.remaining: return self.remaining.pop(0)
            await finished.wait()
            raise StopAsyncIteration
    queue = SimpleNamespace(bind=AsyncMock(), iterator=Iterator)
    channel = SimpleNamespace(set_qos=AsyncMock(), declare_exchange=AsyncMock(),
                              declare_queue=AsyncMock(return_value=queue))
    connection = SimpleNamespace(channel=AsyncMock(return_value=channel), close=AsyncMock())
    monkeypatch.setattr(module.aio_pika, "connect_robust", AsyncMock(return_value=connection))
    return messages, connection


def lease(budget=300, renew=True):
    return SimpleNamespace(_ttl=budget, claim=AsyncMock(return_value="owner"),
        renew=AsyncMock(return_value=renew), is_completed=AsyncMock(return_value=False),
        complete=AsyncMock(return_value=True), release=AsyncMock(return_value=True))


async def test_unacked_batch_until_provider_success_and_exhaustion_goes_dlq(monkeypatch):
    messages, connection = broker(monkeypatch)
    guard = lease()
    queue = TelegramSendQueue(SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    queue.start()
    entered, proceed = asyncio.Event(), asyncio.Event()
    async def send():
        entered.set()
        await proceed.wait()
    async def dispatch(event):
        async with queue.staged() as batch:
            await queue.put(recipient(send))
        return batch
    consumer = asyncio.create_task(start_consumer("amqp://synthetic", SimpleNamespace(dispatch=dispatch), guard))
    try:
        await asyncio.wait_for(entered.wait(), 2)
        assert not messages[0].processed
        guard.complete.assert_not_awaited()
        proceed.set()
        await asyncio.wait_for(consumer, 2)
        assert messages[0].acked
        guard.complete.assert_awaited_once()
        connection.close.assert_awaited_once()
    finally:
        consumer.cancel()
        await asyncio.gather(consumer, return_exceptions=True)
        await queue.shutdown()

    # Exhaustion is a failed original event, never a completed marker/ACK.
    messages, _ = broker(monkeypatch)
    guard = lease()
    queue = TelegramSendQueue(SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    queue._RETRY_DELAYS = [0, 0, 0]
    queue.start()
    failure = AsyncMock(side_effect=RuntimeError("synthetic provider failure"))
    async def failed_dispatch(event):
        async with queue.staged() as batch:
            await queue.put(recipient(failure))
        return batch
    try:
        await asyncio.wait_for(start_consumer("amqp://synthetic", SimpleNamespace(dispatch=failed_dispatch), guard), 2)
        assert failure.await_count == 4
        assert messages[0].dlq and not messages[0].acked
        guard.complete.assert_not_awaited()
    finally:
        await queue.shutdown()


@pytest.mark.parametrize("renew", [True, False])
async def test_deadline_or_lease_loss_defers_and_stops_pending_attempts(monkeypatch, renew):
    messages, _ = broker(monkeypatch)
    guard = lease(budget=1, renew=renew)
    queue = TelegramSendQueue(SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    held = asyncio.Event()
    queue._consume_token = held.wait  # A rate/429 wait must not bypass ownership cancellation.
    queue.start()
    factory = AsyncMock()
    async def dispatch(event):
        async with queue.staged() as batch:
            await queue.put(recipient(factory))
        return batch
    try:
        await asyncio.wait_for(start_consumer("amqp://synthetic", SimpleNamespace(dispatch=dispatch), guard), 4)
        held.set()
        await asyncio.wait_for(queue._queue.join(), 1)
        assert messages[0].nacked and not messages[0].acked
        guard.complete.assert_not_awaited()
        factory.assert_not_awaited()
        assert all(not worker.done() for worker in queue._workers)
    finally:
        await queue.shutdown()


async def test_revoked_headman_after_rate_and_429_wait_is_suppressed():
    member = SimpleNamespace(user_id=11, telegram_id=111, is_headman=True)
    academic = SimpleNamespace(get_current_group_members=AsyncMock(return_value=[member]))
    context = pb.ResolveRequestNotificationResponse(group_id=7, student_id=12,
        detail=pb.StudentRequestDetail(summary=pb.StudentRequestSummary(id="req-1",
            kind=pb.STUDENT_REQUEST_KIND_EXCUSE, status=pb.STUDENT_REQUEST_STATUS_PENDING)))
    attendance = SimpleNamespace(resolve_request_notification=AsyncMock(return_value=context))
    audience = request_audience(academic, attendance, pb.STUDENT_REQUEST_KIND_EXCUSE, "req-1", 7, 11, 111)
    queue = TelegramSendQueue(SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    at_rate, release = asyncio.Event(), asyncio.Event()
    async def rate_wait():
        at_rate.set()
        await release.wait()
    queue._consume_token = rate_wait
    queue.start()
    factory = AsyncMock()
    try:
        async with queue.staged() as batch:
            await queue.put(recipient(factory, audience=audience))
        await asyncio.wait_for(at_rate.wait(), 1)
        member.is_headman = False
        release.set()
        await batch.wait()
        factory.assert_not_awaited()
        assert academic.get_current_group_members.await_count == 2
        # A retry-after also precedes the next fresh role check.
        member.is_headman = True
        class RetryAfter(Exception): retry_after = 0
        async def first_attempt():
            member.is_headman = False
            raise RetryAfter()
        factory = AsyncMock(side_effect=first_attempt)
        async with queue.staged() as retry:
            await queue.put(recipient(factory, audience=audience))
        await retry.wait()
        factory.assert_awaited_once()
    finally:
        await queue.shutdown()


async def test_unknown_audience_defers_batch_without_blocking_other_worker():
    allowed = True
    async def audience():
        if not allowed: raise NotificationPreferencesUnavailable("synthetic authority outage")
        return True
    queue = TelegramSendQueue(SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    one, two = AsyncMock(), AsyncMock()
    async with queue.staged() as first:
        await queue.put(recipient(one, audience=audience))
    async with queue.staged() as second:
        await queue.put(recipient(two, user=12))
    allowed = False
    queue.start()
    try:
        with pytest.raises(NotificationPreferencesUnavailable): await first.wait()
        await asyncio.wait_for(second.wait(), 1)
        one.assert_not_awaited()
        two.assert_awaited_once()
        assert len(queue._workers) == 2 and all(not worker.done() for worker in queue._workers)
    finally:
        await queue.shutdown()


async def test_two_processors_bound_admission_and_shutdown_cancels_all_batches(monkeypatch):
    messages, connection = broker(monkeypatch, count=3)
    guard = lease()
    entered = asyncio.Event()
    queue = TelegramSendQueue(SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    async def wait_rate():
        entered.set()
        await asyncio.Event().wait()
    queue._consume_token = wait_rate
    queue.start()
    calls = []
    factory = AsyncMock()
    async def dispatch(event):
        calls.append(event["event_id"])
        async with queue.staged() as batch:
            await queue.put(recipient(factory))
        return batch
    consumer = asyncio.create_task(start_consumer("amqp://synthetic", SimpleNamespace(dispatch=dispatch), guard))
    try:
        await asyncio.wait_for(entered.wait(), 1)
        assert len(calls) == 2
        consumer.cancel()
        await asyncio.gather(consumer, return_exceptions=True)
        connection.close.assert_awaited_once()
        assert not any(message.acked for message in messages)
        guard.complete.assert_not_awaited()
        await queue.shutdown()
        await asyncio.wait_for(queue._queue.join(), 1)
        factory.assert_not_awaited()
        assert all(worker.done() for worker in queue._workers)
    finally:
        consumer.cancel()
        await asyncio.gather(consumer, return_exceptions=True)
        await queue.shutdown()


async def test_successful_provider_hook_failure_preserves_sent_outcome():
    queue = TelegramSendQueue(SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    queue.start()
    factory = AsyncMock()
    try:
        async with queue.staged() as batch:
            await queue.put(recipient(factory, hook=AsyncMock(side_effect=RuntimeError("tracking failed"))))
        await batch.wait()
        factory.assert_awaited_once()
        assert queue._total_sent == 1 and queue._total_failed == 0
    finally:
        await queue.shutdown()


async def test_initial_authority_rpc_outage_defers_original(monkeypatch):
    messages, _ = broker(monkeypatch)
    guard = lease()
    error = grpc.aio.AioRpcError(grpc.StatusCode.UNAVAILABLE, (), (), "synthetic authority outage")
    await asyncio.wait_for(start_consumer("amqp://synthetic",
        SimpleNamespace(dispatch=AsyncMock(side_effect=error)), guard), 2)
    assert messages[0].nacked and not messages[0].dlq
    guard.complete.assert_not_awaited()


async def test_archive_and_terminal_request_keep_lawful_current_audience():
    from bot.notifications.group_archived import handle_group_archived
    member = SimpleNamespace(user_id=11, telegram_id=111, is_headman=True)
    academic = SimpleNamespace(get_group=AsyncMock(return_value=SimpleNamespace(name="Группа (выпуск 2026)", is_active=False)),
        get_group_members=AsyncMock(return_value=[member]), get_current_group_members=AsyncMock(return_value=[member]))
    bot = SimpleNamespace(send_message=AsyncMock())
    queue = TelegramSendQueue(SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    queue.start()
    try:
        async with queue.staged() as batch:
            await handle_group_archived({"payload": {"group_id": 7}}, bot, academic, queue)
        await batch.wait()
        bot.send_message.assert_awaited_once()
        context = pb.ResolveRequestNotificationResponse(group_id=7,
            detail=pb.StudentRequestDetail(summary=pb.StudentRequestSummary(id="terminal-1",
                kind=pb.STUDENT_REQUEST_KIND_EXCUSE, status=pb.STUDENT_REQUEST_STATUS_APPROVED)))
        attendance = SimpleNamespace(resolve_request_notification=AsyncMock(return_value=context))
        assert await request_audience(academic, attendance, pb.STUDENT_REQUEST_KIND_EXCUSE,
            "terminal-1", 7, 11, 111, pending=False)()
        assert not await request_audience(academic, attendance, pb.STUDENT_REQUEST_KIND_EXCUSE,
            "terminal-1", 7, 11, 111)()
    finally:
        await queue.shutdown()


async def test_tracked_reply_rechecks_authorization_after_edit():
    from bot.consumers.event_dispatcher import EventDispatcher
    member = SimpleNamespace(user_id=11, telegram_id=111, is_headman=True)
    academic = SimpleNamespace(get_current_group_members=AsyncMock(return_value=[member]))
    context = pb.ResolveRequestNotificationResponse(group_id=7,
        detail=pb.StudentRequestDetail(summary=pb.StudentRequestSummary(id="terminal-1",
            kind=pb.STUDENT_REQUEST_KIND_EXCUSE, status=pb.STUDENT_REQUEST_STATUS_APPROVED)))
    attendance = SimpleNamespace(resolve_request_notification=AsyncMock(return_value=context))
    tracker = SimpleNamespace(get_all=AsyncMock(return_value=[{"user_id": 11, "chat_id": 111, "message_id": 2}]),
                              delete_entry=AsyncMock())
    async def edited(**kwargs): member.is_headman = False
    bot = SimpleNamespace(edit_message_reply_markup=AsyncMock(side_effect=edited), send_message=AsyncMock())
    queue = TelegramSendQueue(SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    queue.start()
    dispatcher = EventDispatcher(bot, academic, queue, None, SimpleNamespace(admin_telegram_ids=""),
                                 None, request_tracker=tracker, attendance_client=attendance)
    try:
        async with queue.staged() as batch:
            await dispatcher._close_tracked_messages("excuse", "terminal-1", "Одобрено")
        await batch.wait()
        bot.edit_message_reply_markup.assert_awaited_once()
        bot.send_message.assert_not_awaited()
        tracker.delete_entry.assert_not_awaited()
    finally:
        await queue.shutdown()
