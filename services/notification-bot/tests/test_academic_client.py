"""Tests for AcademicGrpcClient — in-memory cache and gRPC delegation."""

from unittest.mock import AsyncMock, MagicMock, patch

import grpc
import grpc.aio
import pytest

from bot.grpc_client.academic_client import AcademicGrpcClient


def _make_student(user_id: int, telegram_id: int) -> MagicMock:
    s = MagicMock()
    s.user_id = user_id
    s.telegram_id = telegram_id
    return s


def _make_client_with_stub(stub: AsyncMock) -> AcademicGrpcClient:
    """Create AcademicGrpcClient with injected stub (bypasses real gRPC channel)."""
    client = AcademicGrpcClient.__new__(AcademicGrpcClient)
    client._channel = MagicMock()
    client._stub = stub
    client._cache = {}
    client._metadata = ()
    return client


@pytest.mark.asyncio
async def test_get_group_members():
    """Stub returns 2 students; client exposes them with correct telegram_ids."""
    students = [_make_student(1, 111), _make_student(2, 222)]
    response = MagicMock()
    response.students = students

    stub = MagicMock()
    stub.GetGroupMembers = AsyncMock(return_value=response)

    client = _make_client_with_stub(stub)
    result = await client.get_group_members(42)

    assert len(result) == 2
    assert result[0].telegram_id == 111
    assert result[1].telegram_id == 222


@pytest.mark.asyncio
async def test_cache_hit():
    """Second call returns cached result; stub called only once."""
    students = [_make_student(1, 111)]
    response = MagicMock()
    response.students = students

    stub = MagicMock()
    stub.GetGroupMembers = AsyncMock(return_value=response)

    client = _make_client_with_stub(stub)
    await client.get_group_members(42)
    await client.get_group_members(42)

    stub.GetGroupMembers.assert_called_once()


@pytest.mark.asyncio
async def test_cache_expired():
    """After TTL elapses, stub is called again (total 2 times)."""
    students = [_make_student(1, 111)]
    response = MagicMock()
    response.students = students

    stub = MagicMock()
    stub.GetGroupMembers = AsyncMock(return_value=response)

    client = _make_client_with_stub(stub)

    # First call at t=0
    with patch("bot.grpc_client.academic_client.time.monotonic", return_value=0.0):
        await client.get_group_members(42)

    # Second call at t=301 (past 300 s TTL)
    with patch("bot.grpc_client.academic_client.time.monotonic", return_value=301.0):
        await client.get_group_members(42)

    assert stub.GetGroupMembers.call_count == 2


@pytest.mark.asyncio
async def test_cache_invalidate():
    """After invalidate(), next call hits stub again (total 2 times)."""
    students = [_make_student(1, 111)]
    response = MagicMock()
    response.students = students

    stub = MagicMock()
    stub.GetGroupMembers = AsyncMock(return_value=response)

    client = _make_client_with_stub(stub)
    await client.get_group_members(42)
    client.invalidate(42)
    await client.get_group_members(42)

    assert stub.GetGroupMembers.call_count == 2


@pytest.mark.asyncio
async def test_grpc_error_propagates():
    """gRPC errors from stub bubble up to the caller unchanged."""
    stub = MagicMock()
    stub.GetGroupMembers = AsyncMock(
        side_effect=grpc.aio.AioRpcError(
            code=grpc.StatusCode.UNAVAILABLE,
            initial_metadata=MagicMock(),
            trailing_metadata=MagicMock(),
            details="service unavailable",
            debug_error_string="",
        )
    )

    client = _make_client_with_stub(stub)

    with pytest.raises(grpc.aio.AioRpcError):
        await client.get_group_members(42)
