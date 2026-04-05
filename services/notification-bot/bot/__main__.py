import asyncio
import logging

from aiohttp import web

from bot.config import config
from bot.consumers.event_consumer import start_consumer

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
)
logger = logging.getLogger(__name__)

# Global references for health check
_consumer_task: asyncio.Task | None = None


async def health_handler(request: web.Request) -> web.Response:
    """Health check endpoint — verifies watchdog task is alive."""
    if _consumer_task is None or _consumer_task.done():
        raise web.HTTPServiceUnavailable(text='{"status":"DOWN","reason":"watchdog_dead"}')
    return web.Response(text='{"status":"UP"}', content_type="application/json")


async def run_health_server() -> None:
    app = web.Application()
    app.router.add_get("/health", health_handler)
    runner = web.AppRunner(app)
    await runner.setup()
    site = web.TCPSite(runner, "0.0.0.0", config.health_port)
    await site.start()
    logger.info("Health server started on port %d", config.health_port)


async def run_with_watchdog(rabbitmq_url: str) -> None:
    """
    Watchdog loop — restarts start_consumer on failure or silent exit.
    Propagates CancelledError to allow clean shutdown.
    """
    while True:
        try:
            await start_consumer(rabbitmq_url)
            # start_consumer returned normally — silent consumer death, restart
            logger.warning("Consumer exited normally (silent death) — restarting in 5s")
        except asyncio.CancelledError:
            logger.info("Watchdog cancelled, shutting down")
            raise
        except Exception:
            logger.warning("Consumer failed with exception — restarting in 5s", exc_info=True)

        await asyncio.sleep(5)


async def main() -> None:
    global _consumer_task

    await run_health_server()

    _consumer_task = asyncio.create_task(run_with_watchdog(config.rabbitmq_url))
    logger.info("notification-bot started")

    try:
        await _consumer_task
    except asyncio.CancelledError:
        logger.info("Consumer task cancelled, shutting down")
    except Exception:
        logger.exception("Consumer task failed")


if __name__ == "__main__":
    asyncio.run(main())
