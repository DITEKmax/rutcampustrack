"""Tests for bot.observability — structlog JSON + trace context binding.

OTLP exporter отключаем через ``OTEL_SDK_DISABLED=true`` (env set in-process),
чтобы тесты не пытались достучаться до tempo:4317.
"""

from __future__ import annotations

import io
import json
import logging
import os

import pytest
import structlog

os.environ["OTEL_SDK_DISABLED"] = "true"

from bot.observability import bind_trace_context, setup_observability  # noqa: E402


@pytest.fixture(autouse=True)
def _reset_structlog():
    """Сбрасываем structlog/stdlib между тестами, чтобы handler'ы и
    contextvars не утекали между кейсами."""
    structlog.reset_defaults()
    structlog.contextvars.clear_contextvars()
    for name in ("", "bot", "test"):
        logger = logging.getLogger(name)
        for h in list(logger.handlers):
            logger.removeHandler(h)
    yield
    structlog.reset_defaults()
    structlog.contextvars.clear_contextvars()


def _capture_stdout_handler() -> tuple[io.StringIO, logging.Handler]:
    """Заменяет единственный stdout handler root'а на StringIO."""
    buf = io.StringIO()
    root = logging.getLogger()
    target_formatter = root.handlers[0].formatter if root.handlers else None
    handler = logging.StreamHandler(buf)
    if target_formatter is not None:
        handler.setFormatter(target_formatter)
    for existing in list(root.handlers):
        root.removeHandler(existing)
    root.addHandler(handler)
    return buf, handler


def test_setup_observability_produces_json_logs():
    setup_observability()
    buf, _ = _capture_stdout_handler()

    logger = structlog.get_logger("test")
    logger.info("hello-world", foo="bar")

    line = buf.getvalue().strip().splitlines()[-1]
    payload = json.loads(line)

    assert payload["msg"] == "hello-world"
    assert payload["service"] == "notification-bot"
    assert payload["foo"] == "bar"
    assert payload["level"] == "info"
    assert "ts" in payload
    assert payload["logger"] == "test"


def test_bind_trace_context_injects_and_restores():
    setup_observability()
    buf, _ = _capture_stdout_handler()
    logger = structlog.get_logger("test")

    with bind_trace_context("trace-abc", user_id=42, callback_type="message"):
        logger.info("inside")
    logger.info("outside")

    lines = [json.loads(x) for x in buf.getvalue().strip().splitlines()]
    inside = next(line for line in lines if line["msg"] == "inside")
    outside = next(line for line in lines if line["msg"] == "outside")

    assert inside["trace_id"] == "trace-abc"
    assert inside["user_id"] == 42
    assert inside["callback_type"] == "message"
    assert "trace_id" not in outside
    assert "user_id" not in outside


def test_bind_trace_context_skips_none_values():
    setup_observability()
    buf, _ = _capture_stdout_handler()
    logger = structlog.get_logger("test")

    with bind_trace_context(None, user_id=None, callback_type=None):
        logger.info("no-binds")

    line = json.loads(buf.getvalue().strip().splitlines()[-1])
    assert "trace_id" not in line
    assert "user_id" not in line


def test_stdlib_logger_passes_through_json_formatter():
    """aiogram/aio-pika используют stdlib logging — их логи тоже должны
    приходить в JSON."""
    setup_observability()
    buf, _ = _capture_stdout_handler()

    std_logger = logging.getLogger("aio_pika")
    std_logger.info("stdlib line")

    line = json.loads(buf.getvalue().strip().splitlines()[-1])
    assert line["msg"] == "stdlib line"
    assert line["service"] == "notification-bot"
    assert line["logger"] == "aio_pika"
