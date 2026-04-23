from unittest.mock import AsyncMock, MagicMock

import fakeredis
import pytest
import pytest_asyncio


@pytest_asyncio.fixture
async def fake_redis():
    """In-process Redis mock for unit tests. Supports full redis.asyncio API."""
    r = fakeredis.FakeAsyncRedis(decode_responses=True)
    yield r
    await r.aclose()


@pytest.fixture
def callback_query_factory():
    """M09 G4.1 — factory для Aiogram CallbackQuery mock'ов.

    Возвращает callable: ``make(data, user_id=777, message_text="…",
    caption=None, has_document=False)``.

    Минимальный surface, покрывающий текущие handlers:
    ``callback.data``, ``callback.from_user.id``, ``callback.answer(...)``,
    ``callback.message.{text,caption,edit_text,edit_caption}``.

    AsyncMock'и доступны через ``make(...).answer``, ``.message.edit_text``
    и т.д. для ``assert_awaited_once_with(...)``.
    """

    def _make(
        data: str,
        user_id: int = 777,
        message_text: str = "Иванов И. пропустил пару",
        caption: str | None = None,
        has_document: bool = False,
    ) -> MagicMock:
        callback = MagicMock()
        callback.data = data
        callback.from_user = MagicMock()
        callback.from_user.id = user_id
        callback.answer = AsyncMock()

        message = MagicMock()
        message.text = message_text if not has_document else None
        message.caption = caption
        message.edit_text = AsyncMock()
        message.edit_caption = AsyncMock()
        callback.message = message
        return callback

    return _make


@pytest.fixture
def event_publisher_mock():
    """M09 G4.1 — AsyncMock для EventPublisher.publish(event_type, payload)."""
    publisher = MagicMock()
    publisher.publish = AsyncMock()
    return publisher


@pytest.fixture
def academic_client_mock():
    """M09 G6.2 — AsyncMock для AcademicGrpcClient.get_user_by_telegram_id.

    По умолчанию возвращает found + is_headman=True (чтобы handler'ы
    проходили role check в happy-path). Тесты не-старосты override'ят
    return_value на `found=True, is_headman=False` или `found=False`.
    """
    client = MagicMock()
    user = MagicMock()
    user.found = True
    user.is_headman = True
    client.get_user_by_telegram_id = AsyncMock(return_value=user)
    return client
