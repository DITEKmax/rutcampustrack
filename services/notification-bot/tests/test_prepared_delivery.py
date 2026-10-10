"""Atomic same-queue prepare projection transaction boundaries with a broker model."""

import asyncio
import json
from contextlib import asynccontextmanager
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from bot.consumers.event_consumer import QUEUE_NAME, start_consumer
from bot.services.notification_prefs import NotificationPreferencesUnavailable

EVENT = {
    "event_id": "b1e5df89-f73c-4c06-9b3c-231fb56fe41c",
    "event_type": "group.renamed",
    "occurred_at": "2026-10-10T13:00:00+03:00",
    "trace_id": "same-trace",
    "payload": {"group_id": 7},
}
PREPARED = {
    **EVENT,
    "_bot_prepared": {
        "version": 1, "event_id": EVENT["event_id"], "event_type": EVENT["event_type"],
        "recipients": [{"user_id": 1, "roles": ["STUDENT", "TEACHER"]}],
    },
}


def broker(monkeypatch, *, event=None, fail_commit=False, prepared_transport=False, publish_error=None, ack_error=None):
    import bot.consumers.event_consumer as module
    operations = []
    finished = asyncio.Event()
    state = SimpleNamespace(published=[], pending=[], acked=False, rejected=False, requeued=False)
    message = SimpleNamespace(
        body=json.dumps(event or EVENT).encode(),
        exchange="" if prepared_transport else "rut-uit.events",
        routing_key=QUEUE_NAME if prepared_transport else "group.renamed",
        processed=False,
    )

    @asynccontextmanager
    async def process(**kwargs):
        try:
            yield
        except BaseException:
            if not message.processed:
                operations.append("reject")
                message.processed = True
                state.pending.append(("reject", None))
            raise
        else:
            if not message.processed:
                operations.append("ack")
                message.processed = True
                state.pending.append(("ack", None))
                if ack_error is not None:
                    raise ack_error

    async def nack(requeue):
        assert requeue
        operations.append("nack")
        message.processed = True
        state.pending.append(("nack", None))

    async def publish(message, *, routing_key, mandatory):
        assert routing_key == QUEUE_NAME and mandatory
        assert message.delivery_mode.value == 2
        assert message.message_id == EVENT["event_id"]
        operations.append("publish")
        state.pending.append(("publish", json.loads(message.body)))
        if publish_error is not None:
            raise publish_error

    async def select(**kwargs):
        assert kwargs == {"timeout": 8}
        operations.append("select")

    async def commit(**kwargs):
        assert kwargs == {"timeout": 8}
        operations.append("commit")
        if fail_commit:
            raise RuntimeError("synthetic commit unavailable")
        for operation, value in state.pending:
            if operation == "publish":
                state.published.append(value)
            elif operation == "ack":
                state.acked = True
            elif operation == "reject":
                state.rejected = True
            elif operation == "nack":
                state.requeued = True
        state.pending.clear()
        finished.set()

    async def rollback():
        operations.append("rollback")
        state.pending.clear()

    async def close():
        operations.append("close")
        if not state.acked and not state.rejected:
            state.requeued = True
        finished.set()

    class Iterator:
        def __init__(self):
            self.remaining = True

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return False

        def __aiter__(self):
            return self

        async def __anext__(self):
            if self.remaining:
                self.remaining = False
                return message
            await finished.wait()
            raise StopAsyncIteration

    message.process, message.nack = process, nack
    queue = SimpleNamespace(bind=AsyncMock(), iterator=Iterator)
    transaction = SimpleNamespace(select=select, commit=commit, rollback=rollback)
    channel = SimpleNamespace(
        set_qos=AsyncMock(), declare_exchange=AsyncMock(),
        declare_queue=AsyncMock(return_value=queue), transaction=lambda: transaction,
        default_exchange=SimpleNamespace(publish=publish),
    )
    connection = SimpleNamespace(channel=AsyncMock(return_value=channel), close=close)
    monkeypatch.setattr(module.aio_pika, "connect_robust", AsyncMock(return_value=connection))
    guard = SimpleNamespace(
        _ttl=300, claim=AsyncMock(return_value="owned"), is_completed=AsyncMock(return_value=False),
        release=AsyncMock(return_value=True), complete=AsyncMock(return_value=True),
    )
    return state, operations, connection, channel, guard


async def test_atomic_prepare_publish_ack_commit_preserves_facts_and_minimal_roles(monkeypatch):
    state, operations, connection, channel, guard = broker(monkeypatch)
    dispatcher = SimpleNamespace(prepare=AsyncMock(return_value=PREPARED), dispatch=AsyncMock())
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    assert operations[:4] == ["select", "publish", "ack", "commit"]
    assert state.acked and state.published == [PREPARED]
    assert state.published[0]["trace_id"] == EVENT["trace_id"]
    connection.channel.assert_awaited_once_with(publisher_confirms=False)
    channel.set_qos.assert_awaited_once_with(prefetch_count=1)
    dispatcher.dispatch.assert_not_awaited()
    guard.complete.assert_not_awaited()
    guard.release.assert_awaited_once()


async def test_commit_unavailable_retains_original_without_partial_replacement(monkeypatch):
    state, operations, _, _, guard = broker(monkeypatch, fail_commit=True)
    dispatcher = SimpleNamespace(prepare=AsyncMock(return_value=PREPARED), dispatch=AsyncMock())
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    assert operations[:6] == ["select", "publish", "ack", "commit", "rollback", "close"]
    assert not state.acked and not state.published and state.requeued
    dispatcher.dispatch.assert_not_awaited()
    guard.complete.assert_not_awaited()
    guard.release.assert_not_awaited()  # Lease survives uncertain commit until expiry.


async def test_unknown_prepare_authority_commits_requeue_without_provider_or_publish(monkeypatch):
    state, operations, _, _, guard = broker(monkeypatch)
    dispatcher = SimpleNamespace(
        prepare=AsyncMock(side_effect=NotificationPreferencesUnavailable("synthetic unknown")),
        dispatch=AsyncMock(),
    )
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    assert operations[:3] == ["select", "nack", "commit"]
    assert state.requeued and not state.acked and not state.published
    dispatcher.dispatch.assert_not_awaited()
    guard.complete.assert_not_awaited()


@pytest.mark.parametrize("private_transport", [False, True])
async def test_prepared_projection_requires_private_queue_provenance(monkeypatch, private_transport):
    state, operations, _, _, guard = broker(monkeypatch, event=PREPARED, prepared_transport=private_transport)
    dispatcher = SimpleNamespace(prepare=AsyncMock(), dispatch=AsyncMock(return_value=None))
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    dispatcher.prepare.assert_not_awaited()
    if private_transport:
        dispatcher.dispatch.assert_awaited_once_with(PREPARED)
        assert state.acked
        guard.complete.assert_awaited_once()
    else:
        dispatcher.dispatch.assert_not_awaited()
        assert state.rejected and not state.acked
        assert operations[:3] == ["select", "reject", "commit"]
        guard.complete.assert_not_awaited()


@pytest.mark.parametrize("abort", [TimeoutError(), asyncio.CancelledError(), RuntimeError("synthetic drain abort")])
async def test_abort_after_publish_staging_rolls_back_raw_and_prepared_without_commit(monkeypatch, abort):
    state, operations, _, _, guard = broker(monkeypatch, publish_error=abort)
    dispatcher = SimpleNamespace(prepare=AsyncMock(return_value=PREPARED), dispatch=AsyncMock())
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    assert operations[:5] == ["select", "publish", "reject", "rollback", "close"]
    assert "commit" not in operations and "nack" not in operations
    assert not state.acked and not state.published and not state.rejected and state.requeued
    guard.complete.assert_not_awaited()
    guard.release.assert_not_awaited()
    dispatcher.dispatch.assert_not_awaited()


async def test_abort_during_automatic_ack_after_staging_never_commits_projection(monkeypatch):
    state, operations, _, _, guard = broker(monkeypatch, ack_error=TimeoutError())
    dispatcher = SimpleNamespace(prepare=AsyncMock(return_value=PREPARED), dispatch=AsyncMock())
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    assert operations[:5] == ["select", "publish", "ack", "rollback", "close"]
    assert "commit" not in operations and "nack" not in operations
    assert not state.acked and not state.published and state.requeued
    guard.release.assert_not_awaited()
