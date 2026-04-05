"""Tests for JwtRedisClient — JSON JWT storage in Redis with TTL."""
from unittest.mock import AsyncMock, patch

import pytest

from bot.services.jwt_redis_client import JwtRedisClient

KEY_PREFIX = "bot:jwt:"
TTL = 604800


def _make_client(redis) -> JwtRedisClient:
    return JwtRedisClient(
        key_prefix=KEY_PREFIX,
        ttl=TTL,
        redis_client=redis,
    )


@pytest.mark.asyncio
async def test_save_and_get(fake_redis):
    """Saved access and refresh tokens are returned as dict."""
    client = _make_client(fake_redis)
    await client.save(12345, "access_abc", "refresh_xyz", 900)

    result = await client.get(12345)
    assert result is not None
    assert result["access_token"] == "access_abc"
    assert result["refresh_token"] == "refresh_xyz"


@pytest.mark.asyncio
async def test_get_nonexistent(fake_redis):
    """Returns None for unknown telegram_id."""
    client = _make_client(fake_redis)

    result = await client.get(99999)
    assert result is None


@pytest.mark.asyncio
async def test_delete(fake_redis):
    """After delete, get returns None."""
    client = _make_client(fake_redis)
    await client.save(111, "tok_a", "ref_b", 900)
    await client.delete(111)

    result = await client.get(111)
    assert result is None


@pytest.mark.asyncio
async def test_save_overwrites(fake_redis):
    """Saving twice with different tokens returns the latest values."""
    client = _make_client(fake_redis)
    await client.save(222, "old_access", "old_refresh", 900)
    await client.save(222, "new_access", "new_refresh", 900)

    result = await client.get(222)
    assert result is not None
    assert result["access_token"] == "new_access"
    assert result["refresh_token"] == "new_refresh"


@pytest.mark.asyncio
async def test_ttl_set(fake_redis):
    """After save, the Redis key has a positive TTL."""
    client = _make_client(fake_redis)
    await client.save(333, "acc", "ref", 900)

    key = f"{KEY_PREFIX}333"
    ttl = await fake_redis.ttl(key)
    assert ttl > 0


@pytest.mark.asyncio
async def test_ttl_uses_max_of_expires_in_and_default(fake_redis):
    """When expires_in < default TTL, default TTL is used (to keep refresh token alive)."""
    client = _make_client(fake_redis)
    # expires_in=900 (access token) < ttl=604800 (refresh token) — should use 604800
    await client.save(444, "acc", "ref", 900)

    key = f"{KEY_PREFIX}444"
    ttl = await fake_redis.ttl(key)
    # TTL should be approximately 604800 (within 5 seconds of execution)
    assert ttl > 604790


@pytest.mark.asyncio
async def test_redis_down_save_graceful(fake_redis):
    """ConnectionError during set is swallowed — no exception raised to caller."""
    client = _make_client(fake_redis)
    with patch.object(fake_redis, "set", new=AsyncMock(side_effect=ConnectionError("down"))):
        # Must not raise
        await client.save(555, "acc", "ref", 900)


@pytest.mark.asyncio
async def test_redis_down_get_graceful(fake_redis):
    """ConnectionError during get is swallowed — returns None."""
    client = _make_client(fake_redis)
    with patch.object(fake_redis, "get", new=AsyncMock(side_effect=ConnectionError("down"))):
        result = await client.get(666)
    assert result is None
