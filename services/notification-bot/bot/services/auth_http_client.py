import logging

import aiohttp

logger = logging.getLogger(__name__)


class AuthHttpClient:
    """HTTP client for Auth Service OTP endpoints.

    Calls auth-service directly (not through API Gateway) per D-06.
    OTP endpoints are public — no JWT required.

    Timeout is set to 10 seconds to fail fast on service unavailability (per T-23-06).
    """

    _TIMEOUT = aiohttp.ClientTimeout(total=10)

    def __init__(self, base_url: str) -> None:
        self._base_url = base_url
        self._session: aiohttp.ClientSession | None = None

    async def start(self) -> None:
        """Create aiohttp session. Must be called inside async context."""
        self._session = aiohttp.ClientSession(
            base_url=self._base_url,
            timeout=self._TIMEOUT,
        )

    async def close(self) -> None:
        if self._session:
            await self._session.close()

    async def request_otp(self, telegram_id: int) -> str:
        """Request OTP code for telegram_id. Returns 6-digit code string.

        Raises aiohttp.ClientResponseError on 401 (user not found),
        429 (rate limited), or 5xx (service error).
        """
        async with self._session.post(
            "/auth/otp/request",
            json={"telegramId": telegram_id},
        ) as resp:
            resp.raise_for_status()
            data = await resp.json()
            return data["code"]
