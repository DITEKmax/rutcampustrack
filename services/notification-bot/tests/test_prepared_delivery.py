"""Fast consumer protocol classifications. Durable transaction outcomes require the real-broker probe."""

import asyncio
import io
import json
import logging
from contextlib import asynccontextmanager
from types import SimpleNamespace
from unittest.mock import AsyncMock

import grpc
import pytest
import structlog
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider

from bot.consumers.event_consumer import MAX_RETRIES, PARKING_QUEUE_NAME, QUEUE_NAME, RETRY_HEADER, start_consumer
from bot.observability import EVENT_OUTCOMES, setup_observability
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
        "version": 1,
        "event_id": EVENT["event_id"],
        "event_type": EVENT["event_type"],
        "recipients": [{"user_id": 1, "roles": ["STUDENT", "TEACHER"]}],
    },
}


def broker(
    monkeypatch,
    *,
    event=None,
    fail_commit=False,
    prepared_transport=False,
    publish_error=None,
    ack_error=None,
    headers=None,
):
    import bot.consumers.event_consumer as module

    operations = []
    finished = asyncio.Event()
    state = SimpleNamespace(published=[], pending=[], acked=False, rejected=False, requeued=False)
    message = SimpleNamespace(
        body=json.dumps(event or EVENT).encode(),
        exchange="" if prepared_transport else "rut-uit.events",
        routing_key=QUEUE_NAME if prepared_transport else "group.renamed",
        processed=False,
        headers=headers or {},
    )
    state.replacements = []

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
        assert routing_key in {QUEUE_NAME, PARKING_QUEUE_NAME} and mandatory
        assert message.delivery_mode.value == 2
        assert message.message_id == EVENT["event_id"]
        operations.append("publish")
        state.pending.append(("publish", (routing_key, json.loads(message.body), dict(message.headers or {}))))
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
                state.published.append(value[1])
                state.replacements.append(value)
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
        set_qos=AsyncMock(),
        declare_exchange=AsyncMock(),
        declare_queue=AsyncMock(return_value=queue),
        transaction=lambda: transaction,
        default_exchange=SimpleNamespace(publish=publish),
    )
    connection = SimpleNamespace(channel=AsyncMock(return_value=channel), close=close)
    monkeypatch.setattr(module.aio_pika, "connect_robust", AsyncMock(return_value=connection))
    monkeypatch.setattr(module, "RETRY_DELAYS", (0,) * MAX_RETRIES)
    guard = SimpleNamespace(
        _ttl=300,
        claim=AsyncMock(return_value="owned"),
        is_completed=AsyncMock(return_value=False),
        release=AsyncMock(return_value=True),
        complete=AsyncMock(return_value=True),
    )
    return state, operations, connection, channel, guard


async def test_atomic_prepare_publish_ack_commit_preserves_facts_and_minimal_roles(monkeypatch):
    state, operations, connection, channel, guard = broker(monkeypatch)
    dispatcher = SimpleNamespace(
        snapshot_progress=AsyncMock(side_effect=lambda body: body),
        prepare=AsyncMock(return_value=PREPARED),
        dispatch=AsyncMock(),
    )
    before = EVENT_OUTCOMES.labels("prepared")._value.get()
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    assert EVENT_OUTCOMES.labels("prepared")._value.get() == before + 1
    assert state.acked and state.published == [PREPARED]
    assert state.published[0]["trace_id"] == EVENT["trace_id"]
    connection.channel.assert_awaited_once_with(publisher_confirms=False)
    channel.set_qos.assert_awaited_once_with(prefetch_count=1)
    dispatcher.dispatch.assert_not_awaited()
    guard.complete.assert_not_awaited()
    guard.release.assert_awaited_once()


async def test_commit_unavailable_retains_original_without_partial_replacement(monkeypatch):
    state, operations, _, _, guard = broker(monkeypatch, fail_commit=True)
    dispatcher = SimpleNamespace(
        snapshot_progress=AsyncMock(side_effect=lambda body: body),
        prepare=AsyncMock(return_value=PREPARED),
        dispatch=AsyncMock(),
    )
    prepared_before = EVENT_OUTCOMES.labels("prepared")._value.get()
    uncertain_before = EVENT_OUTCOMES.labels("uncertain")._value.get()
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    assert EVENT_OUTCOMES.labels("prepared")._value.get() == prepared_before
    assert EVENT_OUTCOMES.labels("uncertain")._value.get() == uncertain_before + 1
    assert not state.acked and not state.published and state.requeued
    dispatcher.dispatch.assert_not_awaited()
    guard.complete.assert_not_awaited()
    guard.release.assert_not_awaited()  # Lease survives uncertain commit until expiry.


async def test_unknown_prepare_authority_commits_tail_retry_without_provider(monkeypatch):
    state, operations, _, _, guard = broker(monkeypatch)
    dispatcher = SimpleNamespace(
        snapshot_progress=AsyncMock(side_effect=lambda body: body),
        prepare=AsyncMock(side_effect=NotificationPreferencesUnavailable("synthetic unknown")),
        dispatch=AsyncMock(),
    )
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    assert state.acked and not state.requeued and state.published == [EVENT]
    assert state.replacements[0][0] == QUEUE_NAME
    assert state.replacements[0][2][RETRY_HEADER] == 1
    dispatcher.dispatch.assert_not_awaited()
    guard.complete.assert_not_awaited()


@pytest.mark.parametrize("attempt", [0, MAX_RETRIES - 1, MAX_RETRIES])
async def test_transient_recovery_is_bounded_and_parking_keeps_prepared_body(monkeypatch, attempt):
    state, operations, _, channel, guard = broker(
        monkeypatch,
        event=PREPARED,
        prepared_transport=True,
        headers={RETRY_HEADER: attempt},
    )
    error = grpc.aio.AioRpcError(grpc.StatusCode.UNAVAILABLE, (), (), "private-marker")
    dispatcher = SimpleNamespace(
        snapshot_progress=AsyncMock(side_effect=lambda body: body), dispatch=AsyncMock(side_effect=error)
    )
    before = EVENT_OUTCOMES.labels("parked")._value.get()
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    assert state.acked and state.published == [PREPARED] and not state.requeued
    destination, _, headers = state.replacements[0]
    assert destination == (PARKING_QUEUE_NAME if attempt == MAX_RETRIES else QUEUE_NAME)
    assert headers[RETRY_HEADER] == (attempt if attempt == MAX_RETRIES else attempt + 1)
    assert headers["x-bot-recovery-reason"] == "grpc_unavailable"
    assert EVENT_OUTCOMES.labels("parked")._value.get() == before + int(attempt == MAX_RETRIES)
    guard.complete.assert_not_awaited()
    guard.release.assert_awaited_once()
    assert operations.index("publish") < operations.index("ack") < operations.index("commit")
    channel.declare_queue.assert_any_await(PARKING_QUEUE_NAME, durable=True)


@pytest.mark.parametrize("code", [grpc.StatusCode.NOT_FOUND, grpc.StatusCode.INVALID_ARGUMENT])
async def test_wrapped_terminal_authority_failure_is_parked_without_sending_or_completion(monkeypatch, code, caplog):
    state, _, _, _, guard = broker(monkeypatch, event=PREPARED, prepared_transport=True)
    cause = grpc.aio.AioRpcError(code, (), (), "private-canonical-marker")
    error = NotificationPreferencesUnavailable("Canonical request context unavailable")
    error.__cause__ = cause
    dispatcher = SimpleNamespace(
        snapshot_progress=AsyncMock(side_effect=lambda body: body), dispatch=AsyncMock(side_effect=error)
    )
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    assert state.acked and state.replacements[0][0] == PARKING_QUEUE_NAME
    assert state.published == [PREPARED]
    guard.complete.assert_not_awaited()
    assert "private-canonical-marker" not in caplog.text
    assert "cause=AioRpcError" in caplog.text


@pytest.mark.parametrize("cause_class", [ValueError, TypeError])
async def test_local_canonical_validation_parks_and_reports_code_origin_only(monkeypatch, cause_class, caplog):
    state, _, _, _, guard = broker(monkeypatch, event=PREPARED, prepared_transport=True)

    async def canonical_lookup():
        raise cause_class("private-identity-marker")

    async def dispatch(body):
        try:
            await canonical_lookup()
        except Exception as error:
            raise NotificationPreferencesUnavailable("canonical authority unavailable") from error

    await asyncio.wait_for(
        start_consumer(
            "amqp://synthetic",
            SimpleNamespace(snapshot_progress=AsyncMock(side_effect=lambda body: body), dispatch=dispatch),
            guard,
        ),
        3,
    )
    assert state.acked and state.published == [PREPARED]
    assert state.replacements[0][0] == PARKING_QUEUE_NAME
    assert "origin=canonical_lookup:" in caplog.text
    assert f"cause={cause_class.__name__}" in caplog.text
    assert "private-identity-marker" not in caplog.text
    guard.complete.assert_not_awaited()


@pytest.mark.parametrize("failure", ["publish", "ack", "commit"])
async def test_retry_transaction_failure_retains_original_without_completion(monkeypatch, failure):
    state, operations, _, _, guard = broker(
        monkeypatch,
        fail_commit=failure == "commit",
        publish_error=TimeoutError() if failure == "publish" else None,
        ack_error=TimeoutError() if failure == "ack" else None,
    )
    dispatcher = SimpleNamespace(
        snapshot_progress=AsyncMock(side_effect=lambda body: body),
        dispatch=AsyncMock(side_effect=NotificationPreferencesUnavailable("outage")),
    )
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    assert not state.acked and not state.published and state.requeued
    assert "rollback" in operations
    guard.complete.assert_not_awaited()
    guard.release.assert_not_awaited()


async def test_progress_read_failure_rolls_back_original_without_ack_or_dlq(monkeypatch):
    state, operations, _, _, guard = broker(monkeypatch, event=PREPARED, prepared_transport=True)
    dispatcher = SimpleNamespace(
        dispatch=AsyncMock(side_effect=NotificationPreferencesUnavailable("authority outage")),
        snapshot_progress=AsyncMock(side_effect=NotificationPreferencesUnavailable("receipt outage")),
    )
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    assert state.requeued and not state.acked and not state.published and not state.rejected
    assert "rollback" in operations and "commit" not in operations
    guard.complete.assert_not_awaited()
    guard.release.assert_not_awaited()


async def test_partial_progress_survives_retry_parking_and_redrive_after_receipt_expiry(monkeypatch, fake_redis):
    from bot.notifications.audience import NotificationDeliveryState, handle_business
    from bot.services.send_queue import TelegramSendQueue
    from tests.test_notification_audience import academic, resources

    prepared = {
        **PREPARED,
        "_bot_prepared": {
            **PREPARED["_bot_prepared"],
            "recipients": [{"user_id": uid, "roles": ["STUDENT"]} for uid in (1, 2)],
        },
    }
    receipts = NotificationDeliveryState(fake_redis)
    await receipts.complete(EVENT["event_id"], 1)
    dispatcher = SimpleNamespace(
        dispatch=AsyncMock(side_effect=NotificationPreferencesUnavailable("authority outage")),
        snapshot_progress=receipts.snapshot,
    )
    state, _, _, _, guard = broker(
        monkeypatch,
        event=prepared,
        prepared_transport=True,
        headers={RETRY_HEADER: MAX_RETRIES - 1},
    )
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    retry_route, retry_body, retry_headers = state.replacements[0]
    assert retry_route == QUEUE_NAME and retry_body["_bot_prepared"]["delivered_user_ids"] == [1]
    assert retry_body["payload"] == prepared["payload"]
    await fake_redis.flushdb()  # Isolated receipt store expiry; broker progress remains.
    state, _, _, _, guard = broker(
        monkeypatch,
        event=retry_body,
        prepared_transport=True,
        headers=retry_headers,
    )
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    parked_route, parked_body, parked_headers = state.replacements[0]
    assert parked_route == PARKING_QUEUE_NAME and parked_body["_bot_prepared"]["delivered_user_ids"] == [1]
    # Redrive retains progress and still resolves current authority for remaining users.
    state, _, _, _, guard = broker(
        monkeypatch,
        event=parked_body,
        prepared_transport=True,
        headers=parked_headers,
    )
    authority = academic([{1: ["STUDENT"], 2: ["STUDENT"]}])
    bot, reminders, _ = resources()
    queue = TelegramSendQueue(SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    queue.start()

    async def dispatch(body):
        async with queue.staged() as batch:
            await handle_business(
                body,
                bot=bot,
                academic=authority,
                queue=queue,
                reminders=reminders,
                config=None,
                state=receipts,
            )
        return batch

    try:
        await asyncio.wait_for(
            start_consumer(
                "amqp://synthetic",
                SimpleNamespace(dispatch=dispatch, snapshot_progress=receipts.snapshot),
                guard,
            ),
            3,
        )
        assert state.acked and not state.published
        assert [call.kwargs["chat_id"] for call in bot.send_message.await_args_list] == [102]
        guard.complete.assert_awaited_once()
    finally:
        await queue.shutdown()


async def test_cancel_during_retry_backoff_keeps_original_on_main_queue(monkeypatch):
    import bot.consumers.event_consumer as module

    state, _, _, _, guard = broker(monkeypatch)

    async def cancelled(delay):
        raise asyncio.CancelledError

    monkeypatch.setattr(module.asyncio, "sleep", cancelled)
    dispatcher = SimpleNamespace(
        snapshot_progress=AsyncMock(side_effect=lambda body: body),
        dispatch=AsyncMock(side_effect=NotificationPreferencesUnavailable("outage")),
    )
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    assert state.requeued and not state.acked and not state.rejected and not state.published
    guard.complete.assert_not_awaited()


@pytest.mark.parametrize("private_transport", [False, True])
async def test_prepared_projection_requires_private_queue_provenance(monkeypatch, private_transport):
    state, operations, _, _, guard = broker(monkeypatch, event=PREPARED, prepared_transport=private_transport)
    dispatcher = SimpleNamespace(
        snapshot_progress=AsyncMock(side_effect=lambda body: body),
        prepare=AsyncMock(),
        dispatch=AsyncMock(return_value=None),
    )
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


@pytest.mark.parametrize("lease", ["busy", "timeout", "authority_failure"])
@pytest.mark.parametrize(
    "invalid",
    [
        "public_prepared",
        "public_prepared_progress",
        "invalid_private_prepared",
        "invalid_private_progress",
    ],
)
async def test_prepared_input_cannot_gain_trust_through_lease_retry(monkeypatch, lease, invalid):
    event = PREPARED
    if invalid == "invalid_private_prepared":
        event = {**PREPARED, "_bot_prepared": {**PREPARED["_bot_prepared"], "version": 99}}
    elif invalid.endswith("progress"):
        delivered = [999] if invalid == "invalid_private_progress" else [1]
        event = {**PREPARED, "_bot_prepared": {**PREPARED["_bot_prepared"], "delivered_user_ids": delivered}}
    state, operations, _, _, guard = broker(
        monkeypatch,
        event=event,
        prepared_transport=invalid.startswith("invalid_private"),
    )
    if lease == "busy":
        guard.claim.return_value = None
    elif lease == "timeout":
        guard.claim.side_effect = TimeoutError()
    else:
        guard.claim.side_effect = NotificationPreferencesUnavailable("synthetic lease failure")
    dispatcher = SimpleNamespace(
        snapshot_progress=AsyncMock(side_effect=lambda body: body), prepare=AsyncMock(), dispatch=AsyncMock()
    )
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    assert state.rejected and not state.acked and not state.published and not state.requeued
    assert operations == ["select", "reject", "commit", "close"]
    guard.claim.assert_not_awaited()
    guard.complete.assert_not_awaited()
    dispatcher.prepare.assert_not_awaited()
    dispatcher.dispatch.assert_not_awaited()


@pytest.mark.parametrize("abort", [TimeoutError(), asyncio.CancelledError(), RuntimeError("synthetic drain abort")])
async def test_abort_after_publish_staging_rolls_back_raw_and_prepared_without_commit(monkeypatch, abort):
    state, operations, _, _, guard = broker(monkeypatch, publish_error=abort)
    dispatcher = SimpleNamespace(
        snapshot_progress=AsyncMock(side_effect=lambda body: body),
        prepare=AsyncMock(return_value=PREPARED),
        dispatch=AsyncMock(),
    )
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    assert "commit" not in operations and "nack" not in operations
    assert not state.acked and not state.published and not state.rejected and state.requeued
    guard.complete.assert_not_awaited()
    guard.release.assert_not_awaited()
    dispatcher.dispatch.assert_not_awaited()


async def test_abort_during_automatic_ack_after_staging_never_commits_projection(monkeypatch):
    state, operations, _, _, guard = broker(monkeypatch, ack_error=TimeoutError())
    dispatcher = SimpleNamespace(
        snapshot_progress=AsyncMock(side_effect=lambda body: body),
        prepare=AsyncMock(return_value=PREPARED),
        dispatch=AsyncMock(),
    )
    await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
    assert "commit" not in operations and "nack" not in operations
    assert not state.acked and not state.published and state.requeued
    guard.release.assert_not_awaited()


@pytest.mark.parametrize("prepared", [False, True])
@pytest.mark.parametrize("parent", [None, "malformed", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01"])
async def test_real_consumer_restores_context_and_starts_delivery_trace(monkeypatch, prepared, parent):
    with monkeypatch.context() as env:
        env.setenv("OTEL_SDK_DISABLED", "false")
        provider = TracerProvider()
    monkeypatch.setattr(trace, "get_tracer", provider.get_tracer)
    request_id = "11111111-2222-3333-4444-555555555555"
    event = {**(PREPARED if prepared else EVENT), "traceparent": parent, "request_id": request_id}
    state, _, _, _, guard = broker(monkeypatch, event=event, prepared_transport=prepared)
    captured = {}

    async def dispatch(body):
        captured.update(structlog.contextvars.get_contextvars())
        captured["span"] = trace.get_current_span().get_span_context()

    dispatcher = SimpleNamespace(
        snapshot_progress=AsyncMock(side_effect=lambda body: body),
        prepare=AsyncMock(return_value=None),
        dispatch=dispatch,
    )
    before = EVENT_OUTCOMES.labels("processed")._value.get()
    structlog.contextvars.bind_contextvars(requestId="outer")
    try:
        await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
        assert captured["requestId"] == request_id
        assert "trace_id" not in captured  # Noncanonical business correlation is not telemetry.
        assert captured["span"].is_valid
        actual_trace = format(captured["span"].trace_id, "032x")
        assert actual_trace != "0123456789abcdef0123456789abcdef"
        assert EVENT_OUTCOMES.labels("processed")._value.get() == before + 1
        assert state.acked
        assert structlog.contextvars.get_contextvars() == {"requestId": "outer"}
        assert not trace.get_current_span().get_span_context().is_valid
    finally:
        structlog.contextvars.clear_contextvars()
        provider.shutdown()


@pytest.mark.parametrize(
    "field,cancel",
    [(field, False) for field in ("trace_id", "event_id", "event_type", "request_id", "traceparent")]
    + [(None, False), ("trace_id", True)],
)
async def test_consumer_json_never_copies_malformed_envelope_metadata(monkeypatch, field, cancel):
    marker = "SYNTHETIC_PRIVATE_MARKER"
    canonical_id = "11111111-2222-3333-4444-555555555555"
    event = {**EVENT, "trace_id": canonical_id, "request_id": canonical_id}
    if field:
        event[field] = marker
    state, _, _, _, guard = broker(monkeypatch, event=event)
    with monkeypatch.context() as env:
        env.setenv("OTEL_SDK_DISABLED", "false")
        provider = TracerProvider()
    monkeypatch.setattr(trace, "get_tracer", provider.get_tracer)
    original_handlers = list(logging.getLogger().handlers)
    setup_observability()
    output = io.StringIO()
    handler = logging.StreamHandler(output)
    handler.setFormatter(logging.getLogger().handlers[0].formatter)
    logging.getLogger().handlers = [handler]
    received = []
    if field == "event_type":
        monkeypatch.setattr(logging.getLogger("bot.consumers.event_dispatcher"), "level", logging.DEBUG)

    async def dispatch(body):
        received.append(body)
        structlog.get_logger("test").info("handler")
        if field == "event_type":
            from bot.consumers.event_dispatcher import EventDispatcher

            actual_dispatcher = EventDispatcher.__new__(EventDispatcher)
            actual_dispatcher._handlers = {}
            await actual_dispatcher.dispatch(body)
        if cancel:
            raise asyncio.CancelledError

    dispatcher = SimpleNamespace(
        snapshot_progress=AsyncMock(side_effect=lambda body: body),
        prepare=AsyncMock(return_value=None),
        dispatch=dispatch,
    )
    structlog.contextvars.clear_contextvars()
    structlog.contextvars.bind_contextvars(requestId="outer-request")
    try:
        await asyncio.wait_for(start_consumer("amqp://synthetic", dispatcher, guard), 3)
        assert received == [event]  # Telemetry does not rewrite or reject the business envelope.
        assert state.requeued if cancel else state.acked
        captured = output.getvalue()
        assert marker not in captured
        lines = [json.loads(line) for line in captured.splitlines()]
        if field == "event_type":
            assert any(line["level"] == "debug" and line["msg"] == "Unhandled event type: unknown" for line in lines)
        inside = next(line for line in lines if line["msg"] == "handler")
        assert inside["event_type"] == ("unknown" if field == "event_type" else EVENT["event_type"])
        if field == "trace_id":
            assert "trace_id" not in inside
        else:
            assert inside["trace_id"] == canonical_id
        if field == "event_id":
            assert "event_id" not in inside
        else:
            assert inside["event_id"] == EVENT["event_id"]
        if field == "request_id":
            assert "requestId" not in inside
        else:
            assert inside["requestId"] == canonical_id
        assert len(inside["traceId"]) == 32 and len(inside["spanId"]) == 16
        assert structlog.contextvars.get_contextvars() == {"requestId": "outer-request"}
        assert not trace.get_current_span().get_span_context().is_valid
    finally:
        logging.getLogger().handlers = original_handlers
        structlog.contextvars.clear_contextvars()
        provider.shutdown()
