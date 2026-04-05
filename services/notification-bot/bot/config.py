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

    model_config = {"env_file": ".env", "extra": "ignore"}


config = Settings()
