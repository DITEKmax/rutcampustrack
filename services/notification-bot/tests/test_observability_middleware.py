"""Tests for bot.middlewares.observability_middleware."""

from __future__ import annotations

import os

import pytest
import structlog

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
def _clear_contextvars():
    structlog.contextvars.clear_contextvars()
    yield
    structlog.contextvars.clear_contextvars()


@pytest.mark.asyncio
async def test_middleware_binds_user_and_trace():
    middleware = ObservabilityMiddleware()
    captured: dict[str, object] = {}

    async def handler(event, data):
        ctx = structlog.contextvars.get_contextvars()
        captured.update(ctx)
        return "ok"

    result = await middleware(handler, _FakeMessage(user_id=777), {})

    assert result == "ok"
    assert captured["user_id"] == 777
    assert captured["callback_type"] == "_fakemessage"
    assert "trace_id" in captured
    # Context очищен после middleware.
    assert structlog.contextvars.get_contextvars() == {}


@pytest.mark.asyncio
async def test_middleware_captures_callback_data_truncated():
    middleware = ObservabilityMiddleware()
    captured: dict[str, object] = {}
    long_data = "x" * 200

    async def handler(event, data):
        captured.update(structlog.contextvars.get_contextvars())

    await middleware(handler, _FakeCallbackQuery(1, long_data), {})

    assert captured["callback_data"] == "x" * 60


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
    assert "trace_id" in captured
    assert captured["callback_type"] == "_fakemessage"
