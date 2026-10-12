"""Private admission for /start and /credentials; generic RPC never supplies a password."""

from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.handlers.start import cmd_credentials, cmd_links, cmd_start


def _message(user_id=12345, *, chat_id=12345, chat_type="private", sender=True):
    return SimpleNamespace(
        from_user=SimpleNamespace(id=user_id) if sender else None,
        chat=SimpleNamespace(id=chat_id, type=chat_type),
        answer=AsyncMock(),
    )


def _profile(found=True, *, changed=False):
    return SimpleNamespace(
        found=found,
        user_id=17,
        display_name="Иван Иванов",
        login="current_login",
        initial_password="generic_ignored_marker",
        password_changed=changed,
        group_name="ИБ-21",
    )


async def _invoke(handler, message, academic, cards=None):
    if handler is cmd_start:
        await handler(message, academic_client=academic, prefs_client=MagicMock(), credential_cards=cards)
    else:
        await handler(message, academic_client=academic, credential_cards=cards)


@pytest.mark.asyncio
@pytest.mark.parametrize("handler", [cmd_start, cmd_credentials])
@pytest.mark.parametrize("scope", ["group", "supergroup", "channel", "missing", "negative", "mismatch", "bool"])
async def test_privacy_guard_precedes_every_profile_or_credential_lookup(handler, scope):
    message = _message(
        user_id=-1 if scope == "negative" else True if scope == "bool" else 12345,
        chat_id=999 if scope == "mismatch" else 12345,
        chat_type=scope if scope in {"group", "supergroup", "channel"} else "private",
        sender=scope != "missing",
    )
    academic = SimpleNamespace(get_user_by_telegram_id=AsyncMock())
    cards = SimpleNamespace(deliver=AsyncMock())
    await _invoke(handler, message, academic, cards)
    academic.get_user_by_telegram_id.assert_not_awaited()
    cards.deliver.assert_not_awaited()
    assert message.answer.await_count == 1


@pytest.mark.asyncio
@pytest.mark.parametrize("handler", [cmd_start, cmd_credentials])
async def test_unknown_binding_never_requests_credentials(handler):
    message = _message()
    academic = SimpleNamespace(get_user_by_telegram_id=AsyncMock(return_value=_profile(False)))
    cards = SimpleNamespace(deliver=AsyncMock())
    await _invoke(handler, message, academic, cards)
    cards.deliver.assert_not_awaited()
    assert "Аккаунт не найден" in message.answer.await_args.args[0]


@pytest.mark.asyncio
@pytest.mark.parametrize("handler", [cmd_start, cmd_credentials])
async def test_private_bound_handler_uses_only_dedicated_card_service(handler):
    message = _message()
    academic = SimpleNamespace(get_user_by_telegram_id=AsyncMock(return_value=_profile()))
    cards = SimpleNamespace(deliver=AsyncMock(return_value=True))
    await _invoke(handler, message, academic, cards)
    academic.get_user_by_telegram_id.assert_awaited_once_with(12345)
    cards.deliver.assert_awaited_once()
    message.answer.assert_not_awaited()


@pytest.mark.asyncio
@pytest.mark.parametrize("handler", [cmd_start, cmd_credentials])
async def test_generic_profile_marker_never_rendered_without_capability(handler):
    message = _message()
    academic = SimpleNamespace(get_user_by_telegram_id=AsyncMock(return_value=_profile(changed=True)))
    await _invoke(handler, message, academic)
    text = message.answer.await_args.args[0]
    assert bool("generic_ignored_marker" not in text)
    assert "current_login" in text


@pytest.mark.asyncio
@pytest.mark.parametrize("handler", [cmd_start, cmd_credentials])
async def test_authority_outage_has_safe_reply(handler):
    message = _message()
    academic = SimpleNamespace(get_user_by_telegram_id=AsyncMock(side_effect=RuntimeError("synthetic outage")))
    await _invoke(handler, message, academic)
    assert "Сервис временно недоступен" in message.answer.await_args.args[0]


@pytest.mark.asyncio
async def test_links_retains_site_and_pwa_links():
    message = _message()
    await cmd_links(message)
    text = message.answer.await_args.args[0]
    assert "https://ru.ruttrack.site/app" in text
    assert "https://ruttrack.site/app" in text
    assert "PWA" in text
