import logging
from datetime import datetime, timezone
from pathlib import Path

import grpc.aio

from bot.grpc_client import schedule_pb2, schedule_pb2_grpc

logger = logging.getLogger(__name__)


class ScheduleGrpcClient:
    """Async gRPC client for Schedule Service.

    No caching — GetActiveLesson must return current state.
    """

    def __init__(
        self,
        host: str,
        port: int,
        grpc_secret: str = "",
        tls_enabled: bool = False,
        tls_ca_path: str = "",
    ) -> None:
        target = f"{host}:{port}"
        if tls_enabled:
            if not tls_ca_path:
                raise ValueError("SCHEDULE_GRPC_TLS_CA_PATH is required when TLS is enabled")
            ca_path = Path(tls_ca_path)
            if not ca_path.is_file():
                raise ValueError(f"SCHEDULE_GRPC_TLS_CA_PATH does not exist: {tls_ca_path}")
            credentials = grpc.ssl_channel_credentials(root_certificates=ca_path.read_bytes())
            self._channel = grpc.aio.secure_channel(target, credentials)
        else:
            self._channel = grpc.aio.insecure_channel(target)
        self._stub = schedule_pb2_grpc.ScheduleGrpcServiceStub(self._channel)
        # IMP-09: Shared secret for inter-service gRPC auth
        self._metadata = (("x-grpc-secret", grpc_secret),) if grpc_secret else ()

    async def get_active_lesson(self, group_id: int) -> object | None:
        """Get currently active lesson for a group.

        Returns LessonResponse proto or None if no active lesson (NOT_FOUND).
        """
        timestamp = datetime.now(timezone.utc).isoformat()
        request = schedule_pb2.ActiveLessonRequest(
            group_id=group_id,
            timestamp=timestamp,
        )
        try:
            return await self._stub.GetActiveLesson(request, metadata=self._metadata)
        except grpc.aio.AioRpcError as e:
            if e.code() == grpc.StatusCode.NOT_FOUND:
                return None
            raise

    async def close(self) -> None:
        await self._channel.close()
