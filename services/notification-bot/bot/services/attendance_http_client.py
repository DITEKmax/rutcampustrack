import logging

import aiohttp

logger = logging.getLogger(__name__)


class TokenExpiredError(Exception):
    """Raised when JWT is expired (401 from API Gateway)."""
    pass


class AttendanceHttpClient:
    """HTTP client for Attendance Service REST API via API Gateway.

    Uses student's JWT for authentication (per D-09).
    """

    def __init__(self, base_url: str) -> None:
        self._base_url = base_url
        self._session: aiohttp.ClientSession | None = None

    async def start(self) -> None:
        self._session = aiohttp.ClientSession(base_url=self._base_url)

    async def close(self) -> None:
        if self._session:
            await self._session.close()

    async def get_student_records(self, access_token: str) -> list[dict]:
        """Fetch student attendance records.

        Returns list of record dicts with keys: lessonId, status, markedAt, etc.
        HATEOAS response is unwrapped from _embedded.attendanceRecordEntryList.

        Raises TokenExpiredError on 401.
        Raises aiohttp.ClientResponseError on other errors.
        """
        headers = {"Authorization": f"Bearer {access_token}"}
        async with self._session.get(
            "/api/attendance/reports/student/records",
            headers=headers,
        ) as resp:
            if resp.status == 401:
                raise TokenExpiredError("JWT expired or invalid")
            resp.raise_for_status()
            data = await resp.json()
            # HATEOAS CollectionModel wraps list in _embedded
            return data.get("_embedded", {}).get("attendanceRecordEntryList", [])
