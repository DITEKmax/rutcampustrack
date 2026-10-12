"""Actual aiogram/private-HTTP adapters against loopback synthetic providers.

Academic binding/audience is controlled here; Mongo, Rabbit and real Telegram
are separate boundaries. Provider observations contain no message bodies.
"""

import asyncio
import base64
from contextlib import asynccontextmanager
from types import SimpleNamespace

import pytest
from aiogram import Bot
from aiogram.client.session.aiohttp import AiohttpSession
from aiogram.client.telegram import TelegramAPIServer
from aiohttp import web

from bot.services.notification_prefs import (
    CATEGORIES,
    NotificationPreferencesUnavailable,
    NotificationPrefsClient,
)
from bot.services.send_queue import SendTask, TelegramSendQueue, group_audience


class ControlledAuthority:
    def __init__(self):
        self.bound_user = 11
        self.headman = True
        self.enabled = True
        self.available = True
        self.preference_requests = 0

    async def get_user_by_telegram_id(self, telegram_id):
        return SimpleNamespace(found=telegram_id == 111, user_id=self.bound_user)

    async def get_current_group_members(self, group_id):
        assert group_id == 7
        return [SimpleNamespace(user_id=11, telegram_id=111, is_headman=self.headman)]

    async def preferences(self, request):
        self.preference_requests += 1
        assert request.match_info["user"] == "11"
        assert request.match_info["chat"] == "111"
        assert request.query.get("type") == "EXCUSE_REQUESTED"
        if not self.available:
            return web.json_response({"error": "synthetic unavailable"}, status=503)
        return web.json_response(
            {
                "userId": 11,
                "telegramId": 111,
                "globalEnabled": True,
                "categories": {category: True for category in CATEGORIES},
                "canonicalCategories": {category: True for category in CATEGORIES},
                "mutedUntil": None,
                "canonicalMutedUntil": None,
                "eligible": self.enabled,
            }
        )


class ControlledProvider:
    def __init__(self):
        self.attempts = []
        self.successes = []
        self.first_failure = None
        self.after_first_failure = None

    async def send(self, request):
        body = await request.post()
        # Keep only destination and transport result, never the message text.
        chat = int(body["chat_id"])
        self.attempts.append(chat)
        if len(self.attempts) == 1 and self.first_failure is not None:
            if self.after_first_failure is not None:
                self.after_first_failure()
            status = self.first_failure
            result = {"ok": False, "error_code": status, "description": "synthetic provider unavailable"}
            if status == 429:
                result["parameters"] = {"retry_after": 0}
            return web.json_response(result, status=status)
        self.successes.append(chat)
        return web.json_response(
            {
                "ok": True,
                "result": {
                    "message_id": len(self.successes),
                    "date": 1,
                    "chat": {"id": chat, "type": "private"},
                    "text": "synthetic notification",
                },
            }
        )


@asynccontextmanager
async def controlled_delivery():
    authority = ControlledAuthority()
    provider = ControlledProvider()
    app = web.Application()
    app.router.add_get("/internal/bot/notification-preferences/{user}/{chat}", authority.preferences)
    app.router.add_post("/bot{token}/sendMessage", provider.send)
    runner = web.AppRunner(app, access_log=None)
    await runner.setup()
    site = web.TCPSite(runner, "127.0.0.1", 0)
    await site.start()
    port = runner.addresses[0][1]
    base_url = f"http://127.0.0.1:{port}"
    session = AiohttpSession(api=TelegramAPIServer.from_base(base_url))
    bot = Bot(token="123456789:" + "A" * 35, session=session)
    preferences = NotificationPrefsClient(base_url, base64.urlsafe_b64encode(bytes(32)).decode().rstrip("="), authority)
    queue = TelegramSendQueue(preferences)
    queue.start()
    try:
        yield authority, provider, bot, queue
    finally:
        await queue.shutdown()
        await preferences.close()
        await bot.session.close()
        await runner.cleanup()


def recipient(bot, authority):
    return SendTask(
        lambda: bot.send_message(chat_id=111, text="synthetic notification"),
        user_id=11,
        chat_id=111,
        category="tickets",
        notification_type="EXCUSE_REQUESTED",
        audience_check=group_audience(authority, 7, 11, 111, headman=True),
    )


@pytest.mark.asyncio
async def test_actual_aiogram_http_failure_retries_then_records_one_success():
    async with controlled_delivery() as (authority, provider, bot, queue):
        provider.first_failure = 500
        async with queue.staged() as batch:
            await queue.put(recipient(bot, authority))
        await asyncio.wait_for(batch.wait(), 5)
        assert provider.attempts == [111, 111]
        assert provider.successes == [111]
        assert authority.preference_requests >= 2


@pytest.mark.asyncio
@pytest.mark.parametrize("revoke", ["binding", "headman"])
async def test_actual_aiogram_429_rechecks_binding_and_headman_before_retry(revoke):
    async with controlled_delivery() as (authority, provider, bot, queue):
        provider.first_failure = 429

        def withdraw_authority():
            if revoke == "binding":
                authority.bound_user = 12
            else:
                authority.headman = False

        provider.after_first_failure = withdraw_authority
        async with queue.staged() as batch:
            await queue.put(recipient(bot, authority))
        await asyncio.wait_for(batch.wait(), 5)
        assert provider.attempts == [111]
        assert provider.successes == []


@pytest.mark.asyncio
async def test_private_preference_http_unknown_defers_then_disabled_suppresses_then_recovery_sends():
    async with controlled_delivery() as (authority, provider, bot, queue):
        authority.available = False
        with pytest.raises(NotificationPreferencesUnavailable):
            async with queue.staged():
                await queue.put(recipient(bot, authority))
        assert provider.attempts == []
        authority.available = True
        authority.enabled = False
        async with queue.staged() as disabled:
            await queue.put(recipient(bot, authority))
        await disabled.wait()
        assert provider.attempts == []
        authority.enabled = True
        async with queue.staged() as recovered:
            await queue.put(recipient(bot, authority))
        await asyncio.wait_for(recovered.wait(), 5)
        assert provider.attempts == [111]
        assert provider.successes == [111]
