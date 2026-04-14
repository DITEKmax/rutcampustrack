import asyncio
import logging

from aiogram import Bot, Dispatcher
from aiogram.fsm.storage.memory import MemoryStorage
from aiohttp import web

from bot.config import config
from bot.consumers.event_consumer import start_consumer
from bot.consumers.event_dispatcher import EventDispatcher
from bot.grpc_client.academic_client import AcademicGrpcClient
from bot.grpc_client.schedule_client import ScheduleGrpcClient
from bot.handlers import login_router, prefs_router, start_router, status_router
from bot.services.attendance_http_client import AttendanceHttpClient
from bot.services.auth_http_client import AuthHttpClient
from bot.services.jwt_redis_client import JwtRedisClient
from bot.services.notification_prefs import NotificationPrefsClient
from bot.services.redis_client import ReminderRedisClient
from bot.services.reminder_scheduler import ReminderScheduler
from bot.services.send_queue import TelegramSendQueue

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
)
logger = logging.getLogger(__name__)

# Global references for health check
_consumer_task: asyncio.Task | None = None
_bot_task: asyncio.Task | None = None


async def health_handler(request: web.Request) -> web.Response:
    """Health check endpoint — verifies watchdog task and bot polling are alive."""
    if _consumer_task is None or _consumer_task.done():
        raise web.HTTPServiceUnavailable(text='{"status":"DOWN","reason":"watchdog_dead"}')
    if _bot_task is None or _bot_task.done():
        raise web.HTTPServiceUnavailable(text='{"status":"DOWN","reason":"bot_dead"}')
    return web.Response(text='{"status":"UP"}', content_type="application/json")


async def run_health_server() -> None:
    app = web.Application()
    app.router.add_get("/health", health_handler)
    runner = web.AppRunner(app)
    await runner.setup()
    site = web.TCPSite(runner, "0.0.0.0", config.health_port)
    await site.start()
    logger.info("Health server started on port %d", config.health_port)


async def run_with_watchdog(rabbitmq_url: str, dispatcher=None) -> None:
    """
    Watchdog loop — restarts start_consumer on failure or silent exit.
    Propagates CancelledError to allow clean shutdown.
    """
    while True:
        try:
            await start_consumer(rabbitmq_url, dispatcher=dispatcher)
            # start_consumer returned normally — silent consumer death, restart
            logger.warning("Consumer exited normally (silent death) — restarting in 5s")
        except asyncio.CancelledError:
            logger.info("Watchdog cancelled, shutting down")
            raise
        except Exception:
            logger.warning("Consumer failed with exception — restarting in 5s", exc_info=True)

        await asyncio.sleep(5)


async def create_clients():
    """Create and start all service clients for bot handlers."""
    academic_client = AcademicGrpcClient(config.academic_grpc_host, config.academic_grpc_port, config.grpc_secret)
    schedule_client = ScheduleGrpcClient(config.schedule_grpc_host, config.schedule_grpc_port, config.grpc_secret)
    jwt_redis = JwtRedisClient(
        key_prefix=config.jwt_key_prefix,
        ttl=config.jwt_ttl,
        host=config.redis_host,
        port=config.redis_port,
        password=config.redis_password,
    )
    auth_client = AuthHttpClient(base_url=f"http://{config.auth_service_host}:{config.auth_service_port}")
    attendance_client = AttendanceHttpClient(base_url=config.api_gateway_url)

    # Start HTTP sessions (must be in async context — Pitfall 3)
    await auth_client.start()
    await attendance_client.start()

    return academic_client, schedule_client, jwt_redis, auth_client, attendance_client


async def main() -> None:
    global _consumer_task, _bot_task

    await run_health_server()

    # Create service clients
    academic_client, schedule_client, jwt_redis, auth_client, attendance_client = await create_clients()

    # Create Bot and Dispatcher
    bot = Bot(token=config.bot_token)
    storage = MemoryStorage()
    dp = Dispatcher(storage=storage)

    # Notification on/off prefs (Redis-backed)
    prefs_client = NotificationPrefsClient(
        host=config.redis_host,
        port=config.redis_port,
        password=config.redis_password,
    )

    # Inject dependencies via dp workflow data (Aiogram 3 DI pattern)
    dp["academic_client"] = academic_client
    dp["schedule_client"] = schedule_client
    dp["jwt_redis"] = jwt_redis
    dp["auth_client"] = auth_client
    dp["attendance_client"] = attendance_client
    dp["prefs_client"] = prefs_client

    # Register routers
    dp.include_router(start_router)
    dp.include_router(login_router)
    dp.include_router(status_router)
    dp.include_router(prefs_router)

    # Create send queue and reminder redis client
    send_queue = TelegramSendQueue(prefs_client=prefs_client)
    send_queue.start()
    redis_client = ReminderRedisClient(
        key_template=config.reminder_key_template,
        ttl=config.reminder_key_ttl,
        host=config.redis_host,
        port=config.redis_port,
        password=config.redis_password,
    )

    # Create reminder scheduler (NOTIF-02, NOTIF-03 — Plan 25-01/25-02)
    reminder_scheduler = ReminderScheduler(
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
        redis_client=redis_client,
    )

    # Create event dispatcher with all dependencies
    dispatcher = EventDispatcher(
        bot=bot,
        academic_client=academic_client,
        send_queue=send_queue,
        redis_client=redis_client,
        config=config,
        reminder_scheduler=reminder_scheduler,
    )

    # Start watchdog with dispatcher
    _consumer_task = asyncio.create_task(run_with_watchdog(config.rabbitmq_url, dispatcher=dispatcher))

    # Start bot polling (handle_signals=False — Pitfall 5: signals managed by main loop)
    _bot_task = asyncio.create_task(dp.start_polling(bot, handle_signals=False))

    logger.info("notification-bot started (health + watchdog + bot polling)")

    try:
        # Wait for either task to complete (whichever finishes first)
        done, pending = await asyncio.wait(
            [_consumer_task, _bot_task],
            return_when=asyncio.FIRST_EXCEPTION,
        )
        for task in done:
            if task.exception():
                logger.exception("Task failed: %s", task.get_name(), exc_info=task.exception())
    except asyncio.CancelledError:
        logger.info("Main cancelled, shutting down")
    finally:
        # Cleanup
        for task in [_consumer_task, _bot_task]:
            if task and not task.done():
                task.cancel()
        await send_queue.shutdown()
        await redis_client.close()
        await auth_client.close()
        await attendance_client.close()
        await jwt_redis.close()
        await prefs_client.close()
        await schedule_client.close()
        await academic_client.close()


if __name__ == "__main__":
    asyncio.run(main())
