"""M04 Группа 7 — aiogram middleware, который привязывает observability
контекст к текущему handler'у.

Вставляет в structlog contextvars:

* ``user_id`` — ``event.from_user.id`` (если есть).
* ``callback_type`` — класс события (``message``, ``callback_query`` и т.д.)
  и callback ``data`` первые 60 символов для debug.
* ``trace_id`` — новый uuid per-update, чтобы все логи одного
  взаимодействия (message → router → handler → RabbitMQ publish)
  связывались.

Middleware глобальный (регистрируется на ``Dispatcher``), поэтому ловит
и ``Message``, и ``CallbackQuery``, и ``InlineQuery`` — для всех полей
безопасный fallback на ``None``.
"""

from __future__ import annotations

import uuid
from typing import Any, Awaitable, Callable

from aiogram import BaseMiddleware
from aiogram.types import TelegramObject

from bot.observability import bind_trace_context


class ObservabilityMiddleware(BaseMiddleware):
    async def __call__(
        self,
        handler: Callable[[TelegramObject, dict[str, Any]], Awaitable[Any]],
        event: TelegramObject,
        data: dict[str, Any],
    ) -> Any:
        user = getattr(event, "from_user", None)
        user_id = getattr(user, "id", None) if user is not None else None

        callback_type = event.__class__.__name__.lower()
        callback_data: str | None = None
        # CallbackQuery.data — короткая строка; первые 60 символов достаточно
        # для отладки без риска утечки PII.
        raw = getattr(event, "data", None)
        if isinstance(raw, str):
            callback_data = raw[:60]

        trace_id = uuid.uuid4().hex

        with bind_trace_context(
            trace_id,
            user_id=user_id,
            callback_type=callback_type,
            callback_data=callback_data,
        ):
            return await handler(event, data)
