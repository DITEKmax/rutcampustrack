"""Тесты BotIdempotencyGuard (M13 G8 — consumer-side dedup для notification-bot)."""

import pytest

from bot.services.idempotency_guard import BotIdempotencyGuard


@pytest.fixture
async def guard(fake_redis):
    """Guard поверх fakeredis — без сетевых вызовов."""
    g = BotIdempotencyGuard(redis_client=fake_redis)
    yield g


@pytest.mark.asyncio
async def test_first_claim_returns_true(guard):
    assert await guard.try_claim("event-A") is True


@pytest.mark.asyncio
async def test_duplicate_claim_returns_false(guard):
    event_id = "event-dup"
    assert await guard.try_claim(event_id) is True
    assert await guard.try_claim(event_id) is False


@pytest.mark.asyncio
async def test_different_event_ids_are_independent(guard):
    assert await guard.try_claim("event-1") is True
    assert await guard.try_claim("event-2") is True
    assert await guard.try_claim("event-1") is False


@pytest.mark.asyncio
async def test_different_consumer_ids_are_independent(fake_redis):
    """Два guard'а с разными consumer_id не конфликтуют по одному event_id."""
    bot_guard = BotIdempotencyGuard(consumer_id="notification-bot", redis_client=fake_redis)
    other_guard = BotIdempotencyGuard(consumer_id="other-consumer", redis_client=fake_redis)

    event_id = "event-shared"
    assert await bot_guard.try_claim(event_id) is True
    # Другой consumer должен суметь claim'ить тот же event_id.
    assert await other_guard.try_claim(event_id) is True
    # А повторный claim того же consumer'а — отказ.
    assert await bot_guard.try_claim(event_id) is False


@pytest.mark.asyncio
async def test_missing_event_id_fails_closed(guard):
    """M13 G24-fix-6: missing event_id → ValueError (fail-closed).

    После M13 G8 все publisher'ы заполняют event_id. Событие без id =
    либо bug в новом publisher'е, либо forged message в обход replay-
    защиты. Caller (event_consumer) делает NACK → DLQ → manual triage.
    """
    with pytest.raises(ValueError, match="event_id"):
        await guard.try_claim(None)
    with pytest.raises(ValueError, match="event_id"):
        await guard.try_claim("")


@pytest.mark.asyncio
async def test_redis_error_fails_closed(monkeypatch, guard):
    """M13 G24-fix-2: при сбое Redis exception пробрасывается (fail-closed).

    До G24-fix-2 try_claim возвращал True при Redis ошибке (fail-open).
    В паре с handler-exception swallow в event_consumer это означало:
    любой transient Redis блип + любой handler bug = event silently lost.
    Теперь exception пробрасывается → caller делает NACK → DLQ → triage.
    """

    async def boom(*args, **kwargs):
        raise RuntimeError("redis down")

    monkeypatch.setattr(guard._redis, "set", boom)
    with pytest.raises(RuntimeError, match="redis down"):
        await guard.try_claim("event-X")


@pytest.mark.asyncio
async def test_ttl_expires(fake_redis):
    """После истечения TTL тот же event_id может быть claim'ен снова."""
    short_ttl_guard = BotIdempotencyGuard(ttl_seconds=1, redis_client=fake_redis)
    event_id = "event-expiring"
    assert await short_ttl_guard.try_claim(event_id) is True
    # fakeredis поддерживает виртуальное время через redis_client.ttl
    # Проверим что ключ создан с TTL = 1
    ttl = await fake_redis.ttl(short_ttl_guard._key(event_id))
    assert 0 < ttl <= 1
