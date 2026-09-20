"""Authenticated gRPC client for retained Attendance request attachments."""

import grpc.aio

from bot.grpc_client import attendance_pb2, attendance_pb2_grpc


class AttendanceRequestGrpcClient:
    MAX_RECEIVE_BYTES = 24 * 1024 * 1024

    def __init__(self, host: str, port: int, grpc_secret: str = "") -> None:
        self._channel = grpc.aio.insecure_channel(
            f"{host}:{port}",
            options=[("grpc.max_receive_message_length", self.MAX_RECEIVE_BYTES)],
        )
        self._stub = attendance_pb2_grpc.AttendanceRequestBotGrpcServiceStub(self._channel)
        self._metadata = (("x-grpc-secret", grpc_secret),) if grpc_secret else ()

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
        )

    async def close(self) -> None:
        await self._channel.close()
