"""Isolated native bridge runner. Stdout contains only safe outcomes; no secret artifacts."""

from __future__ import annotations

import asyncio
import html
import json
import logging
import os
import sys
import uuid
from pathlib import Path
from types import SimpleNamespace

import aiohttp
import grpc
from aiogram import Bot, Dispatcher
from aiogram.client.session.aiohttp import AiohttpSession
from aiogram.client.telegram import TelegramAPIServer
from aiogram.types import Update
from aiohttp import web

from bot.consumers.event_dispatcher import EventDispatcher
from bot.grpc_client import academic_pb2 as pb
from bot.grpc_client import academic_pb2_grpc
from bot.grpc_client.academic_client import AcademicGrpcClient
from bot.grpc_client.bot_credentials_client import BotCredentialRequestDenied, BotCredentialsGrpcClient
from bot.grpc_client.bot_menu_client import BotMenuGrpcClient
from bot.handlers.start import start_router
from bot.services.bot_credential_cards import BotCredentialCards
from bot.services.notification_prefs import NotificationPreferencesUnavailable
from bot.services.send_queue import TelegramSendQueue

logging.disable(logging.CRITICAL)
_phase = "setup"
_checks = 0
_headman_read_outcome = "UNKNOWN"
_auth_boundary = {"actual_auth_tls": False, "old_academic_denied": False, "old_credential_denied": False}


class BridgeFailure(RuntimeError):
    pass


def check(condition):
    global _checks
    if not condition:
        raise BridgeFailure("required bridge outcome failed")
    _checks += 1


class CountingMenu:
    def __init__(self, actual):
        self.actual = actual
        self.calls = 0

    async def own_profile(self, telegram):
        self.calls += 1
        return await self.actual.own_profile(telegram)

    async def menu_scope(self, telegram):
        self.calls += 1
        return await self.actual.menu_scope(telegram)

    def __getattr__(self, name):
        return getattr(self.actual, name)


class CountingCredentials:
    def __init__(self, actual):
        self.actual = actual
        self.calls = 0
        self.before_register = None

    async def read(self, *args, **kwargs):
        self.calls += 1
        return await self.actual.read(*args, **kwargs)

    async def register(self, **identity):
        callback = self.before_register
        self.before_register = None
        if callback is not None:
            await callback()
        return await self.actual.register(**identity)

    def __getattr__(self, name):
        return getattr(self.actual, name)


class Provider:
    def __init__(self, fixtures, control):
        self.fixtures = fixtures
        self.control = control
        self.cards = {}
        self.next_id = 1
        self.edit_count = 0
        self.notice_count = 0
        self.registration_verified = True
        self.delete_rejected = False
        self.drop_reply = False
        self.gate = None
        self.entered = asyncio.Event()
        self.messages = []
        self.rich_attempts = 0

    def contains_initial(self, fixture):
        marker = html.escape(fixture["initial"])
        return any(marker in text for text in self.cards.values())

    def contains_text(self, marker):
        return any(marker in text for text in self.cards.values())

    async def handle(self, request):
        fields = await request.post()
        method = request.match_info["method"]
        if method == "answerCallbackQuery":
            return web.json_response({"ok": True, "result": True})
        chat = int(fields["chat_id"])
        if method == "sendRichMessage":
            self.rich_attempts += 1
            return self.error("isolated provider uses the supported text fallback")
        if method == "sendMessage":
            message = self.next_id
            self.next_id += 1
            text = fields["text"]
            if "Пароль RutCampusTrack изменён" in text:
                self.notice_count += 1
            self.cards[(chat, message)] = text
            self.messages.append((chat, message, text, json.loads(fields.get("reply_markup", "null"))))
            return self.result(chat, message)
        message = int(fields["message_id"])
        key = (chat, message)
        if method == "editMessageReplyMarkup":
            return self.result(chat, message)
        if method == "deleteMessage":
            if self.delete_rejected:
                return self.error("message can't be deleted")
            if key not in self.cards:
                return self.error("message to delete not found")
            del self.cards[key]
            return web.json_response({"ok": True, "result": True})
        if method != "editMessageText":
            return self.error("unsupported isolated provider operation")
        self.edit_count += 1
        text = fields["text"]
        if "Пароль: <code>" in text:
            receipt = await self.control(f"verify?chat={chat}&message={message}")
            self.registration_verified &= receipt.get("registered") is True
        self.entered.set()
        if self.gate is not None:
            await self.gate.wait()
        if key not in self.cards:
            return self.error("message to edit not found")
        self.cards[key] = text
        if self.drop_reply:
            self.drop_reply = False
            request.transport.close()
        return self.result(chat, message)

    def result(self, chat, message):
        return web.json_response(
            {
                "ok": True,
                "result": {
                    "message_id": message,
                    "date": 1700000000,
                    "chat": {"id": chat, "type": "private"},
                    "text": self.cards[(chat, message)],
                },
            }
        )

    @staticmethod
    def error(description):
        return web.json_response(
            {"ok": False, "error_code": 400, "description": "Bad Request: " + description}, status=400
        )


async def main():
    global _phase
    fixtures = json.loads(os.environ.pop("NP01_FIXTURES"))
    authority = os.environ["NP01_AUTHORITY"]
    host, port = authority.rsplit(":", 1)
    ca_path = os.environ["NP01_CA"]
    service_token = os.environ.pop("NP01_SERVICE_TOKEN")
    auth_authority = os.environ["NP01_AUTH_SERVICE_AUTHORITY"]
    auth_host, auth_port = auth_authority.rsplit(":", 1)
    auth_ca_path = os.environ["NP01_AUTH_SERVICE_CA"]
    auth_service_token = os.environ.pop("NP01_AUTH_SERVICE_TOKEN")
    shared = os.environ.pop("NP01_SHARED_SECRET")
    control_token = os.environ.pop("NP01_CONTROL_TOKEN")
    control_url = os.environ["NP01_CONTROL_URL"]
    generic_actual = AcademicGrpcClient(host, int(port), shared, True, ca_path)
    protected_actual = BotCredentialsGrpcClient(
        host,
        int(port),
        shared,
        True,
        ca_path,
        service_token,
        auth_host=auth_host,
        auth_port=int(auth_port),
        auth_tls_enabled=True,
        auth_tls_ca_path=auth_ca_path,
        auth_service_token=auth_service_token,
    )
    generic = generic_actual
    menu_actual = BotMenuGrpcClient(
        auth_host, int(auth_port), tls_enabled=True, tls_ca_path=auth_ca_path, service_token=auth_service_token
    )
    menu = CountingMenu(menu_actual)
    protected = CountingCredentials(protected_actual)
    provider_runner = None
    bot = None
    pending = None
    async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=5)) as controls:

        async def control(action):
            async with controls.post(
                control_url + "/control/" + action, headers={"x-np01-control": control_token}
            ) as response:
                if response.status != 200:
                    raise BridgeFailure("isolated fixture control failed")
                return await response.json()

        provider = Provider(fixtures, control)
        app = web.Application()
        app.router.add_route("*", "/bot{token}/{method}", provider.handle)
        provider_runner = web.AppRunner(app, access_log=None)
        await provider_runner.setup()
        site = web.TCPSite(provider_runner, "127.0.0.1", 0)
        await site.start()
        provider_port = site._server.sockets[0].getsockname()[1]
        bot = Bot(
            token="123456:" + uuid.uuid4().hex,
            session=AiohttpSession(api=TelegramAPIServer.from_base(f"http://127.0.0.1:{provider_port}")),
        )
        cards = BotCredentialCards(bot=bot, client=protected)
        dispatcher = Dispatcher()
        dispatcher.include_router(start_router)
        update_id = 0

        async def feed(chat, command="/start", *, chat_type="private", sender=None):
            nonlocal update_id
            update_id += 1
            update = Update.model_validate(
                {
                    "update_id": update_id,
                    "message": {
                        "message_id": update_id + 900,
                        "date": 1700000000,
                        "chat": {"id": chat, "type": chat_type},
                        "from": {"id": chat if sender is None else sender, "is_bot": False, "first_name": "Own"},
                        "text": command,
                    },
                },
                context={"bot": bot},
            )
            await dispatcher.feed_update(
                bot,
                update,
                academic_client=generic,
                prefs_client=SimpleNamespace(),
                credential_cards=cards,
                bot_menu_client=menu,
            )

        queue = TelegramSendQueue(prefs_client=None)
        events = EventDispatcher(
            bot=bot,
            academic_client=generic,
            send_queue=queue,
            redis_client=SimpleNamespace(_redis=None),
            config=SimpleNamespace(admin_telegram_ids="0"),
            otp_tracker=SimpleNamespace(),
            credential_cards=cards,
        )
        own = fixtures["student"]

        async def dispatch_change(facts, *, cleaner=None):
            if cleaner is not None:
                events._credential_cards = cleaner
            event = {
                "event_type": "password.changed",
                "event_version": 2,
                "event_id": str(uuid.uuid4()),
                "payload": {
                    "user_id": own["uid"],
                    "telegram_id": own["chat"],
                    "credentials_version": facts["credentials_version"],
                    "telegram_binding_version": facts["telegram_binding_version"],
                },
            }
            batch = await events.dispatch(event)
            if batch is not None:
                await batch.wait()

        try:
            _phase = "real_tls_directed_auth_boundaries"
            from pathlib import Path

            request = pb.BotOwnInitialCredentialsRequest(
                telegram_sender_id=own["chat"],
                private_chat_id=own["chat"],
                expected_user_id=own["uid"],
                mode=pb.BOT_CREDENTIAL_READ_MODE_SNAPSHOT,
            )

            async def denied(endpoint, ca, metadata, expected):
                channel = grpc.aio.secure_channel(
                    endpoint, grpc.ssl_channel_credentials(root_certificates=Path(ca).read_bytes())
                )
                try:
                    stub = academic_pb2_grpc.AcademicGrpcServiceStub(channel)
                    try:
                        await stub.GetOwnBotInitialCredentials(request, metadata=metadata, timeout=3)
                    except grpc.aio.AioRpcError as error:
                        check(error.code() == expected)
                    else:
                        raise BridgeFailure("retired or unauthenticated credential read was admitted")
                finally:
                    await channel.close()

            await denied(
                authority,
                ca_path,
                (("x-grpc-secret", shared), ("x-service-token", service_token)),
                grpc.StatusCode.UNIMPLEMENTED,
            )
            _auth_boundary["old_academic_denied"] = True
            await denied(
                auth_authority, auth_ca_path, (("x-service-token", service_token),), grpc.StatusCode.UNAUTHENTICATED
            )
            _auth_boundary["old_credential_denied"] = True
            await denied(auth_authority, auth_ca_path, (), grpc.StatusCode.UNAUTHENTICATED)
            snapshot = await protected.read(own["chat"], own["uid"])
            check(snapshot.user_id == own["uid"] and snapshot.initial_password == own["initial"])
            _auth_boundary["actual_auth_tls"] = True

            _phase = "own_roles_native_dispatcher"
            for role in ("student", "teacher", "headman", "FULL", "LIMITED"):
                fixture = fixtures[role]
                public = await menu.own_profile(fixture["chat"])
                check(public.found and public.user_id == fixture["uid"] and not hasattr(public, "initial_password"))
                await feed(fixture["chat"])
                check(provider.contains_initial(fixture))
            check(provider.registration_verified)

            _phase = "privacy_before_any_lookup"
            before = (menu.calls, protected.calls, provider.edit_count)
            for chat_type in ("group", "supergroup", "channel"):
                for command in ("/start", "/credentials"):
                    await feed(-10007, command, chat_type=chat_type, sender=own["chat"])
            check(before == (menu.calls, protected.calls, provider.edit_count))

            _phase = "foreign_uid_denial"
            try:
                await protected.read(own["chat"], fixtures["foreign"]["uid"])
            except BotCredentialRequestDenied:
                check(True)
            else:
                raise BridgeFailure("foreign credential request was admitted")

            _phase = "original_uid_cleanup_after_rebind"
            changed = await control("change")
            await control("rebind_foreign")
            await feed(own["chat"], "/credentials")
            check(provider.contains_initial(fixtures["foreign"]))
            notices = provider.notice_count
            await dispatch_change(changed)
            check(not provider.contains_initial(own))
            check(provider.contains_initial(fixtures["foreign"]))
            check(notices == provider.notice_count)
            await control("restore_binding")

            _phase = "event_before_register"
            await control("new_initial")
            fresh = await protected.read(own["chat"], own["uid"])
            marker = html.escape(fresh.initial_password)
            protected.before_register = lambda: control("change")
            await feed(own["chat"], "/credentials")
            check(not provider.contains_text(marker))

            _phase = "late_edit_after_exact_delete"
            await control("new_initial")
            fresh = await protected.read(own["chat"], own["uid"])
            marker = html.escape(fresh.initial_password)
            provider.gate = asyncio.Event()
            provider.entered.clear()
            public = await menu.own_profile(own["chat"])

            async def answer(text, **kwargs):
                return await bot.send_message(chat_id=own["chat"], text=text, **kwargs)

            message = SimpleNamespace(
                from_user=SimpleNamespace(id=own["chat"]),
                chat=SimpleNamespace(id=own["chat"], type="private"),
                answer=answer,
            )
            pending = asyncio.create_task(cards.deliver(message, public))
            await asyncio.wait_for(provider.entered.wait(), timeout=3)
            changed = await control("change")
            restarted = BotCredentialCards(bot=bot, client=protected)
            await dispatch_change(changed, cleaner=restarted)
            provider.gate.set()
            try:
                await pending
            except NotificationPreferencesUnavailable:
                check(True)
            else:
                raise BridgeFailure("late deleted render was reported as delivery")
            pending = None
            provider.gate = None
            check(not provider.contains_text(marker))
            check((await control("facts"))["pending"] == 0)

            _phase = "uncertain_outcome_preserves_durable_retry"
            await control("new_initial")
            fresh = await protected.read(own["chat"], own["uid"])
            marker = html.escape(fresh.initial_password)
            provider.drop_reply = True
            public = await menu.own_profile(own["chat"])
            try:
                await cards.deliver(message, public)
            except NotificationPreferencesUnavailable:
                check(True)
            else:
                raise BridgeFailure("lost provider edit reply was reported confirmed")
            check(provider.contains_text(marker))
            changed = await control("change")
            provider.delete_rejected = True
            edit_count = provider.edit_count
            try:
                await dispatch_change(changed, cleaner=BotCredentialCards(bot=bot, client=protected))
            except NotificationPreferencesUnavailable:
                check(True)
            else:
                raise BridgeFailure("unresolved cleanup was acknowledged")
            check((await control("facts"))["pending"] > 0)
            check(provider.edit_count == edit_count)
            provider.delete_rejected = False
            await dispatch_change(changed)
            check(not provider.contains_text(marker))
            check((await control("facts"))["pending"] == 0)

            _phase = "settled_sanitize_fallback"
            await control("new_initial")
            fresh = await protected.read(own["chat"], own["uid"])
            marker = html.escape(fresh.initial_password)
            await feed(own["chat"], "/credentials")
            check(provider.contains_text(marker))
            changed = await control("change")
            provider.delete_rejected = True
            await dispatch_change(changed, cleaner=BotCredentialCards(bot=bot, client=protected))
            provider.delete_rejected = False
            check(not provider.contains_text(marker))
            check((await control("facts"))["pending"] == 0)

            _phase = "old_event_cannot_remove_later_card"
            await control("new_initial")
            fresh = await protected.read(own["chat"], own["uid"])
            marker = html.escape(fresh.initial_password)
            await feed(own["chat"], "/credentials")
            await dispatch_change(changed)
            check(provider.contains_text(marker))

            _phase = "grant_revocation_no_new_delivery"
            await control("revoke")
            edits = provider.edit_count
            await feed(own["chat"], "/credentials")
            check(provider.edit_count == edits)
            await control("reactivate")

            _phase = "archive_preserves_exact_old_card_cleanup"
            changed = await control("change")
            await control("archive")
            notices = provider.notice_count
            await dispatch_change(changed)
            check(not provider.contains_text(marker))
            check(provider.notice_count == notices)
            check((await control("facts"))["pending"] == 0)
            _phase = "completed"
        finally:
            if provider.gate is not None:
                provider.gate.set()
            if pending is not None:
                pending.cancel()
                await asyncio.gather(pending, return_exceptions=True)
            await menu_actual.close()
            await generic_actual.close()
            await protected_actual.close()
            await bot.session.close()
            await provider_runner.cleanup()


async def own_homework_main():
    """Use the existing isolated Java/PG/TLS fixture; never contact Telegram."""
    global _phase
    from datetime import date
    from unittest.mock import AsyncMock

    from bot.handlers.homework import cmd_homework_today, cmd_homework_week
    from bot.handlers.prefs import HOMEWORK_TODAY_LABEL, HOMEWORK_WEEK_LABEL, main_keyboard
    from bot.services.bot_menu import resolve_menu_scope

    fixtures = json.loads(os.environ.pop("NP01_FIXTURES"))
    own = fixtures["student"]
    host, port = os.environ["NP01_AUTHORITY"].rsplit(":", 1)
    ca_path = os.environ["NP01_CA"]
    token = os.environ.pop("NP01_SERVICE_TOKEN")
    shared = os.environ.pop("NP01_SHARED_SECRET")
    control_token = os.environ.pop("NP01_CONTROL_TOKEN")
    generic = AcademicGrpcClient(host, int(port), shared, True, ca_path)
    auth_host, auth_port = os.environ["NP01_AUTH_SERVICE_AUTHORITY"].rsplit(":", 1)
    menu = BotMenuGrpcClient(
        auth_host,
        int(auth_port),
        tls_enabled=True,
        tls_ca_path=os.environ["NP01_AUTH_SERVICE_CA"],
        service_token=os.environ.pop("NP01_AUTH_SERVICE_TOKEN"),
    )
    protected = BotCredentialsGrpcClient(host, int(port), shared, True, ca_path, token)
    async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=5)) as controls:

        async def control(action):
            async with controls.post(
                os.environ["NP01_CONTROL_URL"] + "/control/" + action,
                headers={"x-np01-control": control_token},
            ) as response:
                check(response.status == 200)
                return await response.json()

        async def arguments():
            scope = await resolve_menu_scope(menu, own["chat"])
            check(
                scope is not None
                and scope.user_id == own["uid"]
                and scope.roles_version > 0
                and scope.telegram_binding_version > 0
                and scope.homework_enabled
            )
            return dict(
                telegram_sender_id=own["chat"],
                expected_user_id=scope.user_id,
                expected_roles_version=scope.roles_version,
                expected_telegram_binding_version=scope.telegram_binding_version,
                date_from=own["date_from"],
                date_to=own["date_to"],
            )

        async def denied(values):
            try:
                await protected.homeworks(**values)
            except BotCredentialRequestDenied:
                check(True)
            else:
                check(False)

        try:
            _phase = "own_homework_profile"
            values = await arguments()
            scope = await resolve_menu_scope(menu, own["chat"])
            labels = [
                button.text
                for row in main_keyboard(homework_enabled=scope is not None and scope.homework_enabled).keyboard
                for button in row
            ]
            check(HOMEWORK_TODAY_LABEL in labels and HOMEWORK_WEEK_LABEL in labels)
            items = await protected.homeworks(**values)
            check(len(items) == 1 and items[0].homework_id == own["homework_id"])
            check(bool(items[0].title))
            title = items[0].title
            _phase = "own_homework_handlers"
            for handler in (cmd_homework_today, cmd_homework_week):
                message = SimpleNamespace(
                    from_user=SimpleNamespace(id=own["chat"]),
                    chat=SimpleNamespace(id=own["chat"], type="private"),
                    answer=AsyncMock(),
                )
                await handler(
                    message,
                    generic,
                    today=date.fromisoformat(own["date_from"]),
                    bot_homework_client=protected,
                    bot_menu_client=menu,
                )
                check(message.answer.await_count == 1 and title in message.answer.await_args.args[0])

            _phase = "own_homework_transport_denials"
            request = pb.BotOwnHomeworksForWeekRequest(**values)
            try:
                await generic._stub.GetOwnBotHomeworksForWeek(request, metadata=generic._metadata, timeout=3)
            except grpc.aio.AioRpcError as error:
                check(error.code() == grpc.StatusCode.UNAUTHENTICATED)
            else:
                check(False)
            # A directed bot token cannot replace the unchanged student-JWT admission.
            try:
                await generic._stub.GetHomeworksForWeek(
                    pb.HomeworksForWeekRequest(
                        student_id=scope.user_id,
                        group_id=next(iter(scope.student_groups)),
                        semester_id=1,
                        date_from=own["date_from"],
                        date_to=own["date_to"],
                    ),
                    metadata=(*generic._metadata, ("x-service-token", token)),
                    timeout=3,
                )
            except grpc.aio.AioRpcError as error:
                check(error.code() == grpc.StatusCode.PERMISSION_DENIED)
            else:
                check(False)
            await denied({**values, "expected_roles_version": values["expected_roles_version"] + 1})
            try:
                await generic._stub.GetOwnBotHomeworksForWeek(
                    pb.BotOwnHomeworksForWeekRequest(**{**values, "expected_telegram_binding_version": 0}),
                    metadata=(*generic._metadata, ("x-service-token", token)),
                    timeout=3,
                )
            except grpc.aio.AioRpcError as error:
                check(error.code() == grpc.StatusCode.INVALID_ARGUMENT)
            else:
                check(False)
            await denied(
                {**values, "expected_telegram_binding_version": values["expected_telegram_binding_version"] + 1}
            )
            await denied({**values, "telegram_sender_id": fixtures["foreign"]["chat"]})

            _phase = "own_homework_revoked_grant"
            await control("revoke")
            check(await resolve_menu_scope(menu, own["chat"]) is None)
            await denied(values)
            await control("reactivate")
            refreshed = await arguments()
            check(refreshed["expected_roles_version"] > values["expected_roles_version"])
            check(len(await protected.homeworks(**refreshed)) == 1)

            _phase = "own_homework_rebound_telegram"
            await control("rebind_foreign")
            await denied(refreshed)
            await control("restore_binding")
            restored = await arguments()
            check(restored["expected_telegram_binding_version"] > refreshed["expected_telegram_binding_version"])
            await denied(refreshed)
            check(len(await protected.homeworks(**restored)) == 1)
            _phase = "complete"
        finally:
            try:
                try:
                    await control("reactivate")
                finally:
                    await control("restore_binding")
            finally:
                await asyncio.gather(generic.close(), protected.close(), menu.close())


async def hosted_role_menu_main():
    """Read the existing CI services; only Node's owned fixture can change domain state."""
    global _phase, _headman_read_outcome
    from bot.grpc_client.bot_read_client import BotReadGrpcClient
    from bot.handlers.prefs import (
        CREDENTIALS_LABEL,
        HEADMAN_LABEL,
        HOMEWORK_TODAY_LABEL,
        HOMEWORK_WEEK_LABEL,
        LINKS_LABEL,
        LOGIN_LABEL,
        SETTINGS_LABEL,
        TEACHER_TODAY_LABEL,
    )
    from bot.handlers.role_reads import LessonChoices, role_reads_router
    from bot.services.bot_menu import BotReadDenied, BotReadUnavailable, resolve_menu_scope

    auth_token = Path(os.environ["NP01_AUTH_SERVICE_TOKEN_FILE"]).read_text().strip()
    read_token = Path(os.environ["NP01_READ_SERVICE_TOKEN_FILE"]).read_text().strip()
    reads = BotReadGrpcClient(
        "mobile-bff",
        19098,
        tls_enabled=True,
        tls_ca_path=os.environ["NP01_READ_SERVICE_CA"],
        service_token=read_token,
    )
    menu = CountingMenu(
        BotMenuGrpcClient(
            "auth-service",
            19090,
            tls_enabled=True,
            tls_ca_path=os.environ["NP01_AUTH_SERVICE_CA"],
            service_token=auth_token,
            read_client=reads,
        )
    )
    common = [CREDENTIALS_LABEL, LINKS_LABEL, LOGIN_LABEL, SETTINGS_LABEL]
    extras = {
        "student": [HOMEWORK_TODAY_LABEL, HOMEWORK_WEEK_LABEL],
        # A genuine HEADMAN also has the required active STUDENT grant.
        "headman": [HOMEWORK_TODAY_LABEL, HOMEWORK_WEEK_LABEL, HEADMAN_LABEL],
        "teacher": [TEACHER_TODAY_LABEL],
        "empty_teacher": [TEACHER_TODAY_LABEL],
        "empty_headman": [HOMEWORK_TODAY_LABEL, HOMEWORK_WEEK_LABEL, HEADMAN_LABEL],
        "admin": [],
        "mixed": [HOMEWORK_TODAY_LABEL, HOMEWORK_WEEK_LABEL, HEADMAN_LABEL, TEACHER_TODAY_LABEL],
    }
    provider_runner = bot = None
    async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=8)) as controls:

        async def control(action):
            async with controls.post(
                os.environ["NP01_CONTROL_URL"] + "/control/" + action,
                headers={"x-np01-control": os.environ["NP01_CONTROL_TOKEN"]},
            ) as response:
                check(response.status == 200)
                return await response.json()

        try:
            fixtures = await control("fixtures")
            provider = Provider(fixtures, control)
            app = web.Application()
            app.router.add_route("*", "/bot{token}/{method}", provider.handle)
            provider_runner = web.AppRunner(app, access_log=None, shutdown_timeout=2)
            await provider_runner.setup()
            site = web.TCPSite(provider_runner, "127.0.0.1", 0)
            await site.start()
            bot = Bot(
                token="123456:" + uuid.uuid4().hex,
                session=AiohttpSession(
                    api=TelegramAPIServer.from_base(f"http://127.0.0.1:{site._server.sockets[0].getsockname()[1]}")
                ),
            )
            dispatcher = Dispatcher()
            dispatcher.include_router(start_router)
            dispatcher.include_router(role_reads_router)
            choices = LessonChoices()
            sequence = 0

            async def feed(chat, text, *, chat_type="private", callback=False):
                nonlocal sequence
                sequence += 1
                message = {
                    "message_id": sequence + 1000,
                    "date": 1700000000,
                    "chat": {"id": chat, "type": chat_type},
                    "text": text,
                    "from": {"id": chat, "is_bot": False, "first_name": "Synthetic"},
                }
                payload = {"update_id": sequence, "message": message}
                if callback:
                    payload = {
                        "update_id": sequence,
                        "callback_query": {
                            "id": str(sequence),
                            "from": message["from"],
                            "chat_instance": "synthetic",
                            "message": message,
                            "data": text,
                        },
                    }
                before = len(provider.messages)
                await dispatcher.feed_update(
                    bot,
                    Update.model_validate(payload, context={"bot": bot}),
                    academic_client=None,
                    prefs_client=None,
                    bot_menu_client=menu,
                    bot_lesson_choices=choices,
                )
                return provider.messages[before:]

            def text(messages):
                return "\n".join(message[2] for message in messages)

            async def denied(operation):
                try:
                    await operation()
                except BotReadDenied:
                    check(True)
                else:
                    check(False)

            _phase = "hosted_private_current_menu"
            for role, expected in extras.items():
                own = fixtures[role]
                scope = await resolve_menu_scope(menu, own["chat"])
                check(scope is not None and scope.user_id == own["uid"])
                replies = await feed(own["chat"], "/start")
                check(len(replies) == 1 and replies[0][0] == own["chat"])
                labels = [button["text"] for row in replies[0][3]["keyboard"] for button in row]
                check(labels == common + expected and len(labels) == len(set(labels)))
            _auth_boundary["actual_auth_tls"] = True

            _phase = "hosted_private_guard"
            for command in ("/start", HEADMAN_LABEL, TEACHER_TODAY_LABEL):
                before = menu.calls
                replies = await feed(fixtures["headman"]["chat"], command, chat_type="group")
                check(menu.calls == before and len(replies) == 1)
                check(fixtures["subject_name"] not in text(replies))
                check(all(row["display_name"] not in text(replies) for row in fixtures["roster"]))

            teacher = fixtures["teacher"]
            teacher_scope = await resolve_menu_scope(menu, teacher["chat"])
            check(teacher_scope is not None)
            _phase = "hosted_teacher_today_and_empty"
            today = await menu.teacher_today(teacher["chat"], teacher_scope)
            check([lesson.lesson_id for lesson in today.lessons] == [fixtures["lesson_id"]])
            replies = await feed(teacher["chat"], TEACHER_TODAY_LABEL)
            check(fixtures["subject_name"] in text(replies) and fixtures["group_name"] in text(replies))
            empty = fixtures["empty_teacher"]
            empty_scope = await resolve_menu_scope(menu, empty["chat"])
            check(empty_scope is not None and not (await menu.teacher_today(empty["chat"], empty_scope)).lessons)
            check("Сегодня у тебя нет пар." in text(await feed(empty["chat"], TEACHER_TODAY_LABEL)))

            _phase = "hosted_headman_current_roster_statistics"
            headman = fixtures["headman"]
            old_scope = await resolve_menu_scope(menu, headman["chat"])
            check(old_scope is not None)
            try:
                current = await menu.headman_current(headman["chat"], old_scope)
            except BotReadDenied:
                _headman_read_outcome = "RPC_DENIED"
                raise
            except BotReadUnavailable as error:
                _headman_read_outcome = "RPC_UNAVAILABLE"
                try:
                    reported_context = BaseException.__context__.__get__(error)
                    if type(reported_context) is grpc.aio.AioRpcError:
                        reported_status = grpc.aio.AioRpcError.code(reported_context)
                        if type(reported_status) is grpc.StatusCode:
                            _headman_read_outcome = {
                                grpc.StatusCode.UNAUTHENTICATED: "RPC_STATUS_UNAUTHENTICATED",
                                grpc.StatusCode.UNAVAILABLE: "RPC_STATUS_UNAVAILABLE",
                                grpc.StatusCode.DEADLINE_EXCEEDED: "RPC_STATUS_DEADLINE_EXCEEDED",
                            }.get(reported_status, "RPC_STATUS_OTHER")
                    elif reported_context is None:
                        _headman_read_outcome = "LOCAL_UNAVAILABLE"
                except Exception:
                    pass
                raise
            if current.state == "NO_CURRENT":
                _headman_read_outcome = "RETURNED_NO_CURRENT"
            elif current.state == "CHOOSE":
                _headman_read_outcome = "RETURNED_CHOOSE"
            elif current.state == "READY":
                _headman_read_outcome = (
                    "RETURNED_READY_CURRENT_LESSON"
                    if current.lesson is not None and current.lesson.lesson_id == fixtures["lesson_id"]
                    else "RETURNED_READY_WRONG_LESSON"
                )
            check(current.state == "READY" and current.lesson.lesson_id == fixtures["lesson_id"])
            expected = sorted(
                (row["uid"], row["display_name"], row["status"], row["record_present"]) for row in fixtures["roster"]
            )
            check(
                sorted(
                    (row.student_id, row.display_name, row.status.upper(), row.record_present) for row in current.rows
                )
                == expected
            )
            check(any(row.status.upper() == "ABSENT" and row.record_present for row in current.rows))
            check(any(not row.record_present and not row.status for row in current.rows))
            replies = await feed(headman["chat"], HEADMAN_LABEL)
            rendered = text(replies)
            check(all(row["display_name"] in rendered for row in fixtures["roster"]))
            check("Отсутствует" in rendered and "Не отмечен" in rendered and "Всего:" in rendered)
            check(provider.rich_attempts == 1)
            empty = fixtures["empty_headman"]
            empty_scope = await resolve_menu_scope(menu, empty["chat"])
            check(empty_scope is not None)
            check((await menu.headman_current(empty["chat"], empty_scope)).state == "NO_CURRENT")
            check("Сейчас нет текущей пары." in text(await feed(empty["chat"], HEADMAN_LABEL)))

            _phase = "hosted_fresh_binding"
            rebound = await control("rebind_teacher")
            check(await resolve_menu_scope(menu, teacher["chat"]) is None)
            fresh = await resolve_menu_scope(menu, rebound["chat"])
            check(fresh is not None and fresh.user_id == teacher_scope.user_id)
            check(fresh.telegram_binding_version > teacher_scope.telegram_binding_version)
            await denied(lambda: menu.teacher_today(teacher["chat"], teacher_scope))
            check(fixtures["subject_name"] in text(await feed(rebound["chat"], TEACHER_TODAY_LABEL)))

            _phase = "hosted_revoked_headman_and_late_callback"
            await control("revoke_headman")
            fresh = await resolve_menu_scope(menu, headman["chat"])
            check(fresh is not None and fresh.roles_version > old_scope.roles_version and not fresh.headman_enabled)
            await denied(lambda: menu.headman_current(headman["chat"], old_scope, fixtures["lesson_id"]))
            replies = await feed(headman["chat"], HEADMAN_LABEL)
            check("Учёт группы недоступен" in text(replies))
            before = (menu.calls, len(provider.messages))
            await feed(headman["chat"], f"group-read:expired-own-choice:{fixtures['lesson_id']}", callback=True)
            check(before == (menu.calls, len(provider.messages)))
            # No real CHOOSE can be issued by canonical nonoverlapping unique slots.
            # Valid-choice/revoke behavior remains covered by the existing defensive unit cases.
            check((await control("finish"))["attendance_unchanged"] is True)
            _phase = "hosted_role_menu_complete"
        finally:
            if bot is not None:
                await bot.session.close()
            if provider_runner is not None:
                await provider_runner.cleanup()
            await asyncio.gather(menu.close(), reads.close())


if __name__ == "__main__":
    try:
        if sys.argv[1:] == ["--hosted-role-menu"]:
            asyncio.run(asyncio.wait_for(hosted_role_menu_main(), timeout=45))
        else:
            asyncio.run(
                asyncio.wait_for(own_homework_main(), timeout=30) if sys.argv[1:] == ["--own-homework"] else main()
            )
    except BaseException:
        print(
            json.dumps(
                {
                    "qualified": False,
                    "actual_tls": False,
                    "real_academic_pg": False,
                    "failures": 1,
                    "checks": _checks,
                    "phase": _phase,
                    **({"headmanReadOutcome": _headman_read_outcome} if sys.argv[1:] == ["--hosted-role-menu"] else {}),
                    "actual_auth_tls": False,
                    "old_academic_denied": False,
                    "old_credential_denied": False,
                }
            )
        )
        sys.exit(1)
    print(
        json.dumps(
            {
                "qualified": True,
                "actual_tls": True,
                "real_academic_pg": True,
                "failures": 0,
                "checks": _checks,
                "phase": _phase,
                "auth_producer_exercised": False,
                "external_telegram_exercised": False,
                **({"native_choice": "NOT_APPLICABLE"} if sys.argv[1:] == ["--hosted-role-menu"] else {}),
                **_auth_boundary,
            }
        )
    )
