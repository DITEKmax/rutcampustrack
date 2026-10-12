"""Native aiogram/Dispatcher → loopback provider behavior; authority stub is not PG acceptance."""

from __future__ import annotations

import asyncio
import uuid
from contextlib import asynccontextmanager
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from aiogram import Bot, Dispatcher, Router
from aiogram.client.session.aiohttp import AiohttpSession
from aiogram.client.telegram import TelegramAPIServer
from aiogram.filters import Command
from aiogram.types import Update
from aiohttp import web

from bot.grpc_client import academic_pb2 as pb
from bot.grpc_client.bot_credentials_client import BotCredentialRequestDenied, BotCredentialsGrpcClient
from bot.handlers.start import cmd_start
from bot.services.bot_credential_cards import BotCredentialCards
from bot.services.notification_prefs import NotificationPreferencesUnavailable


def _copy(reference):
    clone = pb.BotCredentialMessageReference()
    clone.CopyFrom(reference)
    return clone


class AuthorityStub:
    """Only selected provider outcomes; actual generation/CAS correctness lives in Java PG IT."""

    def __init__(self):
        self.initial = uuid.uuid4().hex + "<&"
        self.login = "own<&"
        self.reference = None
        self.registered = False
        self.is_invalidated = False
        self.render_outcome = None
        self.cleanup_outcome = None
        self.deny_register = False

    async def read(self, sender, user_id, **versions):
        if self.is_invalidated:
            raise BotCredentialRequestDenied("synthetic generation changed")
        return pb.BotOwnInitialCredentialsResponse(
            found=True, eligible=True, user_id=17, login=self.login, initial_password=self.initial,
            telegram_binding_version=2, credentials_version=1,
        )

    async def register(self, **identity):
        if self.deny_register:
            raise BotCredentialRequestDenied("synthetic register fence")
        self.registered = True
        self.reference = pb.BotCredentialMessageReference(
            record_id=1, user_id=identity["user_id"], private_chat_id=identity["chat_id"],
            message_id=identity["message_id"], telegram_binding_version=identity["binding_version"],
            credentials_version=identity["credentials_version"], record_revision=1, render_revision=1,
            render_state=pb.BOT_CREDENTIAL_RENDER_STATE_IN_FLIGHT,
            cleanup_state=pb.BOT_CREDENTIAL_CLEANUP_STATE_NOT_REQUIRED,
        )
        return self._response(pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_REGISTERED)

    def invalidate(self):
        self.is_invalidated = True
        self.reference.invalidated_credentials_version = 2
        self.reference.cleanup_state = pb.BOT_CREDENTIAL_CLEANUP_STATE_PENDING
        self.reference.record_revision += 1

    async def complete_render(self, reference, outcome):
        self.render_outcome = outcome
        if self.reference.cleanup_state == pb.BOT_CREDENTIAL_CLEANUP_STATE_CLEANED:
            return self._response(pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_CLEANUP_COMPLETED)
        self.reference.render_state = {
            pb.BOT_CREDENTIAL_RENDER_OUTCOME_NO_EDIT: pb.BOT_CREDENTIAL_RENDER_STATE_NO_EDIT,
            pb.BOT_CREDENTIAL_RENDER_OUTCOME_CONFIRMED_EDIT: pb.BOT_CREDENTIAL_RENDER_STATE_CONFIRMED_EDIT,
            pb.BOT_CREDENTIAL_RENDER_OUTCOME_UNCERTAIN: pb.BOT_CREDENTIAL_RENDER_STATE_UNCERTAIN,
        }[outcome]
        self.reference.record_revision += 1
        return self._response(
            pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_CLEANUP_REQUIRED if self.is_invalidated
            else pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_RENDER_COMPLETED
        )

    async def invalidated(self, **kwargs):
        references = []
        if self.reference is not None and self.reference.cleanup_state == pb.BOT_CREDENTIAL_CLEANUP_STATE_PENDING:
            references.append(_copy(self.reference))
        return pb.BotCredentialMessagesResponse(
            disposition=pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_INVALIDATED_LIST, messages=references
        )

    async def complete_cleanup(self, reference, *, outcome, event_id):
        self.cleanup_outcome = outcome
        self.reference.cleanup_state = pb.BOT_CREDENTIAL_CLEANUP_STATE_CLEANED
        self.reference.record_revision += 1
        return self._response(pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_CLEANUP_COMPLETED)

    def _response(self, disposition):
        return pb.BotCredentialMessagesResponse(disposition=disposition, messages=[_copy(self.reference)])


class Provider:
    def __init__(self, authority):
        self.authority = authority
        self.cards = {}
        self.next_message = 1
        self.edits = 0
        self.registered_before_edit = True
        self.delete_rejected = False
        self.drop_edit_reply = False
        self.edit_entered = asyncio.Event()
        self.edit_gate = None

    async def handle(self, request):
        method = request.match_info["method"]
        fields = await request.post()
        chat = int(fields["chat_id"])
        if method == "sendMessage":
            message = self.next_message
            self.next_message += 1
            self.cards[(chat, message)] = fields["text"]
            return self.result(chat, message)
        key = (chat, int(fields["message_id"]))
        if method == "deleteMessage":
            if self.delete_rejected:
                return self.error("message can't be deleted")
            if key not in self.cards:
                return self.error("message to delete not found")
            del self.cards[key]
            return web.json_response({"ok": True, "result": True})
        if method == "editMessageText":
            self.edits += 1
            self.registered_before_edit &= self.authority.registered
            self.edit_entered.set()
            if self.edit_gate is not None:
                await self.edit_gate.wait()
            # Telegram edit cannot recreate a successfully deleted message ID.
            if key not in self.cards:
                return self.error("message to edit not found")
            self.cards[key] = fields["text"]
            if self.drop_edit_reply:
                self.drop_edit_reply = False
                request.transport.close()
            return self.result(*key)
        return self.error("unsupported test provider method")

    def result(self, chat, message):
        return web.json_response({"ok": True, "result": {
            "message_id": message, "date": 1700000000, "chat": {"id": chat, "type": "private"},
            "text": self.cards[(chat, message)],
        }})

    @staticmethod
    def error(description):
        return web.json_response(
            {"ok": False, "error_code": 400, "description": "Bad Request: " + description}, status=400,
        )

    def has_secret(self):
        import html
        marker = html.escape(self.authority.initial)
        return any(marker in text for text in self.cards.values())

    def has_login(self):
        import html
        marker = html.escape(self.authority.login)
        return any(marker in text for text in self.cards.values())


@asynccontextmanager
async def provider_context(authority):
    provider = Provider(authority)
    app = web.Application()
    app.router.add_route("*", "/bot{token}/{method}", provider.handle)
    runner = web.AppRunner(app, access_log=None)
    await runner.setup()
    site = web.TCPSite(runner, "127.0.0.1", 0)
    await site.start()
    port = site._server.sockets[0].getsockname()[1]
    bot = Bot(token="123456:" + uuid.uuid4().hex,
              session=AiohttpSession(api=TelegramAPIServer.from_base(f"http://127.0.0.1:{port}")))
    try:
        yield provider, bot
    finally:
        if provider.edit_gate is not None:
            provider.edit_gate.set()
        await bot.session.close()
        await runner.cleanup()


def message(bot):
    async def answer(text, **kwargs):
        return await bot.send_message(chat_id=42, text=text, **kwargs)
    return SimpleNamespace(from_user=SimpleNamespace(id=42), chat=SimpleNamespace(id=42, type="private"), answer=answer)


def profile():
    return SimpleNamespace(
        found=True, user_id=17, display_name="Own", login="ordinary", group_name="Group", password_changed=False,
    )


@pytest.mark.asyncio
async def test_native_dispatcher_first_start_renders_one_registered_html_escaped_card():
    authority = AuthorityStub()
    async with provider_context(authority) as (provider, bot):
        cards = BotCredentialCards(bot=bot, client=authority)
        academic = SimpleNamespace(get_user_by_telegram_id=AsyncMock(return_value=profile()))
        dispatcher = Dispatcher()
        router = Router()
        router.message.register(cmd_start, Command("start"))
        dispatcher.include_router(router)
        update = Update.model_validate({"update_id": 1, "message": {
            "message_id": 901, "date": 1700000000, "chat": {"id": 42, "type": "private"},
            "from": {"id": 42, "is_bot": False, "first_name": "Own"}, "text": "/start",
        }}, context={"bot": bot})
        await dispatcher.feed_update(bot, update, academic_client=academic,
                                     prefs_client=SimpleNamespace(), credential_cards=cards)
        assert provider.has_secret() and provider.has_login()
        assert provider.registered_before_edit
        assert provider.edits == 1
        assert len(provider.cards) == 1
        assert authority.render_outcome == pb.BOT_CREDENTIAL_RENDER_OUTCOME_CONFIRMED_EDIT


@pytest.mark.asyncio
async def test_register_fence_keeps_nonsecret_placeholder_and_never_edits():
    authority = AuthorityStub()
    authority.deny_register = True
    async with provider_context(authority) as (provider, bot):
        assert not await BotCredentialCards(bot=bot, client=authority).deliver(message(bot), profile())
        assert not provider.has_secret()
        assert provider.edits == 0


@pytest.mark.asyncio
async def test_missing_dedicated_capability_keeps_ordinary_profile_usable_without_credential_rpc():
    authority = BotCredentialsGrpcClient("academic.test", 19491)
    authority.read = AsyncMock(wraps=authority.read)
    bot = SimpleNamespace(edit_message_text=AsyncMock())
    incoming = SimpleNamespace(
        from_user=SimpleNamespace(id=42), chat=SimpleNamespace(id=42, type="private"), answer=AsyncMock(),
    )
    public = profile()
    public.password_changed = True
    generic = SimpleNamespace(get_user_by_telegram_id=AsyncMock(return_value=public))
    await cmd_start(
        incoming, generic, SimpleNamespace(), credential_cards=BotCredentialCards(bot=bot, client=authority),
    )
    authority.read.assert_not_awaited()
    bot.edit_message_text.assert_not_awaited()
    assert "ordinary" in incoming.answer.await_args.args[0]


@pytest.mark.asyncio
async def test_lost_edit_reply_keeps_uncertain_metadata_until_exact_delete_recovers():
    authority = AuthorityStub()
    async with provider_context(authority) as (provider, bot):
        cards = BotCredentialCards(bot=bot, client=authority)
        provider.drop_edit_reply = True
        with pytest.raises(NotificationPreferencesUnavailable):
            await cards.deliver(message(bot), profile())
        assert provider.has_secret()
        assert authority.render_outcome == pb.BOT_CREDENTIAL_RENDER_OUTCOME_UNCERTAIN
        authority.invalidate()
        provider.delete_rejected = True
        restarted = BotCredentialCards(bot=bot, client=authority)
        with pytest.raises(NotificationPreferencesUnavailable):
            await restarted.cleanup(user_id=17, credentials_version=2, event_id=str(uuid.uuid4()))
        assert authority.reference.cleanup_state == pb.BOT_CREDENTIAL_CLEANUP_STATE_PENDING
        assert authority.cleanup_outcome is None
        assert provider.edits == 1  # No unsafe sanitation while original edit is uncertain.
        provider.delete_rejected = False
        await restarted.cleanup(user_id=17, credentials_version=2, event_id=str(uuid.uuid4()))
        assert not provider.has_secret()
        assert not provider.cards
        assert authority.reference.cleanup_state == pb.BOT_CREDENTIAL_CLEANUP_STATE_CLEANED


@pytest.mark.asyncio
async def test_settled_old_card_can_be_sanitized_after_restart_when_delete_rejected():
    authority = AuthorityStub()
    async with provider_context(authority) as (provider, bot):
        assert await BotCredentialCards(bot=bot, client=authority).deliver(message(bot), profile())
        authority.invalidate()
        provider.delete_rejected = True
        await BotCredentialCards(bot=bot, client=authority).cleanup(
            user_id=17, credentials_version=2, event_id=str(uuid.uuid4()))
        assert not provider.has_secret() and not provider.has_login()
        assert len(provider.cards) == 1
        assert authority.cleanup_outcome == pb.BOT_CREDENTIAL_CLEANUP_OUTCOME_SANITIZED


@pytest.mark.asyncio
async def test_confirmed_exact_delete_during_inflight_edit_prevents_late_recreation():
    authority = AuthorityStub()
    async with provider_context(authority) as (provider, bot):
        provider.edit_gate = asyncio.Event()
        renderer = BotCredentialCards(bot=bot, client=authority)
        pending = asyncio.create_task(renderer.deliver(message(bot), profile()))
        try:
            await asyncio.wait_for(provider.edit_entered.wait(), timeout=3)
            authority.invalidate()
            # Second process has an independent local lock, but shares durable original metadata.
            await BotCredentialCards(bot=bot, client=authority).cleanup(
                user_id=17, credentials_version=2, event_id=str(uuid.uuid4()))
            provider.edit_gate.set()
            with pytest.raises(NotificationPreferencesUnavailable):
                await pending
            assert not provider.cards
            assert not provider.has_secret()
            assert authority.reference.cleanup_state == pb.BOT_CREDENTIAL_CLEANUP_STATE_CLEANED
        finally:
            provider.edit_gate.set()
            if not pending.done():
                pending.cancel()
            await asyncio.gather(pending, return_exceptions=True)
