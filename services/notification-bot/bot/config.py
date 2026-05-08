from pydantic_settings import BaseSettings


class StartupError(RuntimeError):
    """Raised when mandatory configuration is missing — kills the process before
    any handler/consumer starts. Used by validate_startup_config (M08 G8)."""


class Settings(BaseSettings):
    bot_token: str = "placeholder"
    rabbitmq_url: str = "amqp://rct_user:rct_dev_pass@rabbitmq:5672/"
    academic_grpc_host: str = "academic-service"
    academic_grpc_port: int = 19091
    redis_host: str = "redis"
    redis_port: int = 6379
    redis_password: str = "rct_dev_pass"
    health_port: int = 8081

    # Redis key namespace for reminder messages (per D-07)
    # Key format: reminder:msgs:{lesson_id}:{user_id}
    # Type: Redis List
    #   - RPUSH to add message_id
    #   - LRANGE key 0 -1 to read all message_ids
    #   - DEL key to cleanup
    # TTL: 86400 seconds (24 hours) — safety net against event loss (per D-08)
    reminder_key_template: str = "reminder:msgs:{lesson_id}:{user_id}"
    reminder_key_ttl: int = 86400

    # gRPC shared secret (IMP-09)
    grpc_secret: str = ""

    # Schedule Service gRPC
    schedule_grpc_host: str = "schedule-service"
    schedule_grpc_port: int = 19092

    # Auth Service HTTP (direct, not through Gateway — per D-06)
    auth_service_host: str = "auth-service"
    auth_service_port: int = 9090

    # API Gateway (for Attendance REST calls — per D-09)
    api_gateway_url: str = "http://api-gateway:8080"

    # JWT storage in Redis (per D-07)
    # Key format: bot:jwt:{telegram_id}
    # Value: JSON string {"access_token": "...", "refresh_token": "..."}
    # TTL: 604800 seconds (7 days) — matches refresh token lifetime
    jwt_key_prefix: str = "bot:jwt:"
    jwt_ttl: int = 604800

    # Mini App URL for inline check-in button. Supports either a t.me Mini App
    # deep link or a direct WebApp URL configured in BotFather.
    mini_app_url: str = "https://t.me/ruttrack_bot/ruttrack"

    # OTP lifetime in seconds — mirrors OtpProperties.ttlSeconds in auth-service.
    # Used as TTL for otp_msgs:{telegram_id} Redis entries and for the deferred
    # message cleanup task scheduled by /login.
    otp_ttl_seconds: int = 300

    # M04 Группа 9 — Telegram ID админа (или список через запятую) куда
    # форвардить alert.fired события от Alertmanager. "0" = disabled.
    admin_telegram_ids: str = "0"

    model_config = {"env_file": ".env", "extra": "ignore"}


config = Settings()


def validate_startup_config(settings: "Settings") -> None:
    """Fail-fast validator invoked before bot startup (M08 G8, NEW-164).

    Guards against silent mis-deployment where mandatory secrets are
    missing — notification-bot would otherwise connect to gRPC servers
    with empty x-grpc-secret metadata and be silently rejected
    (UNAUTHENTICATED) while the health endpoint still reports UP.

    Raises StartupError listing every missing field; caller must not
    catch it — let the process die so Docker restart policy surfaces
    the misconfiguration in CI/prod.
    """
    missing: list[str] = []
    if not settings.grpc_secret:
        missing.append("GRPC_SECRET (x-grpc-secret metadata for gRPC calls to academic/schedule)")
    if not settings.bot_token or settings.bot_token == "placeholder":
        missing.append("BOT_TOKEN (Telegram bot token)")
    if missing:
        raise StartupError("Missing mandatory configuration:\n  - " + "\n  - ".join(missing))
