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
_connection = None


async def health_handler(request: web.Request) -> web.Response:
    """Health check endpoint — verifies consumer task alive and RabbitMQ connection open."""
    if _consumer_task is None or _consumer_task.done():
        raise web.HTTPServiceUnavailable(text='{"status":"DOWN","reason":"consumer_dead"}')
    if _connection is not None and _connection.is_closed:
        raise web.HTTPServiceUnavailable(text='{"status":"DOWN","reason":"rabbitmq_disconnected"}')
    return web.Response(text='{"status":"UP"}', content_type="application/json")


async def run_health_server() -> None:
    app = web.Application()
    app.router.add_get("/health", health_handler)
    runner = web.AppRunner(app)
    await runner.setup()
    site = web.TCPSite(runner, "0.0.0.0", config.health_port)
    await site.start()
    logger.info("Health server started on port %d", config.health_port)


async def run_consumer() -> None:
    global _connection
    _connection = await start_consumer(config.rabbitmq_url)


async def main() -> None:
    global _consumer_task

    await run_health_server()

    _consumer_task = asyncio.create_task(run_consumer())
    logger.info("notification-bot started")

    try:
        await _consumer_task
    except asyncio.CancelledError:
        logger.info("Consumer task cancelled, shutting down")
    except Exception:
        logger.exception("Consumer task failed")


if __name__ == "__main__":
    asyncio.run(main())
