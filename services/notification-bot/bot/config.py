from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    bot_token: str = "placeholder"
    rabbitmq_url: str = "amqp://rct_user:rct_dev_pass@rabbitmq:5672/"
    academic_grpc_host: str = "academic-service"
    academic_grpc_port: int = 19091
    redis_host: str = "redis"
    redis_port: int = 6379
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

    model_config = {"env_file": ".env", "extra": "ignore"}


config = Settings()
