import pytest_asyncio
import fakeredis


@pytest_asyncio.fixture
async def fake_redis():
    """In-process Redis mock for unit tests. Supports full redis.asyncio API."""
    r = fakeredis.FakeAsyncRedis(decode_responses=True)
    yield r
    await r.aclose()
