"""Actual broker/Redis consumer worker and independent persistent loopback provider supervisor.

Only safe boolean/count outcomes reach stdout. Synthetic credentials stay in child ENV
and in-memory provider state. A killed worker is restarted against the original broker,
Redis, Academic tracker and provider; no completed marker or broker ACK is simulated.
"""

from __future__ import annotations

import asyncio
import html
import json
import logging
import os
import signal
import sys
import uuid
from pathlib import Path
from types import SimpleNamespace
from urllib.parse import quote

import aio_pika
import aiohttp
import redis.asyncio as aioredis
from aiogram import Bot, Dispatcher
from aiogram.client.session.aiohttp import AiohttpSession
from aiogram.client.telegram import TelegramAPIServer
from aiogram.types import Update
from aiohttp import web

from bot.consumers.event_consumer import DLQ_QUEUE_NAME, QUEUE_NAME, start_consumer
from bot.consumers.event_dispatcher import EventDispatcher
from bot.grpc_client.academic_client import AcademicGrpcClient
from bot.grpc_client.bot_credentials_client import BotCredentialsGrpcClient
from bot.handlers.start import start_router
from bot.services.bot_credential_cards import BotCredentialCards
from bot.services.idempotency_guard import BotIdempotencyGuard
from bot.services.notification_prefs import NotificationPreferencesUnavailable
from bot.services.send_queue import TelegramSendQueue

logging.disable(logging.CRITICAL)
_phase = "setup"
_checks = 0
_worker_restarts = 0
_worker_starts = 0
_worker_reaps = 0
_held_lease_expiry = False
_held_lease_initial_pttl = 0
_held_lease_elapsed_ms = 0
_held_lease_redeliveries = 0
_WORKER_INPUTS = (
    "NP01_AUTHORITY",
    "NP01_CA",
    "NP01_SERVICE_TOKEN",
    "NP01_SHARED_SECRET",
    "NP01_RABBIT_HOST",
    "NP01_RABBIT_AMQP_PORT",
    "NP01_RABBIT_USER",
    "NP01_RABBIT_PASSWORD",
    "NP01_RABBIT_VHOST",
    "NP01_REDIS_HOST",
    "NP01_REDIS_PORT",
)


class BridgeFailure(RuntimeError):
    pass


def check(condition):
    global _checks
    if not condition:
        raise BridgeFailure("required native broker outcome failed")
    _checks += 1


def require_exact_card(cards, key, text):
    """Do not let another card with the same initial marker satisfy this invariant."""
    check(key in cards and cards[key] == text)


def capture_new_card(cards, before_keys, chat, marker):
    candidates = [key for key in cards if key not in before_keys and key[0] == chat and marker in cards[key]]
    check(len(candidates) == 1)
    key = candidates[0]
    return key, cards[key]


def worker_environment(provider_url, source, module_root):
    # Explicit own synthetic inputs, never a complete parent environment copy.
    environment = {name: source[name] for name in _WORKER_INPUTS}
    environment.update(
        {
            "LANG": "C.UTF-8",
            "LC_ALL": "C.UTF-8",
            "TZ": "UTC",
            "PYTHONDONTWRITEBYTECODE": "1",
            "PYTHONNOUSERSITE": "1",
            "PYTHONSAFEPATH": "1",
            "PYTHONPATH": module_root,
            "NP01_BROKER_WORKER": "1",
            "NP01_PROVIDER_URL": provider_url,
        }
    )
    return environment


def rabbit_url():
    user = quote(os.environ["NP01_RABBIT_USER"], safe="")
    password = quote(os.environ["NP01_RABBIT_PASSWORD"], safe="")
    vhost = quote(os.environ["NP01_RABBIT_VHOST"], safe="")
    return f"amqp://{user}:{password}@{os.environ['NP01_RABBIT_HOST']}:{os.environ['NP01_RABBIT_AMQP_PORT']}/{vhost}"


def redis_client():
    return aioredis.Redis(
        host=os.environ["NP01_REDIS_HOST"], port=int(os.environ["NP01_REDIS_PORT"]), decode_responses=True
    )


def clients():
    host, port = os.environ["NP01_AUTHORITY"].rsplit(":", 1)
    args = (host, int(port), os.environ["NP01_SHARED_SECRET"], True, os.environ["NP01_CA"])
    return AcademicGrpcClient(*args), BotCredentialsGrpcClient(*args, os.environ["NP01_SERVICE_TOKEN"])


def provider_bot(url):
    return Bot(token="123456:" + uuid.uuid4().hex, session=AiohttpSession(api=TelegramAPIServer.from_base(url)))


async def until(observe, predicate, *, timeout=30):
    deadline = asyncio.get_running_loop().time() + timeout
    while asyncio.get_running_loop().time() < deadline:
        value = await observe()
        if predicate(value):
            return value
        await asyncio.sleep(0.05)
    raise BridgeFailure("native observable deadline exceeded")


async def worker():
    """The actual production consumer runs in its own replaceable OS process."""
    generic, protected = clients()
    redis = redis_client()
    bot = provider_bot(os.environ["NP01_PROVIDER_URL"])
    queue = TelegramSendQueue(prefs_client=None)
    queue.start()
    guard = BotIdempotencyGuard(redis_client=redis)  # Actual default300s lease/7d completion TTL.
    dispatcher = EventDispatcher(
        bot=bot,
        academic_client=generic,
        send_queue=queue,
        redis_client=SimpleNamespace(_redis=redis),
        config=SimpleNamespace(admin_telegram_ids="0"),
        otp_tracker=SimpleNamespace(),
        credential_cards=BotCredentialCards(bot=bot, client=protected),
    )
    try:
        await start_consumer(rabbit_url(), dispatcher=dispatcher, idempotency_guard=guard)
    finally:
        await queue.shutdown()
        await protected.close()
        await generic.close()
        await bot.session.close()
        await redis.aclose()


class Provider:
    """Independent provider state survives a consumer worker process death."""

    def __init__(self, control):
        self.control = control
        self.cards = {}
        self.next_id = 1
        self.edit_count = 0
        self.delete_attempts = 0
        self.notice_count = 0
        self.registration_verified = True
        self.delete_rejected = False
        self.drop_edit_reply = False
        self.delete_gate = None
        self.delete_entered = asyncio.Event()

    def contains(self, marker):
        return any(marker in text for text in self.cards.values())

    async def handle(self, request):
        fields = await request.post()
        chat = int(fields["chat_id"])
        method = request.match_info["method"]
        if method == "sendMessage":
            message = self.next_id
            self.next_id += 1
            text = fields["text"]
            if "Пароль RutCampusTrack изменён" in text:
                self.notice_count += 1
            self.cards[(chat, message)] = text
            return self.result(chat, message)
        message = int(fields["message_id"])
        key = (chat, message)
        if method == "deleteMessage":
            self.delete_attempts += 1
            self.delete_entered.set()
            if self.delete_gate is not None:
                transport = request.transport
                await self.delete_gate.wait()
                if transport is None or transport.is_closing():
                    return self.error("request closed before isolated provider effect")
            if self.delete_rejected:
                return self.error("message can't be deleted")
            if key not in self.cards:
                return self.error("message to delete not found")
            del self.cards[key]
            return web.json_response({"ok": True, "result": True})
        if method != "editMessageText":
            return self.error("unsupported isolated provider method")
        self.edit_count += 1
        if key not in self.cards:
            return self.error("message to edit not found")
        text = fields["text"]
        if "Пароль: <code>" in text:
            receipt = await self.control(f"verify?chat={chat}&message={message}")
            self.registration_verified &= receipt.get("registered") is True
        self.cards[key] = text
        if self.drop_edit_reply:
            self.drop_edit_reply = False
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
        return web.json_response({"ok": False, "error_code": 400, "description": description}, status=400)


async def supervisor():
    global _phase, _worker_restarts, _worker_starts, _worker_reaps
    global _held_lease_expiry, _held_lease_initial_pttl, _held_lease_elapsed_ms, _held_lease_redeliveries
    fixtures = json.loads(os.environ["NP01_FIXTURES"])
    own, foreign = fixtures["own"], fixtures["foreign"]
    generic, protected = clients()
    redis = redis_client()
    guard = BotIdempotencyGuard(redis_client=redis)  # Observation only; no claim/complete writes here.
    child = observer = provider_runner = bot = None
    control_url = os.environ["NP01_CONTROL_URL"]
    control_token = os.environ["NP01_CONTROL_TOKEN"]
    management = f"http://{os.environ['NP01_RABBIT_HOST']}:{os.environ['NP01_RABBIT_HTTP_PORT']}"
    basic = aiohttp.BasicAuth(os.environ["NP01_RABBIT_USER"], os.environ["NP01_RABBIT_PASSWORD"])
    vhost = quote(os.environ["NP01_RABBIT_VHOST"], safe="")
    update_id = 0
    async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=5)) as http:

        async def control(action):
            async with http.post(
                control_url + "/control/" + action, headers={"x-np01-control": control_token}
            ) as response:
                if response.status != 200:
                    raise BridgeFailure("own fixture control failed")
                return await response.json()

        async def queue_facts(name=QUEUE_NAME):
            async with http.get(management + f"/api/queues/{vhost}/{quote(name, safe='')}", auth=basic) as response:
                if response.status == 404:
                    return None
                if response.status != 200:
                    raise BridgeFailure("actual Rabbit queue observation failed")
                value = await response.json()
                stats = value.get("message_stats", {})
                return {
                    "ready": value.get("messages_ready", 0),
                    "unacked": value.get("messages_unacknowledged", 0),
                    "consumers": value.get("consumers", 0),
                    "ack": stats.get("ack", 0),
                    "redeliver": stats.get("redeliver", 0),
                }

        async def key_state(event_id):
            return await redis.get(f"consumed:{BotIdempotencyGuard.DEFAULT_CONSUMER_ID}:{event_id}")

        async def start_worker():
            nonlocal child
            global _worker_starts
            check(child is None)
            environment = worker_environment(provider_url, os.environ, str(Path(__file__).resolve().parents[1]))
            child = await asyncio.create_subprocess_exec(
                sys.executable,
                str(Path(__file__).resolve()),
                env=environment,
                stdout=asyncio.subprocess.DEVNULL,
                stderr=asyncio.subprocess.DEVNULL,
            )
            _worker_starts += 1
            check((await control(f"worker_started?pid={child.pid}"))["worker_owned"])
            await until(queue_facts, lambda fact: fact is not None and fact["consumers"] == 1)
            check(child.returncode is None)

        async def stop_worker(*, force=False):
            nonlocal child
            global _worker_reaps
            if child is not None:
                owned = child
                if owned.returncode is None:
                    try:
                        if force:
                            owned.kill()
                        else:
                            owned.terminate()
                    except ProcessLookupError:
                        pass  # It exited between the status read and our own signal.
                    try:
                        await asyncio.wait_for(owned.wait(), timeout=5)
                    except TimeoutError:
                        try:
                            owned.kill()
                        except ProcessLookupError:
                            pass
                        await asyncio.wait_for(owned.wait(), timeout=5)
                else:
                    await owned.wait()
                child = None
                _worker_reaps += 1
                check((await control(f"worker_reaped?pid={owned.pid}"))["worker_reaped"])

        async def freeze_released_worker(event_id):
            # Freeze only our worker, then recheck the real Redis key. A claim that
            # raced with the observation is resumed and retried; no lease is forged.
            deadline = asyncio.get_running_loop().time() + 15
            while asyncio.get_running_loop().time() < deadline:
                check(child is not None and child.returncode is None)
                if await key_state(event_id) is None:
                    os.kill(child.pid, signal.SIGSTOP)
                    if await key_state(event_id) is None:
                        return
                    os.kill(child.pid, signal.SIGCONT)
                await asyncio.sleep(0.02)
            raise BridgeFailure("actual released-claim restart boundary unavailable")

        async def freeze_processing_worker(event_id):
            await asyncio.wait_for(provider.delete_entered.wait(), timeout=15)
            deadline = asyncio.get_running_loop().time() + 15
            while asyncio.get_running_loop().time() < deadline:
                value = await key_state(event_id)
                if isinstance(value, str) and value.startswith("processing:"):
                    os.kill(child.pid, signal.SIGSTOP)
                    current = await key_state(event_id)
                    pttl = await redis.pttl(f"consumed:{BotIdempotencyGuard.DEFAULT_CONSUMER_ID}:{event_id}")
                    if current == value and pttl > 0:
                        return current, pttl, asyncio.get_running_loop().time()
                    os.kill(child.pid, signal.SIGCONT)
                await asyncio.sleep(0.02)
            raise BridgeFailure("actual held-claim crash boundary unavailable")

        async def feed(chat):
            nonlocal update_id
            update_id += 1
            update = Update.model_validate(
                {
                    "update_id": update_id,
                    "message": {
                        "message_id": update_id + 900,
                        "date": 1700000000,
                        "chat": {"id": chat, "type": "private"},
                        "from": {"id": chat, "is_bot": False, "first_name": "Synthetic"},
                        "text": "/start",
                    },
                },
                context={"bot": bot},
            )
            await dispatcher.feed_update(
                bot, update, academic_client=generic, prefs_client=SimpleNamespace(), credential_cards=cards
            )

        async def observed_ack(event_id, previous_ack):
            async def observe():
                return await queue_facts(), await guard.is_completed(event_id), await control("facts")

            result = await until(
                observe,
                lambda value: value[0] is not None
                and value[0]["ack"] > previous_ack
                and value[0]["ready"] == value[0]["unacked"] == 0
                and value[1]
                and value[2]["pending"] == value[2]["outbox_pending"] == 0,
            )
            check(result[1] and result[2]["initial_cleared"])
            return result[0]

        try:
            provider = Provider(control)
            app = web.Application()
            app.router.add_route("*", "/bot{token}/{method}", provider.handle)
            provider_runner = web.AppRunner(app, access_log=None, shutdown_timeout=5)
            await provider_runner.setup()
            site = web.TCPSite(provider_runner, "127.0.0.1", 0)
            await site.start()
            provider_url = f"http://127.0.0.1:{site._server.sockets[0].getsockname()[1]}"
            bot = provider_bot(provider_url)
            cards = BotCredentialCards(bot=bot, client=protected)
            dispatcher = Dispatcher()
            dispatcher.include_router(start_router)
            observer = await aio_pika.connect_robust(rabbit_url())
            await start_worker()

            _phase = "actual_auth_change_outbox_confirm_consumer_ack"
            public = await generic.get_user_by_telegram_id(own["chat"])
            check(public.found and public.user_id == own["uid"] and not public.initial_password)
            await feed(own["chat"])
            before_foreign = set(provider.cards)
            await feed(foreign["chat"])
            own_marker, foreign_marker = html.escape(own["initial"]), html.escape(foreign["initial"])
            old_foreign_key, old_foreign_text = capture_new_card(
                provider.cards, before_foreign, foreign["chat"], foreign_marker
            )
            check(provider.contains(own_marker) and provider.contains(foreign_marker))
            check(provider.registration_verified)
            baseline = await queue_facts()
            produced = await control("change")
            check(produced["producer_v2"] and produced["producer_original_uid"])
            check(produced["producer_has_recipient"] and produced["producer_payload_fields"] == 4)
            check(produced["outbox_pending"] == 1 and produced["pending"] > 0 and produced["revoked_sessions"] > 0)
            event_id = produced["event_id"]
            published = await control("publish")
            check(published["outbox_pending"] == 0 and published["outbox_sent"] == 1)
            acknowledged = await observed_ack(event_id, baseline["ack"])
            check(not provider.contains(own_marker) and provider.contains(foreign_marker))
            require_exact_card(provider.cards, old_foreign_key, old_foreign_text)

            _phase = "same_real_event_completed_dedup_ack"
            effects = provider.delete_attempts, provider.edit_count, provider.notice_count
            await control("replay")
            acknowledged = await observed_ack(event_id, acknowledged["ack"])
            check(effects == (provider.delete_attempts, provider.edit_count, provider.notice_count))
            require_exact_card(provider.cards, old_foreign_key, old_foreign_text)

            _phase = "actual_unlinked_reset_original_uid_cleanup"
            await control("new_initial")
            fresh = await protected.read(own["chat"], own["uid"])
            reset_marker = html.escape(fresh.initial_password)
            await feed(own["chat"])
            check(provider.contains(reset_marker))
            await control("rebind")
            before_new_foreign = set(provider.cards)
            await feed(own["chat"])
            new_foreign_key, new_foreign_text = capture_new_card(
                provider.cards, before_new_foreign, own["chat"], foreign_marker
            )
            check(new_foreign_key != old_foreign_key)
            require_exact_card(provider.cards, old_foreign_key, old_foreign_text)
            require_exact_card(provider.cards, new_foreign_key, new_foreign_text)
            notices = provider.notice_count
            produced = await control("reset")
            check(produced["producer_v2"] and produced["producer_original_uid"])
            check(not produced["producer_has_recipient"] and produced["producer_payload_fields"] == 3)
            await control("publish")
            acknowledged = await observed_ack(produced["event_id"], acknowledged["ack"])
            check(not provider.contains(reset_marker))
            # BR-T01 sensitivity: remove only new_foreign_key in an isolated own
            # provider counterexample; this exact invariant must fail even if old survives.
            require_exact_card(provider.cards, old_foreign_key, old_foreign_text)
            require_exact_card(provider.cards, new_foreign_key, new_foreign_text)
            check(provider.notice_count == notices)
            await control("restore_binding")

            _phase = "uncertain_provider_keeps_real_claim_retry_without_ack"
            await control("new_initial")
            fresh = await protected.read(own["chat"], own["uid"])
            uncertain_marker = html.escape(fresh.initial_password)
            message = SimpleNamespace(
                from_user=SimpleNamespace(id=own["chat"]),
                chat=SimpleNamespace(id=own["chat"], type="private"),
                answer=lambda text, **kwargs: bot.send_message(own["chat"], text, **kwargs),
            )
            provider.drop_edit_reply = True
            try:
                await cards.deliver(message, SimpleNamespace(user_id=own["uid"]))
            except NotificationPreferencesUnavailable:
                check(True)
            else:
                raise BridgeFailure("lost provider edit reply did not remain uncertain")
            check(provider.contains(uncertain_marker))
            provider.delete_rejected = True
            edits, deletes = provider.edit_count, provider.delete_attempts
            baseline = await queue_facts()
            produced = await control("change")
            event_id = produced["event_id"]
            await control("publish")

            async def retry_facts():
                return await queue_facts(), await key_state(event_id), await control("facts")

            retry = await until(
                retry_facts,
                lambda value: value[0] is not None
                and value[0]["redeliver"] > baseline["redeliver"]
                and value[0]["ack"] == baseline["ack"]
                and value[1] is None
                and value[2]["pending"] > 0
                and provider.delete_attempts >= deletes + 2,
            )
            check(retry[1] is None and not await guard.is_completed(event_id))
            check(provider.contains(uncertain_marker) and provider.edit_count == edits)
            require_exact_card(provider.cards, new_foreign_key, new_foreign_text)

            _phase = "actual_worker_death_durable_redelivery_recovery"
            await freeze_released_worker(event_id)
            await stop_worker(force=True)  # Actual process death after a verified real Redis release.
            stopped = await until(
                queue_facts,
                lambda fact: fact is not None
                and fact["consumers"] == 0
                and fact["ready"] == 1
                and fact["unacked"] == 0,
            )
            check(stopped["ack"] == baseline["ack"] and await key_state(event_id) is None)
            check((await control("facts"))["pending"] > 0 and provider.contains(uncertain_marker))
            require_exact_card(provider.cards, new_foreign_key, new_foreign_text)
            provider.delete_rejected = False
            await start_worker()
            _worker_restarts += 1
            acknowledged = await observed_ack(event_id, baseline["ack"])
            check(not provider.contains(uncertain_marker) and provider.edit_count == edits)
            require_exact_card(provider.cards, old_foreign_key, old_foreign_text)
            require_exact_card(provider.cards, new_foreign_key, new_foreign_text)

            _phase = "worker_death_while_default_processing_lease_held"
            check(BotIdempotencyGuard.DEFAULT_TTL_SECONDS == 300)
            await control("new_initial")
            fresh = await protected.read(own["chat"], own["uid"])
            held_marker = html.escape(fresh.initial_password)
            before_held_card = set(provider.cards)
            await feed(own["chat"])
            held_card_key, held_card_text = capture_new_card(provider.cards, before_held_card, own["chat"], held_marker)
            provider.delete_gate = asyncio.Event()
            provider.delete_entered.clear()
            baseline = await queue_facts()
            produced = await control("change")
            event_id = produced["event_id"]
            await control("publish")
            held_value, held_pttl, observed_at = await freeze_processing_worker(event_id)
            check(270_000 < held_pttl <= 300_000)
            _held_lease_initial_pttl = held_pttl
            unacked = await until(
                queue_facts, lambda fact: fact is not None and fact["unacked"] == 1 and fact["ack"] == baseline["ack"]
            )
            check(unacked["unacked"] == 1 and (await control("facts"))["pending"] > 0)
            check(await key_state(event_id) == held_value)
            require_exact_card(provider.cards, held_card_key, held_card_text)
            require_exact_card(provider.cards, new_foreign_key, new_foreign_text)
            await stop_worker(force=True)
            stopped = await until(
                queue_facts,
                lambda fact: fact is not None
                and fact["consumers"] == 0
                and fact["ready"] == 1
                and fact["unacked"] == 0,
            )
            check(await key_state(event_id) == held_value)
            await start_worker()
            _worker_restarts += 1
            provider.delete_gate.set()
            provider.delete_gate = None

            _phase = "held_lease_blocks_premature_ack_until_natural_expiry"
            # Two subsequent native redeliveries require a NACK/requeue cycle by
            # the fresh worker while it cannot acquire the original lease.
            requeued = await until(
                queue_facts,
                lambda fact: fact is not None
                and fact["redeliver"] >= stopped["redeliver"] + 2
                and fact["ack"] == baseline["ack"],
            )
            _held_lease_redeliveries = requeued["redeliver"] - stopped["redeliver"]
            check(await key_state(event_id) == held_value)
            expiry_deadline = observed_at + BotIdempotencyGuard.DEFAULT_TTL_SECONDS + 45
            while asyncio.get_running_loop().time() < expiry_deadline:
                first = await key_state(event_id)
                if first != held_value:
                    break
                facts, state = await queue_facts(), await control("facts")
                after = await key_state(event_id)
                if after != held_value:
                    break  # Discard observations that crossed the natural expiry boundary.
                check(facts["ack"] == baseline["ack"] and state["pending"] > 0)
                require_exact_card(provider.cards, held_card_key, held_card_text)
                require_exact_card(provider.cards, new_foreign_key, new_foreign_text)
                await asyncio.sleep(0.2)
            else:
                raise BridgeFailure("default processing lease did not naturally expire")
            _held_lease_elapsed_ms = int((asyncio.get_running_loop().time() - observed_at) * 1000)
            check(_held_lease_elapsed_ms >= held_pttl - 2_000)
            _held_lease_expiry = True
            held_value = None  # Never log the actual processing claim token.
            acknowledged = await observed_ack(event_id, baseline["ack"])
            check(held_card_key not in provider.cards and not provider.contains(held_marker))
            require_exact_card(provider.cards, old_foreign_key, old_foreign_text)
            require_exact_card(provider.cards, new_foreign_key, new_foreign_text)

            _phase = "unknown_version_real_dlq_releases_claim"
            malformed = await control("unknown_version")  # Explicit negative wire input, no fake producer claim.
            event_id = malformed["event_id"]
            await until(lambda: queue_facts(DLQ_QUEUE_NAME), lambda fact: fact is not None and fact["ready"] == 1)
            channel = await observer.channel()
            try:
                dlq = await channel.get_queue(DLQ_QUEUE_NAME, ensure=True)
                rejected = await dlq.get(timeout=5, fail=True)
                body = json.loads(rejected.body)
                check(body["event_id"] == event_id and body["event_version"] == 991)
                check(
                    any(
                        death.get("queue") == QUEUE_NAME and death.get("reason") == "rejected"
                        for death in rejected.headers.get("x-death", [])
                    )
                )
                check(await key_state(event_id) is None and not await guard.is_completed(event_id))
                check((await control("facts"))["pending"] == 0)
                require_exact_card(provider.cards, new_foreign_key, new_foreign_text)
                await rejected.ack()
            finally:
                await channel.close()
            _phase = "completed"
        finally:
            await stop_worker(force=True)
            if provider_runner is not None and provider.delete_gate is not None:
                provider.delete_gate.set()
            if observer is not None:
                await observer.close()
            await generic.close()
            await protected.close()
            await redis.aclose()
            if bot is not None:
                await bot.session.close()
            if provider_runner is not None:
                await provider_runner.cleanup()


async def run_supervisor():
    # Handle TERM once so the still-live supervisor reaps its own worker before
    # reporting terminal state. Repeated signals cannot cancel the cleanup awaits.
    loop = asyncio.get_running_loop()
    task = asyncio.create_task(supervisor())
    stopping = False

    def stop():
        nonlocal stopping
        if not stopping:
            stopping = True
            task.cancel()

    for name in (signal.SIGTERM, signal.SIGINT):
        loop.add_signal_handler(name, stop)
    try:
        await task
    finally:
        for name in (signal.SIGTERM, signal.SIGINT):
            loop.remove_signal_handler(name)


if __name__ == "__main__":
    if os.environ.get("NP01_BROKER_WORKER") == "1":
        asyncio.run(worker())  # Native worker stdout/stderr are discarded by the supervisor.
    else:
        try:
            asyncio.run(run_supervisor())
        except BaseException:
            print(
                json.dumps(
                    {
                        "qualified": False,
                        "failures": 1,
                        "checks": _checks,
                        "phase": _phase,
                        "workers_reaped": _worker_starts == _worker_reaps,
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
                    "real_auth_producer": True,
                    "real_rabbit": True,
                    "real_redis": True,
                    "actual_worker_restart": _worker_restarts == 2,
                    "worker_restarts": _worker_restarts,
                    "workers_reaped": _worker_starts == _worker_reaps,
                    "natural_held_lease_expiry": _held_lease_expiry,
                    "held_lease_initial_pttl_ms": _held_lease_initial_pttl,
                    "held_lease_elapsed_ms": _held_lease_elapsed_ms,
                    "held_lease_redeliveries": _held_lease_redeliveries,
                    "failures": 0,
                    "checks": _checks,
                    "phase": _phase,
                    "external_telegram_exercised": False,
                    "auth_http_exercised": False,
                }
            )
        )
