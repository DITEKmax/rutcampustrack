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
import sys
from contextlib import contextmanager
from typing import Iterator

import structlog
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

SERVICE_NAME = "notification-bot"


def _rename_event_key(_, __, event_dict: dict) -> dict:
    """structlog пишет сообщение в ключ ``event`` — переименовываем в ``msg``
    ради консистентности с Java JSON-логами."""
    if "event" in event_dict:
        event_dict["msg"] = event_dict.pop("event")
    return event_dict


def _add_service_name(_, __, event_dict: dict) -> dict:
    event_dict.setdefault("service", SERVICE_NAME)
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

    resource = Resource.create(
        {
            "service.name": SERVICE_NAME,
            "service.namespace": "rutcampustrack",
        }
    )
    provider = TracerProvider(resource=resource)
    # insecure=True — внутренний network (private_net), без TLS. M06
    # добавит mTLS по всей observability-цепочке.
    exporter = OTLPSpanExporter(endpoint=endpoint, insecure=True)
    provider.add_span_processor(BatchSpanProcessor(exporter))
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


@contextmanager
def bind_trace_context(trace_id: str | None, **extra: object) -> Iterator[None]:
    """Привязывает ``trace_id`` + произвольные поля к structlog contextvars.

    Используется в aiogram middleware (вход от пользователя) и aio-pika
    consumer'е (извлечение ``trace_id`` из envelope). По выходу из блока
    предыдущие contextvars восстанавливаются — безопасно при concurrent
    handler'ах (каждый asyncio.Task имеет свой contextvars snapshot).
    """

    bindings: dict[str, object] = {k: v for k, v in extra.items() if v is not None}
    if trace_id:
        bindings["trace_id"] = trace_id
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


def get_logger(name: str | None = None) -> structlog.stdlib.BoundLogger:
    return structlog.get_logger(name or SERVICE_NAME)
