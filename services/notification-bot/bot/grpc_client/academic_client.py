import logging
import time
from typing import Any

import grpc.aio

from bot.grpc_client import academic_pb2, academic_pb2_grpc

logger = logging.getLogger(__name__)


class AcademicGrpcClient:
    _CACHE_TTL_SECONDS = 300  # 5 minutes

    def __init__(self, host: str, port: int) -> None:
        target = f"{host}:{port}"
        self._channel = grpc.aio.insecure_channel(target)
        self._stub = academic_pb2_grpc.AcademicGrpcServiceStub(self._channel)
        self._cache: dict[int, tuple[float, list[Any]]] = {}

    async def get_group_members(self, group_id: int) -> list[Any]:
        now = time.monotonic()
        if group_id in self._cache:
            ts, members = self._cache[group_id]
            if now - ts < self._CACHE_TTL_SECONDS:
                return members
        request = academic_pb2.GroupMembersRequest(group_id=group_id)
        response = await self._stub.GetGroupMembers(request)
        members = list(response.students)
        self._cache[group_id] = (now, members)
        return members

    def invalidate(self, group_id: int) -> None:
        self._cache.pop(group_id, None)

    async def close(self) -> None:
        await self._channel.close()
