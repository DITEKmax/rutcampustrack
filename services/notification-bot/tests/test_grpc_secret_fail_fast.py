"""Security contract: notification-bot MUST fail-fast when mandatory secrets
are missing at startup.

M08 Group 8 (NEW-164). Regression guard against silent mis-deployment —
without this validator, bot connects to academic/schedule gRPC with empty
`x-grpc-secret` metadata and every RPC is silently rejected with
UNAUTHENTICATED while the `/health` endpoint still reports UP.

Covers:
  - empty GRPC_SECRET → StartupError
  - missing BOT_TOKEN (placeholder) → StartupError
  - both populated → no error
  - multi-field missing → error lists every missing field
"""

import pytest

from bot.config import Settings, StartupError, validate_startup_config

# M08 G8 suite marker — every test in this module belongs to the
# SecurityContracts group (selectable via `pytest -m security_contract`).
pytestmark = pytest.mark.security_contract


def _make_settings(**overrides) -> Settings:
    """Build a Settings instance with only explicit overrides.

    BaseSettings normally reads env vars + .env. For isolation, we build
    via the model_validate path with an empty env lookup by passing
    `_env_file=None` via model_config is noisy — easier to just
    instantiate Settings() and mutate the attributes we care about.
    """
    s = Settings(_env_file=None)
    s.grpc_secret = "valid-secret"
    s.bot_token = "valid-bot-token"
    for k, v in overrides.items():
        setattr(s, k, v)
    return s


def test_empty_grpc_secret_fails_startup(monkeypatch):
    """Empty GRPC_SECRET kills the process with a clear message."""
    settings = _make_settings(grpc_secret="")

    with pytest.raises(StartupError) as exc_info:
        validate_startup_config(settings)

    assert "GRPC_SECRET" in str(exc_info.value)
    assert "x-grpc-secret" in str(exc_info.value)


def test_placeholder_bot_token_fails_startup():
    """Default placeholder bot_token is not a real token — must fail."""
    settings = _make_settings(bot_token="placeholder")

    with pytest.raises(StartupError) as exc_info:
        validate_startup_config(settings)

    assert "BOT_TOKEN" in str(exc_info.value)


def test_empty_bot_token_fails_startup():
    """Explicitly empty bot_token also fails."""
    settings = _make_settings(bot_token="")

    with pytest.raises(StartupError) as exc_info:
        validate_startup_config(settings)

    assert "BOT_TOKEN" in str(exc_info.value)


def test_all_secrets_populated_passes():
    """No error when every mandatory field is populated."""
    settings = _make_settings()

    # Must not raise
    validate_startup_config(settings)


def test_example_mini_app_web_url_fails_startup():
    """The check-in button must never open Telegram's placeholder Example Domain."""
    settings = _make_settings(mini_app_web_url="https://example.com/")

    with pytest.raises(StartupError) as exc_info:
        validate_startup_config(settings)

    assert "MINI_APP_WEB_URL" in str(exc_info.value)


def test_multiple_missing_fields_reported_together():
    """StartupError lists every missing field, not just the first."""
    settings = _make_settings(grpc_secret="", bot_token="")

    with pytest.raises(StartupError) as exc_info:
        validate_startup_config(settings)

    message = str(exc_info.value)
    assert "GRPC_SECRET" in message
    assert "BOT_TOKEN" in message


def test_startup_error_is_runtime_error():
    """StartupError MUST subclass RuntimeError so generic `except Exception`
    in upstream code still catches it (but nothing in bot/ does — see
    __main__.main() where validate_startup_config is the first call, before
    any try/except)."""
    assert issubclass(StartupError, RuntimeError)


def test_env_var_populates_grpc_secret(monkeypatch):
    """Verify Settings() reads GRPC_SECRET from env — end-to-end guard
    that validate_startup_config runs against the same value Docker
    injects via `GRPC_SECRET: ${GRPC_SECRET}` (docker-compose.prod.yml)."""
    monkeypatch.setenv("GRPC_SECRET", "env-injected-secret")
    monkeypatch.setenv("BOT_TOKEN", "env-injected-token")

    settings = Settings()

    assert settings.grpc_secret == "env-injected-secret"
    assert settings.bot_token == "env-injected-token"
    # And the validator accepts it
    validate_startup_config(settings)
