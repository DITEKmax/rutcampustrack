"""M04 Группа 7 — observability init для notification-bot.

Централизует две вещи:

1. **JSON-логи** через structlog + stdlib logging bridge. Формат совместим
   с logstash-logback-encoder схемой Java-сервисов (поля ``ts``, ``level``,
   ``logger``, ``thread``, ``msg``, ``service``) + context-переменные
   (``trace_id``, ``user_id``, ``callback_type``, ``event_type``).

2. **Distributed tracing** через OpenTelemetry SDK. Экспорт OTLP gRPC в
   Tempo (env ``OTEL_EXPORTER_OTLP_ENDPOINT``, default ``http://tempo:4317``).
   Instrumentation для aio-pika / aiohttp / grpc / redis регистрируется
   здесь же, чтобы ``__main__.py`` оставался тонким.

Публичные функции:

* :func:`setup_observability()` — вызвать один раз при старте бота.
* :func:`bind_trace_context(trace_id, **extra)` — context-manager для
  аiогram middleware и aio-pika consumer'ов.
* :func:`get_logger(name)` — тонкая обёртка над ``structlog.get_logger``.
"""

from __future__ import annotations

import logging
import os
import re
import socket
import sys
from contextlib import contextmanager
from typing import Iterator

import structlog
from opentelemetry import trace
from opentelemetry.context import Context
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import ReadableSpan, TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor, SpanExporter
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator
from prometheus_client import CollectorRegistry, Counter, Gauge, GCCollector, PlatformCollector, ProcessCollector
from prometheus_client.core import CounterMetricFamily, GaugeMetricFamily

SERVICE_NAME = "notification-bot"
_IDENTITY_KEYS = ("deployment.environment", "deployment.environment.name", "service.version", "service.instance.id")
_IDENTITY_VALUE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}")


def _identity_resource(resource: Resource) -> Resource:
    attributes = {"service.name": SERVICE_NAME, "service.namespace": "rutcampustrack"}
    for key in _IDENTITY_KEYS:
        value = resource.attributes.get(key)
        if isinstance(value, str) and _IDENTITY_VALUE.fullmatch(value):
            attributes[key] = value
    return Resource(attributes)


_RESOURCE = _identity_resource(Resource.create({"service.instance.id": socket.gethostname()}))
METRICS_REGISTRY = CollectorRegistry()
ProcessCollector(registry=METRICS_REGISTRY)
GCCollector(registry=METRICS_REGISTRY)
PlatformCollector(registry=METRICS_REGISTRY)
EVENT_OUTCOMES = Counter(
    "notification_bot_event_transactions",
    "Broker transaction outcomes; processed does not mean end-user delivery",
    ["outcome"],
    registry=METRICS_REGISTRY,
)
for _outcome in ("processed", "prepared", "duplicate", "deferred", "rejected", "uncertain", "cancelled"):
    EVENT_OUTCOMES.labels(_outcome)
CONSUMER_CONNECTED = Gauge(
    "notification_bot_consumer_connected",
    "Consumer queue iterator is active",
    registry=METRICS_REGISTRY,
)
TASK_ALIVE = Gauge(
    "notification_bot_task_alive",
    "Required long-running task is alive",
    ["task"],
    registry=METRICS_REGISTRY,
)
for _task in ("watchdog", "polling", "keyboard_sync"):
    TASK_ALIVE.labels(_task).set(0)


class SendQueueCollector:
    """Read the existing queue's actual execution counters without changing delivery."""

    def __init__(self, queue):
        self.queue = queue

    def collect(self):
        yield CounterMetricFamily(
            "notification_bot_send_tasks_completed",
            "Send task returned successfully; not a user receipt",
            value=self.queue._total_sent,
        )
        yield CounterMetricFamily(
            "notification_bot_send_tasks_exhausted",
            "Send tasks that exhausted provider attempts",
            value=self.queue._total_failed,
        )
        yield GaugeMetricFamily(
            "notification_bot_send_queue_depth",
            "In-memory pending send tasks; Rabbit owns durable work",
            value=self.queue._queue.qsize(),
        )
        yield GaugeMetricFamily(
            "notification_bot_send_workers_alive",
            "Live send queue workers",
            value=sum(not worker.done() for worker in self.queue._workers),
        )


_UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
_LEGACY_TRACE = re.compile(r"[0-9a-f]{16}|[0-9a-f]{32}")
_TRACEPARENT = re.compile(r"00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")
_TELEGRAM_URL = re.compile(r"(?i)(/bot|/file/bot)[^/\s?\"'<>]+")
_URL_USERINFO = re.compile(r"(\w+://)[^/\s@]+@")
_SECRET_KEY_TEXT = (
    r"[A-Za-z0-9_]*(?:password|passwd|credential|token|secret|otp|callback_data|authorization|cookie)[A-Za-z0-9_]*"
)
_QUOTED_SECRET_VALUE = re.compile(
    rf"(?i)([\"']?{_SECRET_KEY_TEXT}[\"']?\s*[:=]\s*)(?:\"(?:\\.|[^\"\\])*\"|'(?:\\.|[^'\\])*')"
)
_SECRET_VALUE = re.compile(rf"(?i)\b({_SECRET_KEY_TEXT})\s*[:=]\s*[^\s,;]+")
_BEARER = re.compile(r"(?i)\bBearer\s+[^\s,;]+")
_PRIVATE_KEY = re.compile(
    r"(?i)(password|passwd|credential|token|secret|authorization|cookie|callback.?data|otp|payload|body)"
)


def telemetry_id(value: object, *, legacy_trace: bool = False) -> str | None:
    """Diagnostic input only: match the shared Java event correlation contract."""
    if isinstance(value, str) and (_UUID.fullmatch(value) or (legacy_trace and _LEGACY_TRACE.fullmatch(value))):
        return value
    return None


def telemetry_event_type(value: object) -> str:
    from bot.services.notification_prefs import BUSINESS_EVENTS

    if isinstance(value, str) and (
        value in BUSINESS_EVENTS or value in {"otp.requested", "otp.verified", "password.changed", "alert.fired"}
    ):
        return value
    return "unknown"


def _normalize_log_fields(fields: dict) -> dict:
    for key in ("trace_id", "event_id", "request_id", "requestId"):
        if key in fields:
            value = telemetry_id(fields[key], legacy_trace=key == "trace_id")
            if value is None:
                fields.pop(key)
            else:
                fields[key] = value
    if "event_type" in fields:
        fields["event_type"] = telemetry_event_type(fields["event_type"])
    return fields


def _safe_text(value: str) -> str:
    value = _TELEGRAM_URL.sub(r"\1[REDACTED]", value)
    value = _URL_USERINFO.sub(r"\1[REDACTED]@", value)
    value = _BEARER.sub("Bearer [REDACTED]", value)
    value = _QUOTED_SECRET_VALUE.sub(r'\1"[REDACTED]"', value)
    return _SECRET_VALUE.sub(r"\1=[REDACTED]", value)


def _safe_value(value):
    if isinstance(value, dict):
        return {k: "[REDACTED]" if _PRIVATE_KEY.search(str(k)) else _safe_value(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)):
        return [_safe_value(item) for item in value]
    if isinstance(value, str):
        return _safe_text(value)
    if value is None or isinstance(value, (bool, int, float)):
        return value
    return "[REDACTED]"


def _safe_log(_, __, event_dict: dict) -> dict:
    # Exceptions can contain provider request bodies/passwords. Keep the class only.
    error = event_dict.pop("exc_info", None)
    if error:
        if error is True:
            error = sys.exc_info()[1]
        elif isinstance(error, tuple):
            error = error[1]
        event_dict["exception_type"] = type(error).__name__
    event_dict.pop("stack_info", None)
    event_dict.pop("exc_text", None)
    return _safe_value(_normalize_log_fields(event_dict))


def _add_active_span(_, __, event_dict: dict) -> dict:
    # Never synthesize a Tempo ID from legacy business trace_id.
    event_dict.pop("traceId", None)
    event_dict.pop("spanId", None)
    context = trace.get_current_span().get_span_context()
    if context.is_valid:
        event_dict["traceId"] = format(context.trace_id, "032x")
        event_dict["spanId"] = format(context.span_id, "016x")
    return event_dict


class SafeSpanExporter(SpanExporter):
    """Allowlist the actual OTLP boundary, including auto-instrumented spans.

    URLs, exception events/status text, resource extras and link attributes can
    carry credentials. Export only topology/timing/IDs and finite protocol facts.
    """

    def __init__(self, delegate: SpanExporter):
        self._delegate = delegate

    def export(self, spans):
        def clean_context(context):
            if context is None:
                return None
            return trace.SpanContext(
                context.trace_id,
                context.span_id,
                context.is_remote,
                context.trace_flags,
            )

        safe = []
        for span in spans:
            attrs = {}
            for key in ("http.status_code", "http.response.status_code", "rpc.grpc.status_code"):
                value = (span.attributes or {}).get(key)
                if type(value) is int and 0 <= value <= 599:
                    attrs[key] = value
            request_id = (span.attributes or {}).get("requestId")
            if isinstance(request_id, str) and _UUID.fullmatch(request_id):
                attrs["requestId"] = request_id
            names = {
                trace.SpanKind.CLIENT: "client.operation",
                trace.SpanKind.SERVER: "server.operation",
                trace.SpanKind.CONSUMER: "notification-bot.consume",
                trace.SpanKind.PRODUCER: "messaging.publish",
            }
            safe.append(
                ReadableSpan(
                    name=(
                        span.name
                        if span.name in {"notification-bot.update", "notification-bot.consume"}
                        else names.get(span.kind, "notification-bot.operation")
                    ),
                    context=clean_context(span.context),
                    parent=clean_context(span.parent),
                    resource=_identity_resource(span.resource),
                    attributes=attrs,
                    links=[trace.Link(clean_context(link.context)) for link in span.links],
                    kind=span.kind,
                    status=trace.Status(span.status.status_code),
                    start_time=span.start_time,
                    end_time=span.end_time,
                )
            )
        return self._delegate.export(safe)

    def shutdown(self):
        return self._delegate.shutdown()

    def force_flush(self, timeout_millis=30000):
        return self._delegate.force_flush(timeout_millis)


def _rename_event_key(_, __, event_dict: dict) -> dict:
    """structlog пишет сообщение в ключ ``event`` — переименовываем в ``msg``
    ради консистентности с Java JSON-логами."""
    if "event" in event_dict:
        event_dict["msg"] = event_dict.pop("event")
    return event_dict


def _add_service_name(_, __, event_dict: dict) -> dict:
    event_dict.setdefault("service", SERVICE_NAME)
    attributes = _RESOURCE.attributes
    event_dict["environment"] = attributes.get(
        "deployment.environment.name", attributes.get("deployment.environment", "unknown")
    )
    event_dict["version"] = attributes.get("service.version", "unknown")
    event_dict["instance"] = attributes.get("service.instance.id", "unknown")
    return event_dict


# G11 M-sec-2 fix: PII masking для Telegram user_id в structlog
# contextvars (middleware биндит его на каждый update). В Java-сервисах
# аналогичное делает shared-logback (regex masking). Здесь маскируем
# на уровне processor'а — сохраняем отладочную ценность через hash,
# но не храним plaintext ID в Loki.
_SENSITIVE_KEYS = frozenset({"user_id", "telegram_id", "from_user_id", "chat_id"})


def _mask_pii(_, __, event_dict: dict) -> dict:
    for key in _SENSITIVE_KEYS:
        value = event_dict.get(key)
        if value is None:
            continue
        # Сохраняем type-info и последние 3 цифры (для debugging "тот же
        # юзер или другой") + hash. Для int 123456789 → "***789".
        text = str(value)
        event_dict[key] = "***" + text[-3:] if len(text) >= 3 else "***"
    return event_dict


def _configure_structlog() -> None:
    """Настраивает structlog + stdlib bridge.

    stdlib ``logging`` (используемый aiogram, aio-pika, grpcio) проходит
    через ProcessorFormatter → тот же JSON-renderer. Итог — единый формат
    для любых источников.
    """

    timestamper = structlog.processors.TimeStamper(fmt="iso", utc=True, key="ts")

    shared_processors: list = [
        structlog.contextvars.merge_contextvars,
        structlog.processors.add_log_level,
        structlog.stdlib.add_logger_name,
        timestamper,
        _add_service_name,
        _add_active_span,
        _mask_pii,
        _rename_event_key,
    ]

    # structlog → stdlib bridge: ForeignKeyRenderer отдаёт dict обратно
    # ProcessorFormatter'у, который финализирует его JSONRenderer'ом.
    structlog.configure(
        processors=shared_processors
        + [
            structlog.stdlib.ProcessorFormatter.wrap_for_formatter,
        ],
        logger_factory=structlog.stdlib.LoggerFactory(),
        wrapper_class=structlog.stdlib.BoundLogger,
        cache_logger_on_first_use=True,
    )

    formatter = structlog.stdlib.ProcessorFormatter(
        foreign_pre_chain=shared_processors,
        processors=[
            structlog.stdlib.ProcessorFormatter.remove_processors_meta,
            _safe_log,
            structlog.processors.JSONRenderer(),
        ],
    )

    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(formatter)

    root = logging.getLogger()
    # Убираем handler'ы, поставленные `logging.basicConfig` в __main__.py.
    for existing in list(root.handlers):
        root.removeHandler(existing)
    root.addHandler(handler)
    root.setLevel(logging.INFO)

    # Снижаем шум aiogram/aio-pika на INFO (DEBUG слишком болтлив).
    for noisy in ("aiogram.event", "aio_pika", "aiormq", "grpc"):
        logging.getLogger(noisy).setLevel(logging.INFO)


def _configure_tracing() -> None:
    """Регистрирует OTel SDK + OTLP exporter + auto-instrumentation.

    Если ``OTEL_SDK_DISABLED=true`` — tracing отключён (используется в
    unit-тестах, чтобы не тащить сетевой клиент).
    """

    if os.getenv("OTEL_SDK_DISABLED", "").lower() == "true":
        return

    endpoint = os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://tempo:4317")

    provider = TracerProvider(resource=_RESOURCE)
    # insecure=True — внутренний network (private_net), без TLS. M06
    # добавит mTLS по всей observability-цепочке.
    exporter = OTLPSpanExporter(endpoint=endpoint, insecure=True)
    provider.add_span_processor(BatchSpanProcessor(SafeSpanExporter(exporter)))
    trace.set_tracer_provider(provider)

    # Auto-instrumentation — покрывает aio-pika publish/consume, aiohttp
    # клиенты (auth/attendance HTTP), grpc (academic/schedule), redis.
    # Импорты ленивые чтобы ImportError в unit-тестах без extras не валил
    # setup_observability().
    try:
        from opentelemetry.instrumentation.aio_pika import AioPikaInstrumentor

        AioPikaInstrumentor().instrument()
    except Exception:  # pragma: no cover — best-effort instrumentation
        logging.getLogger(__name__).warning("aio_pika instrumentation skipped", exc_info=True)

    try:
        from opentelemetry.instrumentation.aiohttp_client import AioHttpClientInstrumentor

        _exclude_telegram_urls()
        AioHttpClientInstrumentor().instrument()
    except Exception:  # pragma: no cover
        logging.getLogger(__name__).warning("aiohttp instrumentation skipped", exc_info=True)

    try:
        from opentelemetry.instrumentation.grpc import GrpcAioInstrumentorClient

        GrpcAioInstrumentorClient().instrument()
    except Exception:  # pragma: no cover
        logging.getLogger(__name__).warning("grpc instrumentation skipped", exc_info=True)

    try:
        from opentelemetry.instrumentation.redis import RedisInstrumentor

        RedisInstrumentor().instrument()
    except Exception:  # pragma: no cover
        logging.getLogger(__name__).warning("redis instrumentation skipped", exc_info=True)


def setup_observability() -> None:
    """Идемпотентный bootstrap — вызывать один раз из ``__main__.py``."""

    _configure_structlog()
    _configure_tracing()


def _exclude_telegram_urls() -> None:
    # 0.62b0 reads the documented environment setting at ClientSession creation;
    # instrument(excluded_urls=...) is ignored by this instrumentation version.
    name = "OTEL_PYTHON_AIOHTTP_CLIENT_EXCLUDED_URLS"
    existing = os.getenv(name, os.getenv("OTEL_PYTHON_EXCLUDED_URLS", ""))
    telegram = r"(?i:/bot[^/]+/|/file/bot[^/]+/)"
    if telegram not in existing:
        os.environ[name] = ",".join(filter(None, (existing, telegram)))


@contextmanager
def bind_trace_context(trace_id: str | None, **extra: object) -> Iterator[None]:
    """Привязывает ``trace_id`` + произвольные поля к structlog contextvars.

    Используется в aiogram middleware (вход от пользователя) и aio-pika
    consumer'е (извлечение ``trace_id`` из envelope). По выходу из блока
    предыдущие contextvars восстанавливаются — безопасно при concurrent
    handler'ах (каждый asyncio.Task имеет свой contextvars snapshot).
    """

    bindings: dict[str, object] = {k: v for k, v in extra.items() if v is not None}
    if trace_id is not None:
        bindings["trace_id"] = trace_id
    bindings = _normalize_log_fields(bindings)
    if not bindings:
        yield
        return
    # Snapshot текущего contextvars, чтобы восстановить после yield —
    # безопасно при nested contexts (middleware → consumer → handler).
    previous = structlog.contextvars.get_contextvars()
    structlog.contextvars.bind_contextvars(**bindings)
    try:
        yield
    finally:
        # Сброс и восстановление предыдущего состояния.
        structlog.contextvars.clear_contextvars()
        if previous:
            structlog.contextvars.bind_contextvars(**previous)


@contextmanager
def isolated_log_context() -> Iterator[None]:
    """New ingress/worker roots cannot inherit an unrelated request's log fields."""
    previous = structlog.contextvars.get_contextvars()
    structlog.contextvars.clear_contextvars()
    try:
        yield
    finally:
        structlog.contextvars.clear_contextvars()
        structlog.contextvars.bind_contextvars(**previous)


def get_logger(name: str | None = None) -> structlog.stdlib.BoundLogger:
    return structlog.get_logger(name or SERVICE_NAME)


@contextmanager
def event_trace_context(envelope: dict) -> Iterator[None]:
    """Durable W3C parent only; legacy trace_id is a separate business field."""
    parent = Context()
    traceparent = envelope.get("traceparent")
    if isinstance(traceparent, str) and _TRACEPARENT.fullmatch(traceparent):
        parent = TraceContextTextMapPropagator().extract({"traceparent": traceparent}, context=parent)
    request_id = envelope.get("request_id")
    if not isinstance(request_id, str) or not _UUID.fullmatch(request_id):
        request_id = None
    with trace.get_tracer(__name__).start_as_current_span(
        "notification-bot.consume",
        context=parent,
        kind=trace.SpanKind.CONSUMER,
        record_exception=False,
    ) as span:
        if request_id:
            span.set_attribute("requestId", request_id)
        with isolated_log_context():
            with bind_trace_context(envelope.get("trace_id"), requestId=request_id):
                yield
