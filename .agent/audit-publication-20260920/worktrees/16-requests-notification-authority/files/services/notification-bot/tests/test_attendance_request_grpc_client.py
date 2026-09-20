"""Tests for the private Attendance request gRPC client boundary."""

from unittest.mock import AsyncMock, MagicMock

import grpc
import pytest

from bot.grpc_client import attendance_pb2
from bot.grpc_client.attendance_client import AttendanceRequestGrpcClient

REQUEST_ID = "0123456789abcdef01234567"


def _client_with_stub(stub: MagicMock, *, timeout: float = 3.0) -> AttendanceRequestGrpcClient:
    client = AttendanceRequestGrpcClient.__new__(AttendanceRequestGrpcClient)
    client._channel = MagicMock()
    client._stub = stub
    client._metadata = (("x-grpc-secret", "unit-secret"),)
    client._resolve_timeout_seconds = timeout
    return client


@pytest.mark.asyncio
async def test_resolve_sends_kind_id_secret_and_finite_timeout():
    response = attendance_pb2.ResolveRequestNotificationResponse(group_id=5, student_id=10)
    stub = MagicMock()
    stub.ResolveRequestNotification = AsyncMock(return_value=response)
    client = _client_with_stub(stub, timeout=2.5)

    result = await client.resolve_request_notification(
        attendance_pb2.STUDENT_REQUEST_KIND_EXCUSE,
        REQUEST_ID,
    )

    assert result is response
    stub.ResolveRequestNotification.assert_awaited_once()
    request = stub.ResolveRequestNotification.await_args.args[0]
    assert request.kind == attendance_pb2.STUDENT_REQUEST_KIND_EXCUSE
    assert request.request_id == REQUEST_ID
    assert stub.ResolveRequestNotification.await_args.kwargs["metadata"] == (
        ("x-grpc-secret", "unit-secret"),
    )
    assert stub.ResolveRequestNotification.await_args.kwargs["timeout"] == 2.5


@pytest.mark.asyncio
async def test_resolve_rpc_errors_are_not_swallowed():
    error = grpc.aio.AioRpcError(
        code=grpc.StatusCode.UNAVAILABLE,
        initial_metadata=MagicMock(),
        trailing_metadata=MagicMock(),
        details="attendance unavailable",
        debug_error_string="",
    )
    stub = MagicMock()
    stub.ResolveRequestNotification = AsyncMock(side_effect=error)
    client = _client_with_stub(stub)

    with pytest.raises(grpc.aio.AioRpcError) as exc_info:
        await client.resolve_request_notification(
            attendance_pb2.STUDENT_REQUEST_KIND_LATE_CHECKIN,
            REQUEST_ID,
        )

    assert exc_info.value.code() == grpc.StatusCode.UNAVAILABLE


@pytest.mark.parametrize(
    ("kind", "request_id"),
    [
        (attendance_pb2.STUDENT_REQUEST_KIND_UNSPECIFIED, REQUEST_ID),
        (attendance_pb2.STUDENT_REQUEST_KIND_EXCUSE, ""),
    ],
)
@pytest.mark.asyncio
async def test_resolve_rejects_invalid_lookup_keys_before_rpc(kind, request_id):
    stub = MagicMock()
    stub.ResolveRequestNotification = AsyncMock()
    client = _client_with_stub(stub)

    with pytest.raises(ValueError):
        await client.resolve_request_notification(kind, request_id)

    stub.ResolveRequestNotification.assert_not_awaited()


@pytest.mark.asyncio
async def test_attachment_fetch_preserves_rpc_failure_metadata_and_finite_timeout():
    error = grpc.aio.AioRpcError(
        code=grpc.StatusCode.NOT_FOUND,
        initial_metadata=MagicMock(),
        trailing_metadata=MagicMock(),
        details="attachment missing",
        debug_error_string="",
    )
    stub = MagicMock()
    stub.FetchExcuseAttachment = AsyncMock(side_effect=error)
    client = _client_with_stub(stub, timeout=4.25)

    with pytest.raises(grpc.aio.AioRpcError) as exc_info:
        await client.fetch_excuse_attachment(20, REQUEST_ID, "abcdefabcdefabcdefabcdef")

    assert exc_info.value.code() == grpc.StatusCode.NOT_FOUND
    kwargs = stub.FetchExcuseAttachment.await_args.kwargs
    assert kwargs["metadata"] == (("x-grpc-secret", "unit-secret"),)
    assert kwargs["timeout"] == 4.25
