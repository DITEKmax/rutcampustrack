import pytest_asyncio
import fakeredis.aioredis


@pytest_asyncio.fixture
async def fake_redis():
    """In-process Redis mock for unit tests. Supports full redis.asyncio API."""
    r = fakeredis.aioredis.FakeRedis(decode_responses=True)
    yield r
    await r.aclose()
