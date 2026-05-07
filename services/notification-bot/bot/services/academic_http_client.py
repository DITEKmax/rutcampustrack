import logging
from typing import Any

import aiohttp

logger = logging.getLogger(__name__)


class TokenExpiredError(Exception):
    """Raised when JWT is expired or invalid."""


class AcademicHttpClient:
    """HTTP client for Academic Service REST API via API Gateway."""

    _TIMEOUT = aiohttp.ClientTimeout(total=10)

    def __init__(self, base_url: str) -> None:
        self._base_url = base_url
        self._session: aiohttp.ClientSession | None = None

    async def start(self) -> None:
        self._session = aiohttp.ClientSession(base_url=self._base_url, timeout=self._TIMEOUT)

    async def close(self) -> None:
        if self._session:
            await self._session.close()

    async def get_active_semester(self, access_token: str) -> dict[str, Any] | None:
        """Return active semester from /academic/semesters or None."""
        data = await self._get_json(
            "/api/academic/semesters",
            access_token,
            params={"size": 50},
        )
        semesters = _embedded_list(data, "semesterResponseList")
        return next((semester for semester in semesters if semester.get("active") is True), None)

    async def get_homeworks(self, access_token: str, group_id: int, semester_id: int) -> list[dict[str, Any]]:
        """Return homework list for a group and semester."""
        data = await self._get_json(
            "/api/academic/homeworks",
            access_token,
            params={
                "groupId": group_id,
                "semesterId": semester_id,
                "size": 200,
            },
        )
        return _embedded_list(data, "homeworkResponseList")

    async def _get_json(self, path: str, access_token: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
        if self._session is None:
            raise RuntimeError("AcademicHttpClient.start() must be called before use")
        headers = {"Authorization": f"Bearer {access_token}"}
        async with self._session.get(path, headers=headers, params=params) as resp:
            if resp.status == 401:
                raise TokenExpiredError("JWT expired or invalid")
            resp.raise_for_status()
            data = await resp.json()
            return data if isinstance(data, dict) else {}


def _embedded_list(data: dict[str, Any], rel: str) -> list[dict[str, Any]]:
    embedded = data.get("_embedded")
    if not isinstance(embedded, dict):
        return []
    items = embedded.get(rel, [])
    return [item for item in items if isinstance(item, dict)]
