"""Authenticated gRPC client for retained Attendance request attachments."""

import math

import grpc.aio

from bot.grpc_client import attendance_pb2, attendance_pb2_grpc


class AttendanceRequestGrpcClient:
    MAX_RECEIVE_BYTES = 24 * 1024 * 1024

    def __init__(
        self,
        host: str,
        port: int,
        grpc_secret: str = "",
        resolve_timeout_seconds: float = 3.0,
    ) -> None:
        if not math.isfinite(resolve_timeout_seconds) or resolve_timeout_seconds <= 0:
            raise ValueError("resolve timeout must be finite and positive")
        self._channel = grpc.aio.insecure_channel(
            f"{host}:{port}",
            options=[("grpc.max_receive_message_length", self.MAX_RECEIVE_BYTES)],
        )
        self._stub = attendance_pb2_grpc.AttendanceRequestBotGrpcServiceStub(self._channel)
        self._metadata = (("x-grpc-secret", grpc_secret),) if grpc_secret else ()
        self._resolve_timeout_seconds = resolve_timeout_seconds

    async def fetch_excuse_attachment(self, actor_user_id: int, request_id: str, attachment_id: str):
        if actor_user_id <= 0 or not request_id or not attachment_id:
            raise ValueError("positive actor and non-empty attachment identifiers are required")
        return await self._stub.FetchExcuseAttachment(
            attendance_pb2.FetchExcuseAttachmentRequest(
                actor_user_id=actor_user_id,
                request_id=request_id,
                attachment_id=attachment_id,
            ),
            metadata=self._metadata,
            timeout=self._resolve_timeout_seconds,
        )

    async def resolve_request_notification(self, kind: int, request_id: str):
        """Resolve canonical private notification context by request kind/id."""
        if kind not in (
            attendance_pb2.STUDENT_REQUEST_KIND_EXCUSE,
            attendance_pb2.STUDENT_REQUEST_KIND_LATE_CHECKIN,
        ):
            raise ValueError("unsupported student request kind")
        if not request_id:
            raise ValueError("request id is required")
        return await self._stub.ResolveRequestNotification(
            attendance_pb2.ResolveRequestNotificationRequest(
                kind=kind,
                request_id=request_id,
            ),
            metadata=self._metadata,
            timeout=self._resolve_timeout_seconds,
        )

    async def close(self) -> None:
        await self._channel.close()
