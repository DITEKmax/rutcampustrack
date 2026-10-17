"""Real bot consumer/Rabbit/Redis + aiogram HTTP500, isolated synthetic ports only.

Run solely under NOTIFY3 using a fresh owned broker/cache. Output contains only
provider counters, claim/queue facts and safe infrastructure error types.
"""

import argparse
import asyncio
import json
import secrets
from types import SimpleNamespace
from uuid import uuid4

import aio_pika
import aiohttp
import redis.asyncio as redis
from aiogram import Bot
from aiogram.client.session.aiohttp import AiohttpSession
from aiogram.client.telegram import TelegramAPIServer
from aiohttp import web

from bot.consumers.event_consumer import DLQ_QUEUE_NAME, EXCHANGE_NAME, QUEUE_NAME, start_consumer
from bot.consumers.event_dispatcher import EventDispatcher
from bot.services.idempotency_guard import BotIdempotencyGuard
from bot.services.otp_message_tracker import OtpMessageTracker
from bot.services.redis_client import ReminderRedisClient
from bot.services.send_queue import TelegramSendQueue


async def probe(args):
    provider_attempts = 0
    provider_successes = 0
    provider_recovered = False

    async def fail_send(request):
        nonlocal provider_attempts, provider_successes
        # Consume only in memory; never store or log the purpose-specific OTP.
        body = await request.post()
        provider_attempts += 1
        if provider_recovered:
            provider_successes += 1
            return web.json_response(
                {
                    "ok": True,
                    "result": {
                        "message_id": 1,
                        "date": 1,
                        "chat": {"id": int(body["chat_id"]), "type": "private"},
                        "text": "synthetic response",
                    },
                }
            )
        return web.json_response(
            {"ok": False, "error_code": 500, "description": "synthetic provider unavailable"}, status=500
        )

    app = web.Application()
    app.router.add_post("/bot{token}/sendMessage", fail_send)
    runner = web.AppRunner(app, access_log=None)
    await runner.setup()
    site = web.TCPSite(runner, "127.0.0.1", 0)
    await site.start()
    session = AiohttpSession(api=TelegramAPIServer.from_base(f"http://127.0.0.1:{runner.addresses[0][1]}"))
    bot = Bot(token="123456789:" + "A" * 35, session=session)
    cache = redis.Redis(host="127.0.0.1", port=args.redis_port, decode_responses=True)
    guard = BotIdempotencyGuard(redis_client=cache)
    queue = TelegramSendQueue()
    dispatcher = EventDispatcher(
        bot=bot,
        academic_client=SimpleNamespace(),  # OTP dispatch has no Academic RPC; explicit seam.
        send_queue=queue,
        redis_client=ReminderRedisClient("notify3:{lesson_id}:{user_id}", 300, redis_client=cache),
        config=SimpleNamespace(),
        otp_tracker=OtpMessageTracker(300, redis_client=cache),
    )
    url = f"amqp://guest:guest@127.0.0.1:{args.rabbit_port}/"
    consumer = asyncio.create_task(start_consumer(url, dispatcher, guard))
    publisher = None
    event_id = str(uuid4())
    try:
        publisher = await aio_pika.connect_robust(url)
        channel = await publisher.channel()
        async with aiohttp.ClientSession(auth=aiohttp.BasicAuth("guest", "guest")) as management:

            async def counts(name):
                async with management.get(f"http://127.0.0.1:{args.management_port}/api/queues/%2F/{name}") as response:
                    if response.status == 404:
                        return None
                    if response.status != 200:
                        raise RuntimeError("Synthetic management boundary unavailable")
                    data = await response.json()
                    if not all(key in data for key in ("messages_ready", "messages_unacknowledged")):
                        # Rabbit management samples new queue counters asynchronously.
                        return None
                    return {
                        **{key: data[key] for key in ("messages_ready", "messages_unacknowledged")},
                        "ack": data.get("message_stats", {}).get("ack", 0),
                    }

            async with asyncio.timeout(15):
                while await counts(QUEUE_NAME) is None or await counts(DLQ_QUEUE_NAME) is None:
                    if consumer.done():
                        consumer.result()
                    await asyncio.sleep(0.1)
            exchange = await channel.get_exchange(EXCHANGE_NAME)
            ack_before = (await counts(QUEUE_NAME))["ack"]
            body = {
                "event_id": event_id,
                "event_type": "otp.requested",
                "payload": {
                    "telegram_id": 111,
                    "code": f"{secrets.randbelow(1_000_000):06d}",
                    "ttl_seconds": 120,
                    "purpose": "login",
                },
            }
            await exchange.publish(
                aio_pika.Message(json.dumps(body).encode(), delivery_mode=aio_pika.DeliveryMode.PERSISTENT),
                routing_key="",
                mandatory=True,
            )
            async with asyncio.timeout(15):
                while True:
                    main, dlq = await counts(QUEUE_NAME), await counts(DLQ_QUEUE_NAME)
                    if main is None or dlq is None:
                        await asyncio.sleep(0.2)
                        continue
                    completed = await guard.is_completed(event_id)
                    if provider_attempts and (completed or dlq["messages_ready"] > 0):
                        # Management statistics are sampled; wait for the actual settled ACK/DLQ state.
                        if (
                            main["messages_unacknowledged"] == 0
                            and main["messages_ready"] == 0
                            and (main["ack"] > ack_before or dlq["messages_ready"] > 0)
                        ):
                            break
                    await asyncio.sleep(0.2)
            silent_loss = completed and provider_attempts > 0 and dlq["messages_ready"] == 0
            claim_released = await cache.exists(f"consumed:{guard.DEFAULT_CONSUMER_ID}:{event_id}") == 0
            failed_delivery = {
                "provider_http_status": 500,
                "provider_attempts": provider_attempts,
                "provider_successes": provider_successes,
                "dedup_completed": completed,
                "claim_released": claim_released,
                "main": main,
                "dlq": dlq,
                "silent_loss": silent_loss,
            }
            if not args.recover:
                return {**failed_delivery, "result": "COUNTEREXAMPLE" if silent_loss else "NO_SILENT_LOSS"}
            retained = not completed and claim_released and dlq["messages_ready"] == 1 and main["ack"] == ack_before
            if not retained:
                return {
                    "result": "ASSERTION_FAILURE",
                    "oracle": "failed delivery retained without completion/ACK",
                    "failed_delivery": failed_delivery,
                    "silent_loss": silent_loss,
                }

            # Controlled manual DLQ replay of this known owned event, never an automatic retry claim.
            replay_queue = await channel.get_queue(DLQ_QUEUE_NAME)
            replay = await replay_queue.get(timeout=5)
            same_event = json.loads(replay.body)["event_id"] == event_id
            if not same_event:
                return {"result": "ASSERTION_FAILURE", "oracle": "same owned event", "silent_loss": False}
            provider_recovered = True
            await exchange.publish(
                aio_pika.Message(replay.body, delivery_mode=aio_pika.DeliveryMode.PERSISTENT),
                routing_key="",
                mandatory=True,
            )
            async with asyncio.timeout(15):
                while True:
                    after = await counts(QUEUE_NAME)
                    if (
                        after is not None
                        and after["ack"] > main["ack"]
                        and after["messages_ready"] == 0
                        and after["messages_unacknowledged"] == 0
                        and await guard.is_completed(event_id)
                        and provider_successes == 1
                    ):
                        break
                    await asyncio.sleep(0.2)
            await replay.ack()
            successful_ack = after["ack"]
            await exchange.publish(
                aio_pika.Message(replay.body, delivery_mode=aio_pika.DeliveryMode.PERSISTENT),
                routing_key="",
                mandatory=True,
            )
            async with asyncio.timeout(15):
                while True:
                    after, dlq_after = await counts(QUEUE_NAME), await counts(DLQ_QUEUE_NAME)
                    if (
                        after is not None
                        and dlq_after is not None
                        and after["ack"] > successful_ack
                        and after["messages_ready"] == 0
                        and after["messages_unacknowledged"] == 0
                        and dlq_after["messages_ready"] == 0
                        and dlq_after["messages_unacknowledged"] == 0
                    ):
                        break
                    await asyncio.sleep(0.2)
            passed = provider_attempts == 2 and provider_successes == 1 and await guard.is_completed(event_id)
            return {
                "result": "RECOVERY_PASS" if passed else "ASSERTION_FAILURE",
                "failed_delivery": failed_delivery,
                "manual_replay_same_event": same_event,
                "provider_attempts": provider_attempts,
                "provider_successes": provider_successes,
                "dedup_completed": await guard.is_completed(event_id),
                "main": after,
                "dlq": dlq_after,
                "duplicate_had_no_provider_effect": passed,
                "silent_loss": False,
            }
    finally:
        consumer.cancel()
        await asyncio.gather(consumer, return_exceptions=True)
        if publisher is not None:
            await publisher.close()
        await queue.shutdown()
        await cache.aclose()
        await bot.session.close()
        await runner.cleanup()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for name in ("rabbit-port", "redis-port", "management-port"):
        parser.add_argument("--" + name, type=int, required=True)
    parser.add_argument("--recover", action="store_true")
    options = parser.parse_args()
    if any(not 1024 < value < 65536 for value in (options.rabbit_port, options.redis_port, options.management_port)):
        raise ValueError("Explicit isolated synthetic ports required")
    try:
        outcome = asyncio.run(probe(options))
    except Exception as error:
        print(json.dumps({"result": "INFRA_ERROR", "error_type": type(error).__name__}))
        raise SystemExit(2)
    print(json.dumps(outcome))
    raise SystemExit(1 if outcome["silent_loss"] or outcome["result"] == "ASSERTION_FAILURE" else 0)
