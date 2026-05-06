"""Tests for ReminderRedisClient — list-based storage with TTL and graceful degradation."""

from unittest.mock import AsyncMock, patch

import pytest

from bot.services.redis_client import ReminderRedisClient

KEY_TEMPLATE = "reminder:msgs:{lesson_id}:{user_id}"
TTL = 86400


def _make_client(redis) -> ReminderRedisClient:
    return ReminderRedisClient(
        key_template=KEY_TEMPLATE,
        ttl=TTL,
        redis_client=redis,
    )


@pytest.mark.asyncio
async def test_add_and_get_order(fake_redis):
    """Three message_ids stored via rpush are returned in insertion order."""
    client = _make_client(fake_redis)
    await client.add_message_id(1, 10, 100)
    await client.add_message_id(1, 10, 200)
    await client.add_message_id(1, 10, 300)

    result = await client.get_message_ids(1, 10)
    assert result == [100, 200, 300]


@pytest.mark.asyncio
async def test_get_empty(fake_redis):
    """Nonexistent key returns an empty list."""
    client = _make_client(fake_redis)
    result = await client.get_message_ids(999, 999)
    assert result == []


@pytest.mark.asyncio
async def test_delete_key(fake_redis):
    """After delete_key, get_message_ids returns an empty list."""
    client = _make_client(fake_redis)
    await client.add_message_id(2, 20, 111)
    await client.delete_key(2, 20)

    result = await client.get_message_ids(2, 20)
    assert result == []


@pytest.mark.asyncio
async def test_ttl_set(fake_redis):
    """After adding a message_id, the key has a positive TTL."""
    client = _make_client(fake_redis)
    await client.add_message_id(3, 30, 42)

    key = KEY_TEMPLATE.format(lesson_id=3, user_id=30)
    ttl = await fake_redis.ttl(key)
    assert ttl > 0


@pytest.mark.asyncio
async def test_marked_state_roundtrip(fake_redis):
    client = _make_client(fake_redis)

    assert await client.is_student_marked(10, 100) is False

    await client.mark_student_marked(10, 100, "present")

    assert await client.is_student_marked(10, 100) is True
    ttl = await fake_redis.ttl("reminder:marked:10:100")
    assert ttl > 0


@pytest.mark.asyncio
async def test_delete_marked_key(fake_redis):
    client = _make_client(fake_redis)
    await client.mark_student_marked(11, 101, "absent")

    await client.delete_marked_key(11, 101)

    assert await client.is_student_marked(11, 101) is False


@pytest.mark.asyncio
async def test_redis_down_add_graceful(fake_redis):
    """ConnectionError during rpush is swallowed — no exception raised to caller."""
    client = _make_client(fake_redis)
    with patch.object(fake_redis, "rpush", new=AsyncMock(side_effect=ConnectionError("down"))):
        # Must not raise
        await client.add_message_id(4, 40, 99)


@pytest.mark.asyncio
async def test_redis_down_get_graceful(fake_redis):
    """ConnectionError during lrange is swallowed — returns empty list."""
    client = _make_client(fake_redis)
    with patch.object(fake_redis, "lrange", new=AsyncMock(side_effect=ConnectionError("down"))):
        result = await client.get_message_ids(5, 50)
    assert result == []


@pytest.mark.asyncio
async def test_redis_down_marked_read_fails_open(fake_redis):
    client = _make_client(fake_redis)
    with patch.object(fake_redis, "get", new=AsyncMock(side_effect=ConnectionError("down"))):
        result = await client.is_student_marked(5, 50)
    assert result is False
