"""Tests for bot.middlewares.observability_middleware."""

from __future__ import annotations

import asyncio
import os

import pytest
import structlog
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider

os.environ["OTEL_SDK_DISABLED"] = "true"

from bot.middlewares import ObservabilityMiddleware  # noqa: E402


class _FakeUser:
    def __init__(self, user_id: int) -> None:
        self.id = user_id


class _FakeMessage:
    def __init__(self, user_id: int | None = None) -> None:
        self.from_user = _FakeUser(user_id) if user_id is not None else None


class _FakeCallbackQuery:
    def __init__(self, user_id: int, data: str) -> None:
        self.from_user = _FakeUser(user_id)
        self.data = data


@pytest.fixture(autouse=True)
def _clear_contextvars(monkeypatch):
    with monkeypatch.context() as env:
        env.setenv("OTEL_SDK_DISABLED", "false")
        provider = TracerProvider()
    monkeypatch.setattr(trace, "get_tracer", provider.get_tracer)
    structlog.contextvars.clear_contextvars()
    yield
    structlog.contextvars.clear_contextvars()
    provider.shutdown()


@pytest.mark.asyncio
async def test_middleware_binds_user_and_trace():
    middleware = ObservabilityMiddleware()
    captured: dict[str, object] = {}

    async def handler(event, data):
        ctx = structlog.contextvars.get_contextvars()
        captured.update(ctx)
        captured["span"] = trace.get_current_span().get_span_context()
        return "ok"

    result = await middleware(handler, _FakeMessage(user_id=777), {})

    assert result == "ok"
    assert captured["user_id"] == 777
    assert captured["callback_type"] == "_fakemessage"
    assert "trace_id" not in captured
    assert captured["span"].is_valid
    # Context очищен после middleware.
    assert structlog.contextvars.get_contextvars() == {}


@pytest.mark.asyncio
async def test_middleware_never_captures_callback_data():
    middleware = ObservabilityMiddleware()
    captured: dict[str, object] = {}
    long_data = "x" * 200

    async def handler(event, data):
        captured.update(structlog.contextvars.get_contextvars())

    await middleware(handler, _FakeCallbackQuery(1, long_data), {})

    assert "callback_data" not in captured


@pytest.mark.asyncio
async def test_middleware_handles_anonymous_event():
    middleware = ObservabilityMiddleware()
    captured: dict[str, object] = {}

    async def handler(event, data):
        captured.update(structlog.contextvars.get_contextvars())

    # event без from_user (ChannelPost, EditedChannelPost и т.д.) — middleware
    # не должен падать.
    await middleware(handler, _FakeMessage(user_id=None), {})

    assert "user_id" not in captured
    assert "trace_id" not in captured
    assert captured["callback_type"] == "_fakemessage"


@pytest.mark.parametrize("error", [RuntimeError, asyncio.CancelledError])
async def test_middleware_failure_restores_log_and_active_trace_context(error):
    structlog.contextvars.bind_contextvars(requestId="outer-request")
    with trace.get_tracer(__name__).start_as_current_span("outer") as outer:

        async def handler(event, data):
            assert "requestId" not in structlog.contextvars.get_contextvars()
            assert trace.get_current_span().get_span_context().trace_id != outer.get_span_context().trace_id
            raise error()

        with pytest.raises(error):
            await ObservabilityMiddleware()(handler, _FakeMessage(1), {})
        assert trace.get_current_span() is outer
    assert structlog.contextvars.get_contextvars() == {"requestId": "outer-request"}
