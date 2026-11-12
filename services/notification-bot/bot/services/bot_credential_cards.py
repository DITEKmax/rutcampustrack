"""Register nonsecret placeholders before one credential edit; clean exact durable old cards."""

from __future__ import annotations

import asyncio
import html
import logging
import uuid
import weakref

from aiogram.exceptions import TelegramBadRequest

from bot.grpc_client import academic_pb2 as pb
from bot.grpc_client.bot_credentials_client import BotCredentialRequestDenied
from bot.services.notification_prefs import NotificationPreferencesUnavailable

logger = logging.getLogger(__name__)

_PROVIDER_TIMEOUT = 5.0
_UNAVAILABLE_CARD_TEXT = "🔐 Начальные данные входа сейчас недоступны."
_SAFE_CARD_TEXT = "🔐 Прежние данные входа удалены. Используйте свой текущий пароль."
_RENDER_STATES = frozenset(
    {
        pb.BOT_CREDENTIAL_RENDER_STATE_IN_FLIGHT,
        pb.BOT_CREDENTIAL_RENDER_STATE_CONFIRMED_EDIT,
        pb.BOT_CREDENTIAL_RENDER_STATE_NO_EDIT,
        pb.BOT_CREDENTIAL_RENDER_STATE_UNCERTAIN,
    }
)


class BotCredentialCards:
    def __init__(self, *, bot, client) -> None:
        self._bot = bot
        self._client = client
        # In-process serialization complements server revisions across instances/restarts.
        self._locks = weakref.WeakValueDictionary()

    def _lock(self, chat_id, message_id):
        key = (chat_id, message_id)
        lock = self._locks.get(key)
        if lock is None:
            lock = asyncio.Lock()
            self._locks[key] = lock
        return lock

    async def deliver(self, message, profile, *, intro="", footer="") -> bool:
        from bot.handlers.start import private_sender

        chat_id = private_sender(message)
        user_id = getattr(profile, "user_id", None)
        if chat_id is None or type(user_id) is not int or user_id <= 0:
            return False
        if getattr(self._client, "configured", True) is False:
            # Ordinary redacted profiles remain usable with legacy bot configuration.
            # Cleanup never takes this fallback: known old card metadata must stay pending.
            return False
        stage = "snapshot"
        try:
            try:
                snapshot = await self._client.read(chat_id, user_id)
            except BotCredentialRequestDenied:
                return False
            if not self._eligible(snapshot, user_id):
                return False
            binding_version, credentials_version = snapshot.telegram_binding_version, snapshot.credentials_version
            # Do not retain the initial snapshot's password during placeholder publication.
            snapshot = None
            stage = "placeholder"
            async with asyncio.timeout(_PROVIDER_TIMEOUT):
                placeholder = await message.answer("🔐 Готовим ваши данные для входа…", parse_mode="HTML")
            message_id = getattr(placeholder, "message_id", None)
            returned_chat = getattr(getattr(placeholder, "chat", None), "id", None)
            if type(message_id) is not int or message_id <= 0 or returned_chat != chat_id:
                raise NotificationPreferencesUnavailable("Credential placeholder receipt unavailable")
        except Exception:
            logger.warning("Credential card delivery unavailable (stage=%s)", stage)
            raise NotificationPreferencesUnavailable("Credential card delivery unavailable") from None
        return await self._register_and_render(
            chat_id, message_id, user_id, binding_version, credentials_version, intro=intro, footer=footer
        )

    async def _register_and_render(
        self, chat_id, message_id, user_id, binding_version, credentials_version, *, intro, footer
    ):
        # This invocation has the receipt for a new, non-secret Telegram message.
        discard_placeholder = True
        try:
            try:
                registered = await self._client.register(
                    user_id=user_id,
                    chat_id=chat_id,
                    message_id=message_id,
                    binding_version=binding_version,
                    credentials_version=credentials_version,
                )
            except BotCredentialRequestDenied:
                return False
            if registered.disposition == pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_ALREADY_REGISTERED:
                discard_placeholder = False
                return False  # A replay cannot obtain another render permit or modify the existing card.
            if registered.disposition != pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_REGISTERED:
                raise NotificationPreferencesUnavailable("Credential render permit unavailable")
            reference = self._one_reference(registered)
            if (
                reference.user_id != user_id
                or reference.private_chat_id != chat_id
                or reference.message_id != message_id
                or reference.telegram_binding_version != binding_version
                or reference.credentials_version != credentials_version
                or reference.render_state != pb.BOT_CREDENTIAL_RENDER_STATE_IN_FLIGHT
                or reference.cleanup_state != pb.BOT_CREDENTIAL_CLEANUP_STATE_NOT_REQUIRED
                or reference.invalidated_credentials_version != 0
            ):
                raise NotificationPreferencesUnavailable("Credential render permit does not match placeholder")
            # The render routine owns settlement and disposal from this point.
            discard_placeholder = False
            return await self._render(reference, intro=intro, footer=footer)
        except Exception:
            if discard_placeholder:
                logger.warning("Credential card delivery unavailable (stage=register)")
                raise NotificationPreferencesUnavailable("Credential render permit unavailable") from None
            raise
        finally:
            if discard_placeholder:
                await self._discard_placeholder(chat_id, message_id)

    async def _render(self, reference, *, intro, footer):
        chat_id, message_id, user_id = reference.private_chat_id, reference.message_id, reference.user_id
        binding_version, credentials_version = reference.telegram_binding_version, reference.credentials_version
        async with self._lock(chat_id, message_id):
            outcome = pb.BOT_CREDENTIAL_RENDER_OUTCOME_NO_EDIT
            failed = cancelled = False
            stage = "credential_recheck"
            fresh = text = None
            try:
                fresh = await self._client.read(
                    chat_id, user_id, binding_version=binding_version, credentials_version=credentials_version
                )
                if (
                    self._eligible(fresh, user_id)
                    and fresh.telegram_binding_version == binding_version
                    and fresh.credentials_version == credentials_version
                ):
                    stage = "render"
                    text = (
                        f"{intro}🔐 <b>Данные для входа</b>\n\n"
                        f"Логин: <code>{html.escape(fresh.login)}</code>\n"
                        f"Пароль: <code>{html.escape(fresh.initial_password)}</code>\n\n"
                        f"После первого входа смените пароль.{footer}"
                    )
                    # From this point any unknown reply/cancellation is an unresolved provider edit.
                    outcome = pb.BOT_CREDENTIAL_RENDER_OUTCOME_UNCERTAIN
                    async with asyncio.timeout(_PROVIDER_TIMEOUT):
                        edited = await self._bot.edit_message_text(
                            chat_id=chat_id, message_id=message_id, text=text, parse_mode="HTML"
                        )
                    if edited is not None and edited is not False:
                        outcome = pb.BOT_CREDENTIAL_RENDER_OUTCOME_CONFIRMED_EDIT
                    else:
                        failed = True
            except BotCredentialRequestDenied:
                pass
            except TelegramBadRequest as error:
                if stage == "render" and outcome == pb.BOT_CREDENTIAL_RENDER_OUTCOME_UNCERTAIN:
                    description = error.message.lower().removeprefix("bad request: ")
                    if description.startswith(
                        ("message can't be edited", "message to edit not found", "can't parse entities")
                    ):
                        outcome = pb.BOT_CREDENTIAL_RENDER_OUTCOME_NO_EDIT
                failed = True
            except asyncio.CancelledError:
                cancelled = True
            except Exception:
                failed = True
            finally:
                fresh = text = None

            if failed:
                logger.warning("Credential card delivery unavailable (stage=%s)", stage)
            discard_placeholder = outcome == pb.BOT_CREDENTIAL_RENDER_OUTCOME_NO_EDIT
            try:
                settled = await self._client.complete_render(reference, outcome)
                disposition = settled.disposition
                settled_reference = self._one_reference(settled)
                self._require_same_card(reference, settled_reference)
                if disposition == pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_CLEANUP_REQUIRED:
                    await self._cleanup_card_unlocked(settled_reference, str(uuid.uuid4()))
                    discard_placeholder = False
                elif disposition == pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_CLEANUP_COMPLETED:
                    discard_placeholder = False
                elif disposition != pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_RENDER_COMPLETED:
                    raise NotificationPreferencesUnavailable("Credential render receipt unavailable")
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.warning("Credential card delivery unavailable (stage=settle)")
                if cancelled:
                    raise asyncio.CancelledError() from None
                raise NotificationPreferencesUnavailable("Credential render recovery pending") from None
            finally:
                if discard_placeholder:
                    await self._discard_placeholder(chat_id, message_id)
            if cancelled:
                raise asyncio.CancelledError()
            if failed:
                raise NotificationPreferencesUnavailable("Credential provider outcome unresolved") from None
            return (
                outcome == pb.BOT_CREDENTIAL_RENDER_OUTCOME_CONFIRMED_EDIT
                and disposition == pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_RENDER_COMPLETED
            )

    async def _discard_placeholder(self, chat_id, message_id):
        if await self._remove_card(chat_id, message_id, safe_text=_UNAVAILABLE_CARD_TEXT) is None:
            logger.warning("Credential card delivery unavailable (stage=discard_placeholder)")

    async def cleanup(self, *, user_id: int, credentials_version: int, event_id: str) -> None:
        cursor = 0
        try:
            while True:
                page = await self._client.invalidated(
                    user_id=user_id,
                    credentials_version=credentials_version,
                    event_id=event_id,
                    after_record_id=cursor,
                )
                if page.disposition != pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_INVALIDATED_LIST:
                    raise NotificationPreferencesUnavailable("Credential cleanup list unavailable")
                previous = cursor
                for reference in page.messages:
                    self._validate_reference(reference)
                    if (
                        reference.user_id != user_id
                        or reference.record_id <= previous
                        or not 0 < reference.invalidated_credentials_version <= credentials_version
                        or reference.cleanup_state != pb.BOT_CREDENTIAL_CLEANUP_STATE_PENDING
                    ):
                        raise NotificationPreferencesUnavailable("Credential cleanup list identity invalid")
                    previous = reference.record_id
                    async with self._lock(reference.private_chat_id, reference.message_id):
                        await self._cleanup_card_unlocked(reference, event_id)
                next_cursor = page.next_after_record_id
                if next_cursor == 0:
                    return
                if not page.messages or next_cursor != previous or next_cursor <= cursor:
                    raise NotificationPreferencesUnavailable("Credential cleanup pagination invalid")
                cursor = next_cursor
        except asyncio.CancelledError:
            raise
        except Exception:
            # Existing consumer releases the claim and retries; exact metadata remains PENDING.
            raise NotificationPreferencesUnavailable("Credential cleanup recovery pending") from None

    async def _cleanup_card_unlocked(self, reference, event_id: str) -> None:
        self._validate_reference(reference)
        if (
            reference.cleanup_state != pb.BOT_CREDENTIAL_CLEANUP_STATE_PENDING
            or reference.invalidated_credentials_version < reference.credentials_version
        ):
            raise NotificationPreferencesUnavailable("Credential cleanup receipt invalid")
        can_sanitize = reference.render_state in {
            pb.BOT_CREDENTIAL_RENDER_STATE_CONFIRMED_EDIT,
            pb.BOT_CREDENTIAL_RENDER_STATE_NO_EDIT,
        }
        outcome = await self._remove_card(
            reference.private_chat_id, reference.message_id, safe_text=_SAFE_CARD_TEXT if can_sanitize else None
        )
        if outcome is None:
            raise NotificationPreferencesUnavailable("Credential sanitation provider unavailable")
        completed = await self._client.complete_cleanup(reference, event_id=event_id, outcome=outcome)
        if completed.disposition != pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_CLEANUP_COMPLETED:
            raise NotificationPreferencesUnavailable("Credential cleanup completion unavailable")
        completed_reference = self._one_reference(completed)
        self._require_same_card(reference, completed_reference)
        if completed_reference.cleanup_state != pb.BOT_CREDENTIAL_CLEANUP_STATE_CLEANED:
            raise NotificationPreferencesUnavailable("Credential cleanup durable receipt incomplete")

    async def _remove_card(self, chat_id, message_id, *, safe_text):
        try:
            async with asyncio.timeout(_PROVIDER_TIMEOUT):
                if await self._bot.delete_message(chat_id=chat_id, message_id=message_id) is True:
                    return pb.BOT_CREDENTIAL_CLEANUP_OUTCOME_DELETED
        except TelegramBadRequest as error:
            if "message to delete not found" in error.message.lower():
                return pb.BOT_CREDENTIAL_CLEANUP_OUTCOME_ABSENT
        except Exception:
            pass
        # An uncertain late credential edit could overwrite an in-place sanitation.
        if safe_text is None:
            return None
        try:
            async with asyncio.timeout(_PROVIDER_TIMEOUT):
                sanitized = await self._bot.edit_message_text(
                    chat_id=chat_id, message_id=message_id, text=safe_text, parse_mode=None
                )
            if sanitized is not None and sanitized is not False:
                return pb.BOT_CREDENTIAL_CLEANUP_OUTCOME_SANITIZED
        except TelegramBadRequest as error:
            description = error.message.lower()
            if "message to edit not found" in description:
                return pb.BOT_CREDENTIAL_CLEANUP_OUTCOME_ABSENT
            if "message is not modified" in description:
                return pb.BOT_CREDENTIAL_CLEANUP_OUTCOME_SANITIZED
        except Exception:
            pass
        return None

    async def notify_change(self, *, user_id, credentials_version, binding_version, telegram_id) -> None:
        if telegram_id is None:
            return
        try:
            fresh = await self._client.read(
                telegram_id, user_id, binding_version=binding_version, credentials_version=credentials_version
            )
        except BotCredentialRequestDenied:
            return
        if (
            not fresh.found
            or fresh.user_id != user_id
            or fresh.credentials_version != credentials_version
            or fresh.telegram_binding_version != binding_version
        ):
            return
        fresh = None
        try:
            async with asyncio.timeout(_PROVIDER_TIMEOUT):
                await self._bot.send_message(
                    chat_id=telegram_id,
                    text=(
                        "🔐 Пароль RutCampusTrack изменён. Если вы не меняли пароль, немедленно обратитесь в поддержку."
                    ),
                )
        except asyncio.CancelledError:
            raise
        except Exception:
            raise NotificationPreferencesUnavailable("Password notice provider unavailable") from None

    @staticmethod
    def _eligible(snapshot, user_id):
        return (
            snapshot.found
            and snapshot.eligible
            and snapshot.user_id == user_id
            and not snapshot.password_changed
            and bool(snapshot.login)
            and bool(snapshot.initial_password)
            and snapshot.telegram_binding_version > 0
            and snapshot.credentials_version > 0
        )

    @classmethod
    def _one_reference(cls, response):
        if len(response.messages) != 1:
            raise NotificationPreferencesUnavailable("Exact credential card receipt required")
        reference = response.messages[0]
        cls._validate_reference(reference)
        return reference

    @staticmethod
    def _validate_reference(reference):
        if (
            any(
                type(value) is not int or value <= 0
                for value in (
                    reference.record_id,
                    reference.user_id,
                    reference.private_chat_id,
                    reference.message_id,
                    reference.telegram_binding_version,
                    reference.credentials_version,
                    reference.record_revision,
                    reference.render_revision,
                )
            )
            or reference.render_state not in _RENDER_STATES
            or reference.cleanup_state
            not in {
                pb.BOT_CREDENTIAL_CLEANUP_STATE_NOT_REQUIRED,
                pb.BOT_CREDENTIAL_CLEANUP_STATE_PENDING,
                pb.BOT_CREDENTIAL_CLEANUP_STATE_CLEANED,
            }
        ):
            raise NotificationPreferencesUnavailable("Credential card metadata invalid")

    @staticmethod
    def _require_same_card(original, returned):
        if any(
            getattr(original, field) != getattr(returned, field)
            for field in (
                "record_id",
                "user_id",
                "private_chat_id",
                "message_id",
                "telegram_binding_version",
                "credentials_version",
                "render_revision",
            )
        ):
            raise NotificationPreferencesUnavailable("Credential card receipt identity changed")
