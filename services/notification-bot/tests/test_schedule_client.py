"""Tests for ScheduleGrpcClient — GetActiveLesson with NOT_FOUND -> None handling."""

from unittest.mock import AsyncMock, MagicMock

import grpc
import grpc.aio
import pytest

from bot.grpc_client.schedule_client import ScheduleGrpcClient


def _make_lesson_response(lesson_id: int = 1, subject_id: int = 10) -> MagicMock:
    lesson = MagicMock()
    lesson.id = lesson_id
    lesson.subject_id = subject_id
    lesson.room = "101A"
    lesson.start_time = "09:00"
    lesson.end_time = "10:30"
    lesson.status = "active"
    return lesson


def _make_client_with_stub(stub: MagicMock) -> ScheduleGrpcClient:
    """Create ScheduleGrpcClient with injected stub (bypasses real gRPC channel)."""
    client = ScheduleGrpcClient.__new__(ScheduleGrpcClient)
    client._channel = MagicMock()
    client._stub = stub
    return client


@pytest.mark.asyncio
async def test_get_active_lesson_found():
    """Stub returns LessonResponse; client returns it as-is (not None)."""
    lesson = _make_lesson_response(lesson_id=42, subject_id=5)
    stub = MagicMock()
    stub.GetActiveLesson = AsyncMock(return_value=lesson)

    client = _make_client_with_stub(stub)
    result = await client.get_active_lesson(group_id=100)

    assert result is not None
    assert result.id == 42
    assert result.subject_id == 5
    assert result.room == "101A"


@pytest.mark.asyncio
async def test_get_active_lesson_not_found():
    """NOT_FOUND gRPC error is caught and returns None (no active lesson)."""
    stub = MagicMock()
    stub.GetActiveLesson = AsyncMock(
        side_effect=grpc.aio.AioRpcError(
            code=grpc.StatusCode.NOT_FOUND,
            initial_metadata=MagicMock(),
            trailing_metadata=MagicMock(),
            details="no active lesson",
            debug_error_string="",
        )
    )

    client = _make_client_with_stub(stub)
    result = await client.get_active_lesson(group_id=100)

    assert result is None


@pytest.mark.asyncio
async def test_get_active_lesson_other_error():
    """Non-NOT_FOUND gRPC errors propagate to the caller."""
    stub = MagicMock()
    stub.GetActiveLesson = AsyncMock(
        side_effect=grpc.aio.AioRpcError(
            code=grpc.StatusCode.UNAVAILABLE,
            initial_metadata=MagicMock(),
            trailing_metadata=MagicMock(),
            details="service unavailable",
            debug_error_string="",
        )
    )

    client = _make_client_with_stub(stub)

    with pytest.raises(grpc.aio.AioRpcError) as exc_info:
        await client.get_active_lesson(group_id=100)

    assert exc_info.value.code() == grpc.StatusCode.UNAVAILABLE


@pytest.mark.asyncio
async def test_get_active_lesson_passes_group_id():
    """GetActiveLesson is called with the provided group_id."""
    lesson = _make_lesson_response()
    stub = MagicMock()
    stub.GetActiveLesson = AsyncMock(return_value=lesson)

    client = _make_client_with_stub(stub)
    await client.get_active_lesson(group_id=77)

    stub.GetActiveLesson.assert_called_once()
    call_args = stub.GetActiveLesson.call_args
    request = call_args[0][0]
    assert request.group_id == 77
