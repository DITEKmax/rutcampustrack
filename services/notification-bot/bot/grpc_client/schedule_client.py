import logging
from datetime import datetime, timezone

import grpc.aio

from bot.grpc_client import schedule_pb2, schedule_pb2_grpc

logger = logging.getLogger(__name__)


class ScheduleGrpcClient:
    """Async gRPC client for Schedule Service.

    No caching — GetActiveLesson must return current state.
    """

    def __init__(self, host: str, port: int) -> None:
        target = f"{host}:{port}"
        self._channel = grpc.aio.insecure_channel(target)
        self._stub = schedule_pb2_grpc.ScheduleGrpcServiceStub(self._channel)

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
            return await self._stub.GetActiveLesson(request)
        except grpc.aio.AioRpcError as e:
            if e.code() == grpc.StatusCode.NOT_FOUND:
                return None
            raise

    async def close(self) -> None:
        await self._channel.close()
