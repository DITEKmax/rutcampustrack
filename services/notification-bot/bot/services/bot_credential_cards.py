"""Register nonsecret placeholders before one credential edit; clean exact durable old cards."""

from __future__ import annotations

import asyncio
import html
import uuid
import weakref

from aiogram.exceptions import TelegramBadRequest

from bot.grpc_client import academic_pb2 as pb
from bot.grpc_client.bot_credentials_client import BotCredentialRequestDenied
from bot.services.notification_prefs import NotificationPreferencesUnavailable

_PROVIDER_TIMEOUT = 5.0
_SAFE_CARD_TEXT = "🔐 Прежние данные входа удалены. Используй свой текущий пароль."
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

    async def deliver(self, message, profile, *, intro="", footer="", reply_markup=None) -> bool:
        from bot.handlers.start import private_sender

        chat_id = private_sender(message)
        user_id = getattr(profile, "user_id", None)
        if chat_id is None or type(user_id) is not int or user_id <= 0:
            return False
        if getattr(self._client, "configured", True) is False:
            # Ordinary redacted profiles remain usable with legacy bot configuration.
            # Cleanup never takes this fallback: known old card metadata must stay pending.
            return False
        try:
            snapshot = await self._client.read(chat_id, user_id)
        except BotCredentialRequestDenied:
            return False
        if not self._eligible(snapshot, user_id):
            return False
        binding_version, credentials_version = snapshot.telegram_binding_version, snapshot.credentials_version
        # Do not retain the initial snapshot's password while sending/registering the placeholder.
        snapshot = None
        try:
            async with asyncio.timeout(_PROVIDER_TIMEOUT):
                placeholder = await message.answer(
                    "🔐 Готовим твои данные для входа…", reply_markup=reply_markup, parse_mode="HTML"
                )
        except asyncio.CancelledError:
            raise
        except Exception:
            raise NotificationPreferencesUnavailable("Credential placeholder provider unavailable") from None
        message_id = getattr(placeholder, "message_id", None)
        returned_chat = getattr(getattr(placeholder, "chat", None), "id", None)
        if type(message_id) is not int or message_id <= 0 or returned_chat != chat_id:
            raise NotificationPreferencesUnavailable("Credential placeholder receipt unavailable")
        try:
            registered = await self._client.register(
                user_id=user_id, chat_id=chat_id, message_id=message_id,
                binding_version=binding_version, credentials_version=credentials_version,
            )
        except BotCredentialRequestDenied:
            return False  # This exact placeholder has never contained credentials.
        if registered.disposition == pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_ALREADY_REGISTERED:
            return False  # A replay cannot obtain another render permit.
        if registered.disposition != pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_REGISTERED:
            raise NotificationPreferencesUnavailable("Credential render permit unavailable")
        reference = self._one_reference(registered)
        if (
            reference.user_id != user_id or reference.private_chat_id != chat_id
            or reference.message_id != message_id or reference.telegram_binding_version != binding_version
            or reference.credentials_version != credentials_version
            or reference.render_state != pb.BOT_CREDENTIAL_RENDER_STATE_IN_FLIGHT
            or reference.cleanup_state != pb.BOT_CREDENTIAL_CLEANUP_STATE_NOT_REQUIRED
            or reference.invalidated_credentials_version != 0
        ):
            raise NotificationPreferencesUnavailable("Credential render permit does not match placeholder")

        async with self._lock(chat_id, message_id):
            outcome = pb.BOT_CREDENTIAL_RENDER_OUTCOME_NO_EDIT
            failed = cancelled = False
            fresh = text = None
            try:
                fresh = await self._client.read(
                    chat_id, user_id, binding_version=binding_version, credentials_version=credentials_version
                )
                if (self._eligible(fresh, user_id)
                        and fresh.telegram_binding_version == binding_version
                        and fresh.credentials_version == credentials_version):
                    text = (
                        f"{intro}🔐 <b>Данные для входа</b>\n\n"
                        f"Логин: <code>{html.escape(fresh.login)}</code>\n"
                        f"Пароль: <code>{html.escape(fresh.initial_password)}</code>\n\n"
                        f"После первого входа смени пароль.{footer}"
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
            except asyncio.CancelledError:
                cancelled = True
            except Exception:
                failed = True
            finally:
                fresh = text = None

            try:
                settled = await self._client.complete_render(reference, outcome)
                disposition = settled.disposition
                settled_reference = self._one_reference(settled)
                self._require_same_card(reference, settled_reference)
                if disposition == pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_CLEANUP_REQUIRED:
                    await self._cleanup_card_unlocked(settled_reference, str(uuid.uuid4()))
                elif disposition not in {
                    pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_RENDER_COMPLETED,
                    pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_CLEANUP_COMPLETED,
                }:
                    raise NotificationPreferencesUnavailable("Credential render receipt unavailable")
            except asyncio.CancelledError:
                raise
            except Exception:
                if cancelled:
                    raise asyncio.CancelledError() from None
                raise NotificationPreferencesUnavailable("Credential render recovery pending") from None
            if cancelled:
                raise asyncio.CancelledError()
            if failed:
                raise NotificationPreferencesUnavailable("Credential provider outcome unresolved") from None
            return (
                outcome == pb.BOT_CREDENTIAL_RENDER_OUTCOME_CONFIRMED_EDIT
                and disposition == pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_RENDER_COMPLETED
            )

    async def cleanup(self, *, user_id: int, credentials_version: int, event_id: str) -> None:
        cursor = 0
        try:
            while True:
                page = await self._client.invalidated(
                    user_id=user_id, credentials_version=credentials_version,
                    event_id=event_id, after_record_id=cursor,
                )
                if page.disposition != pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_INVALIDATED_LIST:
                    raise NotificationPreferencesUnavailable("Credential cleanup list unavailable")
                previous = cursor
                for reference in page.messages:
                    self._validate_reference(reference)
                    if (
                        reference.user_id != user_id or reference.record_id <= previous
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
        outcome = None
        try:
            async with asyncio.timeout(_PROVIDER_TIMEOUT):
                removed = await self._bot.delete_message(
                    chat_id=reference.private_chat_id, message_id=reference.message_id
                )
            if removed is True:
                outcome = pb.BOT_CREDENTIAL_CLEANUP_OUTCOME_DELETED
        except TelegramBadRequest as error:
            if "message to delete not found" in error.message.lower():
                outcome = pb.BOT_CREDENTIAL_CLEANUP_OUTCOME_ABSENT
        except asyncio.CancelledError:
            raise
        except Exception:
            pass
        if outcome is None:
            if reference.render_state not in {
                pb.BOT_CREDENTIAL_RENDER_STATE_CONFIRMED_EDIT, pb.BOT_CREDENTIAL_RENDER_STATE_NO_EDIT
            }:
                raise NotificationPreferencesUnavailable("Unresolved credential render requires exact deletion")
            try:
                async with asyncio.timeout(_PROVIDER_TIMEOUT):
                    sanitized = await self._bot.edit_message_text(
                        chat_id=reference.private_chat_id, message_id=reference.message_id,
                        text=_SAFE_CARD_TEXT, parse_mode=None,
                    )
                if sanitized is not None and sanitized is not False:
                    outcome = pb.BOT_CREDENTIAL_CLEANUP_OUTCOME_SANITIZED
            except TelegramBadRequest as error:
                description = error.message.lower()
                if "message to edit not found" in description:
                    outcome = pb.BOT_CREDENTIAL_CLEANUP_OUTCOME_ABSENT
                elif "message is not modified" in description:
                    outcome = pb.BOT_CREDENTIAL_CLEANUP_OUTCOME_SANITIZED
            except asyncio.CancelledError:
                raise
            except Exception:
                pass
        if outcome is None:
            raise NotificationPreferencesUnavailable("Credential sanitation provider unavailable")
        completed = await self._client.complete_cleanup(reference, event_id=event_id, outcome=outcome)
        if completed.disposition != pb.BOT_CREDENTIAL_MESSAGE_DISPOSITION_CLEANUP_COMPLETED:
            raise NotificationPreferencesUnavailable("Credential cleanup completion unavailable")
        completed_reference = self._one_reference(completed)
        self._require_same_card(reference, completed_reference)
        if completed_reference.cleanup_state != pb.BOT_CREDENTIAL_CLEANUP_STATE_CLEANED:
            raise NotificationPreferencesUnavailable("Credential cleanup durable receipt incomplete")

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
            not fresh.found or fresh.user_id != user_id
            or fresh.credentials_version != credentials_version
            or fresh.telegram_binding_version != binding_version
        ):
            return
        fresh = None
        try:
            async with asyncio.timeout(_PROVIDER_TIMEOUT):
                await self._bot.send_message(
                    chat_id=telegram_id,
                    text="🔐 Пароль RutCampusTrack изменён. Если это сделал не ты, немедленно обратись в поддержку.",
                )
        except asyncio.CancelledError:
            raise
        except Exception:
            raise NotificationPreferencesUnavailable("Password notice provider unavailable") from None

    @staticmethod
    def _eligible(snapshot, user_id):
        return (
            snapshot.found and snapshot.eligible and snapshot.user_id == user_id
            and not snapshot.password_changed and bool(snapshot.login) and bool(snapshot.initial_password)
            and snapshot.telegram_binding_version > 0 and snapshot.credentials_version > 0
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
            any(type(value) is not int or value <= 0 for value in (
                reference.record_id, reference.user_id, reference.private_chat_id, reference.message_id,
                reference.telegram_binding_version, reference.credentials_version,
                reference.record_revision, reference.render_revision,
            )) or reference.render_state not in _RENDER_STATES
            or reference.cleanup_state not in {
                pb.BOT_CREDENTIAL_CLEANUP_STATE_NOT_REQUIRED,
                pb.BOT_CREDENTIAL_CLEANUP_STATE_PENDING,
                pb.BOT_CREDENTIAL_CLEANUP_STATE_CLEANED,
            }
        ):
            raise NotificationPreferencesUnavailable("Credential card metadata invalid")

    @staticmethod
    def _require_same_card(original, returned):
        if any(getattr(original, field) != getattr(returned, field) for field in (
            "record_id", "user_id", "private_chat_id", "message_id", "telegram_binding_version",
            "credentials_version", "render_revision",
        )):
            raise NotificationPreferencesUnavailable("Credential card receipt identity changed")
