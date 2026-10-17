"""Tests for bot.observability — structlog JSON + trace context binding.

OTLP exporter отключаем через ``OTEL_SDK_DISABLED=true`` (env set in-process),
чтобы тесты не пытались достучаться до tempo:4317.
"""

from __future__ import annotations

import io
import json
import logging
import os
import threading
from types import SimpleNamespace

import pytest
import structlog
from opentelemetry import trace
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter

os.environ["OTEL_SDK_DISABLED"] = "true"

from bot.observability import (  # noqa: E402
    SafeSpanExporter,
    _exclude_telegram_urls,
    bind_trace_context,
    event_trace_context,
    setup_observability,
)


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


@pytest.mark.parametrize("legacy", ["11111111-2222-3333-4444-555555555555", "0123456789abcdef", "0123456789abcdef" * 2])
def test_bind_trace_context_injects_and_restores(legacy):
    setup_observability()
    buf, _ = _capture_stdout_handler()
    logger = structlog.get_logger("test")

    with bind_trace_context(legacy, user_id=42, callback_type="message"):
        logger.info("inside")
    logger.info("outside")

    lines = [json.loads(x) for x in buf.getvalue().strip().splitlines()]
    inside = next(line for line in lines if line["msg"] == "inside")
    outside = next(line for line in lines if line["msg"] == "outside")

    assert inside["trace_id"] == legacy
    # G11 M-sec-2: user_id маскируется — plaintext 42 не сохраняется.
    assert inside["user_id"] == "***"
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


@pytest.fixture
def sdk(monkeypatch):
    exporter = InMemorySpanExporter()
    # SDK 1.41 reads OTEL_SDK_DISABLED at provider construction too. Enable
    # this isolated in-memory provider, then restore the network bootstrap guard.
    with monkeypatch.context() as env:
        env.setenv("OTEL_SDK_DISABLED", "false")
        provider = TracerProvider(
            resource=Resource({"service.name": "notification-bot", "password": "resource-marker"})
        )
    provider.add_span_processor(SimpleSpanProcessor(SafeSpanExporter(exporter)))
    monkeypatch.setattr(trace, "get_tracer", provider.get_tracer)
    yield provider.get_tracer("synthetic"), exporter
    provider.shutdown()


def test_actual_sdk_export_boundary_strips_all_untrusted_surfaces(sdk):
    tracer, exporter = sdk
    markers = ("url-marker", "callback-marker", "credential-marker", "event-marker", "link-marker", "status-marker")
    context = trace.SpanContext(123, 456, True, trace.TraceFlags(1), trace.TraceState([("secret", "state-marker")]))
    with tracer.start_as_current_span(
        "https://api.telegram.org/boturl-marker/sendMessage",
        links=[trace.Link(context, {"credential": markers[4]})],
    ) as span:
        span.set_attribute("http.url", f"https://api.telegram.org/bot{markers[0]}/sendMessage")
        span.set_attribute("callback_data", markers[1])
        span.set_attribute("password", markers[2])
        span.set_attribute("http.response.status_code", 200)
        span.add_event(markers[3], {"password": markers[2]})
        span.set_status(trace.Status(trace.StatusCode.ERROR, markers[5]))
    (exported,) = exporter.get_finished_spans()
    serialized = exported.to_json()
    for marker in (*markers, "resource-marker", "state-marker"):
        assert marker not in serialized
    assert exported.attributes == {"http.response.status_code": 200}
    assert exported.status.status_code == trace.StatusCode.ERROR
    assert exported.context.is_valid
    assert exported.links[0].context.trace_id == 123


def test_production_batch_export_bootstrap_sanitizes_and_reaps_sdk_thread(monkeypatch):
    from opentelemetry.instrumentation.aio_pika import AioPikaInstrumentor
    from opentelemetry.instrumentation.aiohttp_client import AioHttpClientInstrumentor
    from opentelemetry.instrumentation.grpc import GrpcAioInstrumentorClient
    from opentelemetry.instrumentation.redis import RedisInstrumentor

    import bot.observability as module

    before_threads = {thread.ident for thread in threading.enumerate()}
    exporter = InMemorySpanExporter()
    providers = []
    monkeypatch.setenv("OTEL_SDK_DISABLED", "false")
    monkeypatch.setenv("OTEL_PYTHON_AIOHTTP_CLIENT_EXCLUDED_URLS", "")
    monkeypatch.setattr(module, "OTLPSpanExporter", lambda **kwargs: exporter)
    monkeypatch.setattr(trace, "set_tracer_provider", providers.append)
    for instrumentor in (AioPikaInstrumentor, AioHttpClientInstrumentor, GrpcAioInstrumentorClient, RedisInstrumentor):
        monkeypatch.setattr(instrumentor, "instrument", lambda self: None)
    module._configure_tracing()
    (provider,) = providers
    try:
        with provider.get_tracer("synthetic").start_as_current_span("/botbootstrap-marker/getUpdates") as span:
            span.set_attribute("callback_data", "bootstrap-callback-marker")
        assert provider.force_flush()
        (exported,) = exporter.get_finished_spans()
        assert exported.context.is_valid
        assert "bootstrap-marker" not in exported.to_json()
        assert "bootstrap-callback-marker" not in exported.to_json()
    finally:
        provider.shutdown()
    assert {thread.ident for thread in threading.enumerate()} == before_threads


@pytest.mark.asyncio
async def test_pinned_aiohttp_instrumentation_excludes_token_urls_without_network(monkeypatch):
    from opentelemetry.instrumentation.aiohttp_client import create_trace_config
    from yarl import URL

    monkeypatch.setenv("OTEL_PYTHON_AIOHTTP_CLIENT_EXCLUDED_URLS", "internal/ignored")
    _exclude_telegram_urls()
    provider = TracerProvider()
    config = create_trace_config(tracer_provider=provider)
    # The actual pinned instrumentation's request-start callback uses its URL
    # exclusion before starting a span or injecting request trace headers.
    context = config.trace_config_ctx()
    params = SimpleNamespace(method="POST", url=URL("https://api.telegram.org/boturl-marker/sendMessage"), headers={})
    for callback in config.on_request_start:
        await callback(None, context, params)
    assert context.span is None
    assert params.headers == {}
    provider.shutdown()


def test_json_structlog_and_stdlib_redact_credentials_including_exceptions(sdk):
    setup_observability()
    buf, _ = _capture_stdout_handler()
    tracer, _ = sdk
    with tracer.start_as_current_span("safe") as span:
        structlog.get_logger("test").info(
            "safe",
            callback_data="callback-marker",
            nested={"password": "password-marker", "token": "token-marker"},
        )
        logging.getLogger("test").warning(
            'Provider {"initial_password": "quoted password-marker with spaces", '
            '"callback_data": "callback-marker with spaces", "access_token":"token-marker"}'
        )
        logging.getLogger("test").warning("Authorization: Bearer token-marker")
        try:
            raise ValueError("https://api.telegram.org/boturl-marker/sendMessage callback_data=callback-marker")
        except ValueError:
            logging.getLogger("test").exception("Provider https://user:pass-marker@server/ /boturl-marker/getUpdates")
    output = buf.getvalue()
    for marker in ("callback-marker", "password-marker", "token-marker", "url-marker", "pass-marker"):
        assert marker not in output
    lines = [json.loads(line) for line in output.splitlines()]
    assert all(line["traceId"] == format(span.get_span_context().trace_id, "032x") for line in lines)
    assert all(line["spanId"] == format(span.get_span_context().span_id, "016x") for line in lines)
    assert lines[-1]["exception_type"] == "ValueError"


@pytest.mark.parametrize("parent", [None, "unknown", "00-" + "0" * 32 + "-" + "0" * 16 + "-01"])
def test_legacy_or_invalid_parent_never_becomes_tempo_id_and_context_restores(sdk, parent):
    _, exporter = sdk
    legacy = "11111111-2222-3333-4444-555555555555"
    structlog.contextvars.bind_contextvars(requestId="outer-request", trace_id="outer-legacy")
    with pytest.raises(RuntimeError):
        with event_trace_context({"trace_id": legacy, "traceparent": parent, "request_id": "invalid"}):
            assert structlog.contextvars.get_contextvars() == {"trace_id": legacy}
            context = trace.get_current_span().get_span_context()
            assert context.is_valid and format(context.trace_id, "032x") != legacy.replace("-", "")
            raise RuntimeError("synthetic")
    assert structlog.contextvars.get_contextvars() == {"requestId": "outer-request", "trace_id": "outer-legacy"}
    (exported,) = exporter.get_finished_spans()
    assert exported.parent is None


def test_durable_w3c_parent_and_request_correlation_log_matches_exported_span(sdk):
    setup_observability()
    buf, _ = _capture_stdout_handler()
    _, exporter = sdk
    request_id = "11111111-2222-3333-4444-555555555555"
    with event_trace_context(
        {
            "traceparent": "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01",
            "request_id": request_id,
            "trace_id": "0123456789abcdef",
        }
    ):
        structlog.get_logger("test").info("consume")
    payload = json.loads(buf.getvalue())
    (exported,) = exporter.get_finished_spans()
    assert payload["traceId"] == "0123456789abcdef0123456789abcdef"
    assert payload["spanId"] == format(exported.context.span_id, "016x")
    assert exported.parent.span_id == int("0123456789abcdef", 16)
    assert exported.context.span_id != exported.parent.span_id
    assert exported.attributes["requestId"] == payload["requestId"] == request_id
    assert payload["trace_id"] == "0123456789abcdef"
    assert not trace.get_current_span().get_span_context().is_valid
    assert structlog.contextvars.get_contextvars() == {}
