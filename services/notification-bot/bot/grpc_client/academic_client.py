import logging
import time
from typing import Any

import grpc.aio

from bot.grpc_client import academic_pb2, academic_pb2_grpc

logger = logging.getLogger(__name__)


class AcademicGrpcClient:
    _CACHE_TTL_SECONDS = 300  # 5 minutes

    def __init__(self, host: str, port: int, grpc_secret: str = "") -> None:
        target = f"{host}:{port}"
        self._channel = grpc.aio.insecure_channel(target)
        self._stub = academic_pb2_grpc.AcademicGrpcServiceStub(self._channel)
        # IMP-09: Shared secret for inter-service gRPC auth
        self._metadata = (("x-grpc-secret", grpc_secret),) if grpc_secret else ()
        self._cache: dict[int, tuple[float, list[Any]]] = {}

    async def get_group(self, group_id: int) -> Any:
        """Resolve group info by ID. Returns GroupResponse proto (.id, .name, .is_active).

        Used by group.renamed / group.archived notification handlers (58-07).
        No local caching: the handler fires rarely and needs fresh name after rename.
        """
        request = academic_pb2.GroupRequest(group_id=group_id)
        return await self._stub.GetGroup(request, metadata=self._metadata)

    async def get_group_members(self, group_id: int) -> list[Any]:
        now = time.monotonic()
        if group_id in self._cache:
            ts, members = self._cache[group_id]
            if now - ts < self._CACHE_TTL_SECONDS:
                return members
        request = academic_pb2.GroupMembersRequest(group_id=group_id)
        response = await self._stub.GetGroupMembers(request, metadata=self._metadata)
        members = list(response.students)
        self._cache[group_id] = (now, members)
        return members

    def invalidate(self, group_id: int) -> None:
        self._cache.pop(group_id, None)

    async def get_user_by_telegram_id(self, telegram_id: int):
        """Look up user by Telegram ID. Returns UserByTelegramIdResponse proto.

        Response has .found bool — if False, user doesn't exist.
        Does NOT raise on not-found (uses found flag instead of gRPC error).
        """
        request = academic_pb2.UserByTelegramIdRequest(telegram_id=telegram_id)
        return await self._stub.GetUserByTelegramId(request, metadata=self._metadata)

    async def get_subjects_by_ids(self, subject_ids: list[int]):
        """Resolve subject IDs to names. Returns SubjectsByIdsResponse proto."""
        request = academic_pb2.SubjectsByIdsRequest(subject_ids=subject_ids)
        return await self._stub.GetSubjectsByIds(request, metadata=self._metadata)

    async def close(self) -> None:
        await self._channel.close()
