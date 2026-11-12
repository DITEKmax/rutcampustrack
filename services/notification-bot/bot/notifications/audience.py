"""Central Academic notification authority and broker-owned Telegram delivery."""

from __future__ import annotations

import logging
from datetime import date, datetime
from html import escape
from uuid import UUID, uuid4

import grpc
from aiogram.exceptions import (
    TelegramBadRequest,
    TelegramForbiddenError,
    TelegramNetworkError,
    TelegramRetryAfter,
    TelegramServerError,
)
from aiogram.types import InlineKeyboardButton, InlineKeyboardMarkup
from redis.exceptions import ConnectionError as RedisConnectionError
from redis.exceptions import TimeoutError as RedisTimeoutError

from bot.grpc_client import academic_pb2 as pb
from bot.grpc_client import attendance_pb2
from bot.notifications.headman_alerts import request_facts
from bot.notifications.presentation import digest_pages, message_options, private_request_event, render
from bot.services.notification_prefs import (
    BUSINESS_EVENTS,
    NotificationPreferencesUnavailable,
    category_for_event,
    type_for_event,
)
from bot.services.redis_client import ReminderLifecycleUnavailable
from bot.services.send_queue import RecipientSuppressed, SendTask

logger = logging.getLogger(__name__)

ROLES = frozenset({"STUDENT", "HEADMAN", "TEACHER", "ADMIN"})
RETENTION = 7 * 24 * 60 * 60
# Provider exhaustion retains the original body in the seven-day DLQ. Allow
# additional time for partial delivery and retry processing before it enters the DLQ.
_DIGEST_PROGRESS_RETENTION = 14 * 24 * 60 * 60
_PERSONAL = {
    "attendance.marked",
    "excuse.requested",
    "excuse.decided",
    "late_checkin.requested",
    "late_checkin.decided",
    "homework.weekly_digest",
    "homework.due_reminder",
    "group.membership.changed",
    "headman.access.changed",
    "assistant.access.changed",
    "admin.access.changed",
}
_OWN_SYNC = {
    "lesson.cancelled",
    "lesson.one_off.created",
    "lesson.one_off.cancelled",
    "lesson.restored",
    "homework.published",
    "homework.updated",
    "homework.withdrawn",
    "group.renamed",
    "group.archived",
    "semester.activated",
}
_HEADMAN_STUDENT_SELF_EVENTS = {
    "lesson.cancelled",
    "homework.published",
    "homework.updated",
}
_HEADMAN_STUDENT_ROLES = {"STUDENT", "HEADMAN"}
_REQUESTS = {"excuse.requested", "excuse.decided", "late_checkin.requested", "late_checkin.decided"}
_CHECKIN_REMINDERS = {"lesson.started", "lesson.reminder"}
_FINAL_MARKS = {"present", "absent", "excused", "free_attendance"}
_TEACHER_EVENTS = {
    "lesson.cancelled",
    "lesson.one_off.created",
    "lesson.one_off.cancelled",
    "lesson.transferred",
    "lesson.restored",
    "lesson.updated",
    "homework.published",
    "homework.updated",
    "homework.withdrawn",
}
_SCOPE_FIELDS = ("group_id", "subject_id", "semester_id", "lesson_type", "lesson_date")


def positive(value):
    return type(value) is int and 0 < value <= 2**63 - 1


def validate_audience(recipients):
    result = {}
    for recipient in recipients:
        user_id = recipient.user_id
        roles = list(recipient.roles)
        if (
            not positive(user_id)
            or user_id in result
            or not roles
            or len(roles) != len(set(roles))
            or not set(roles) <= ROLES
        ):
            raise NotificationPreferencesUnavailable("Invalid central notification recipients")
        result[user_id] = frozenset(roles)
    return result


def audience_request(event):
    kind, payload = event.get("event_type"), event.get("payload")
    if kind not in BUSINESS_EVENTS or not isinstance(payload, dict) or type_for_event(event) is None:
        raise NotificationPreferencesUnavailable("Unknown business notification context")
    occurred_at = event.get("occurred_at")
    try:
        parsed = datetime.fromisoformat(occurred_at.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            raise ValueError
        UUID(event["event_id"])
    except (ValueError, TypeError, AttributeError, KeyError) as error:
        raise NotificationPreferencesUnavailable("Invalid notification identity/time") from error
    request = pb.NotificationAudienceRequest(
        request_id=str(uuid4()),
        event_type=kind,
        occurred_at=occurred_at,
    )
    group_id = payload.get("group_id")
    if group_id is not None:
        if not positive(group_id):
            raise NotificationPreferencesUnavailable("Invalid notification group")
        request.group_id = group_id
    if kind in _PERSONAL:
        user_id = payload.get("target_user_id" if kind == "admin.access.changed" else "user_id")
        if not positive(user_id):
            raise NotificationPreferencesUnavailable("Invalid notification affected user")
        request.affected_user_id = user_id
    if kind == "semester.operation.completed":
        actor = payload.get("actor_user_id")
        if not positive(actor):
            raise NotificationPreferencesUnavailable("Unknown semester operation actor")
        request.affected_user_id = actor
    if kind in {"excuse.decided", "late_checkin.decided"}:
        # Actual schema status; no inferred decision/caller permission override.
        request.request_outcome = payload["status"]
    snapshots = (
        [payload.get("source"), payload.get("target")]
        if kind in {"lesson.transferred", "lesson.updated"}
        else [{**payload, **payload.get("homework", {})}]
        if isinstance(payload.get("homework"), dict)
        else [payload]
    )
    incomplete = False
    for snapshot in snapshots if kind in _TEACHER_EVENTS else []:
        if not isinstance(snapshot, dict):
            raise NotificationPreferencesUnavailable("Missing factual notification snapshot")
        # Reject malformed provided values even when a legacy tuple is incomplete.
        for key in _SCOPE_FIELDS[:3]:
            if snapshot.get(key) is not None and not positive(snapshot[key]):
                raise NotificationPreferencesUnavailable("Invalid partial notification scope")
        if snapshot.get("lesson_type") is not None and snapshot["lesson_type"] not in {
            "lecture",
            "practice",
            "lab",
            "LECTURE",
            "PRACTICE",
            "LAB",
        }:
            raise NotificationPreferencesUnavailable("Invalid partial notification type")
        if snapshot.get("lesson_date") is not None:
            try:
                if date.fromisoformat(snapshot["lesson_date"]).isoformat() != snapshot["lesson_date"]:
                    raise ValueError
            except (ValueError, TypeError) as error:
                raise NotificationPreferencesUnavailable("Invalid partial notification date") from error
        # Legacy facts without a complete tuple never acquire a teacher audience.
        if not all(snapshot.get(key) is not None for key in _SCOPE_FIELDS):
            incomplete = True
            continue
        try:
            if not all(positive(snapshot[key]) for key in _SCOPE_FIELDS[:3]):
                raise ValueError
            if snapshot["lesson_type"] not in {"lecture", "practice", "lab", "LECTURE", "PRACTICE", "LAB"}:
                raise ValueError
            if date.fromisoformat(snapshot["lesson_date"]).isoformat() != snapshot["lesson_date"]:
                raise ValueError
            values = {key: snapshot[key] for key in _SCOPE_FIELDS}
            if snapshot.get("assignment_id") is not None:
                if not positive(snapshot["assignment_id"]):
                    raise ValueError
                values["assignment_id"] = snapshot["assignment_id"]
            request.lesson_scopes.add(**values)
        except (ValueError, TypeError) as error:
            raise NotificationPreferencesUnavailable("Invalid factual notification scope") from error
    if kind in {"lesson.transferred", "lesson.updated"}:
        primary_group = snapshots[0].get("group_id")
        if not positive(primary_group):
            raise NotificationPreferencesUnavailable("Unknown factual notification group")
        request.group_id = primary_group
    if incomplete and kind in {"lesson.transferred", "lesson.updated"}:
        # A partially known change has no teacher projection; resolve each factual group.
        request.ClearField("lesson_scopes")
    return request


def audience_requests(event):
    request = audience_request(event)
    if event["event_type"] not in {"lesson.transferred", "lesson.updated"}:
        return [request]
    groups = set()
    for key in ("source", "target"):
        group_id = event["payload"][key].get("group_id")
        if not positive(group_id):
            raise NotificationPreferencesUnavailable("Unknown factual notification group")
        groups.add(group_id)
    requests = []
    for group_id in sorted(groups):
        projection = pb.NotificationAudienceRequest()
        projection.CopyFrom(request)
        projection.request_id = str(uuid4())
        projection.group_id = group_id
        projection.ClearField("lesson_scopes")
        for scope in request.lesson_scopes:
            if scope.group_id == group_id:
                projection.lesson_scopes.add().CopyFrom(scope)
        requests.append(projection)
    return requests


async def resolve_all(academic, requests):
    combined = {}
    for request in requests:
        for user_id, roles in (await resolve(academic, request)).items():
            combined[user_id] = combined.get(user_id, frozenset()) | roles
    return combined


async def resolve(academic, request):
    try:
        response = await academic.resolve_notification_audience(request)
        if response.request_id != request.request_id or response.event_type != request.event_type:
            raise ValueError("Mismatched notification audience echo")
        return validate_audience(response.recipients)
    except NotificationPreferencesUnavailable:
        raise
    except Exception as error:
        raise NotificationPreferencesUnavailable("Central notification audience unavailable") from error


def prepared_audience(event):
    envelope = event.get("_bot_prepared")
    if (
        not isinstance(envelope, dict)
        or not {"version", "event_id", "event_type", "recipients"} <= set(envelope)
        or set(envelope) - {
            "version", "event_id", "event_type", "recipients", "delivered_user_ids",
            "digest_progress", "reminder_recovery",
        }
        or type(envelope["version"]) is not int
        or envelope["version"] != 1
        or envelope["event_id"] != event.get("event_id")
        or envelope["event_type"] != event.get("event_type")
        or not isinstance(envelope["recipients"], list)
    ):
        raise ValueError("Invalid internal notification envelope")
    recipients = []
    try:
        for item in envelope["recipients"]:
            if (
                not isinstance(item, dict)
                or set(item) != {"user_id", "roles"}
                or not positive(item["user_id"])
                or not isinstance(item["roles"], list)
                or any(not isinstance(role, str) for role in item["roles"])
            ):
                raise ValueError
            recipients.append(pb.NotificationAudienceRecipient(**item))
        audience = validate_audience(recipients)
        delivered = envelope.get("delivered_user_ids", [])
        if (
            not isinstance(delivered, list)
            or any(not positive(uid) for uid in delivered)
            or len(delivered) != len(set(delivered))
            or not set(delivered) <= audience.keys()
        ):
            raise ValueError("Invalid internal delivery progress")
        if "digest_progress" in envelope:
            _digest_progress(event, audience.keys() - set(delivered))
        if "reminder_recovery" in envelope:
            validate_reminder_recovery(event, set(delivered))
        return audience
    except (ValueError, TypeError, NotificationPreferencesUnavailable) as error:
        raise ValueError("Invalid internal notification recipients") from error


def validate_reminder_recovery(event, delivered):
    entries = event["_bot_prepared"]["reminder_recovery"]
    if event["event_type"] not in _CHECKIN_REMINDERS or not isinstance(entries, list) or not entries:
        raise ValueError("Invalid reminder recovery context")
    users = set()
    for entry in entries:
        if (
            not isinstance(entry, dict)
            or set(entry) != {"user_id", "chat_id", "message_id"}
            or not all(positive(value) for value in entry.values())
            or entry["user_id"] not in delivered
            or entry["user_id"] in users
        ):
            raise ValueError("Invalid reminder recovery recipient")
        users.add(entry["user_id"])


def _digest_progress(event, pending_users):
    progress = event["_bot_prepared"]["digest_progress"]
    if event["event_type"] != "homework.weekly_digest" or not isinstance(progress, list) or not progress:
        raise ValueError("Invalid digest progress")
    page_count = len(digest_pages(event))
    by_user = {}
    for entry in progress:
        if not isinstance(entry, dict) or set(entry) != {"user_id", "pages_sent"}:
            raise ValueError("Invalid digest progress entry")
        user_id, pages_sent = entry["user_id"], entry["pages_sent"]
        if (
            not positive(user_id)
            or user_id not in pending_users
            or user_id in by_user
            or not positive(pages_sent)
            or not 1 <= pages_sent <= page_count
            or page_count <= 1
        ):
            raise ValueError("Invalid digest progress receipt")
        by_user[user_id] = pages_sent
    return by_user


async def prepare_business(event, academic):
    """Return factual event plus immutable minimal audience for same-queue atomic replace."""
    requests = audience_requests(event)
    audience = await resolve_all(academic, requests)
    return {
        **event,
        "_bot_prepared": {
            "version": 1,
            "event_id": event["event_id"],
            "event_type": event["event_type"],
            "recipients": [{"user_id": uid, "roles": sorted(roles)} for uid, roles in sorted(audience.items())],
        },
    }


class NotificationDeliveryState:
    """Optional partial-delivery receipts; immutable roles are held by the durable broker envelope."""

    def __init__(self, redis_client):
        self._redis = redis_client

    async def completed(self, event_id, user_id):
        try:
            return await self._redis.get(self._sent_key(event_id, user_id)) == "sent"
        except Exception as error:
            raise NotificationPreferencesUnavailable("Notification receipt unavailable") from error

    async def complete(self, event_id, user_id):
        try:
            await self._redis.set(self._sent_key(event_id, user_id), "sent", ex=RETENTION)
        except Exception as error:
            raise NotificationPreferencesUnavailable("Notification receipt unavailable") from error

    @staticmethod
    def _sent_key(event_id, user_id):
        return f"notification-bot:sent:{event_id}:{user_id}"

    async def complete_request(self, event_id, user_id, *, tracker, kind, request_id, chat_id, message_id):
        # The runtime configures tracking and delivery state on the same Redis.
        args = (kind, request_id, chat_id, message_id, user_id)
        receipt = (self._sent_key(event_id, user_id), RETENTION)
        try:
            try:
                await tracker.add(*args, delivery_receipt=receipt)
            except (RedisConnectionError, RedisTimeoutError):
                # Telegram already sent the card. Retry only the idempotent Redis write.
                await tracker.add(*args, delivery_receipt=receipt)
        except Exception as error:
            raise NotificationPreferencesUnavailable("Request delivery receipt unavailable") from error

    async def digest_pages_sent(self, event_id, user_id, page_count):
        try:
            raw = await self._redis.get(f"notification-bot:digest-pages:{event_id}:{user_id}")
            if raw is None:
                return 0
            count = int(raw)
            if str(count) != raw or not 1 <= count <= page_count:
                raise ValueError("Invalid digest receipt")
            return count
        except Exception as error:
            raise NotificationPreferencesUnavailable("Digest receipt unavailable") from error

    async def record_digest_pages(self, event_id, user_id, count):
        try:
            await self._redis.set(
                f"notification-bot:digest-pages:{event_id}:{user_id}", str(count), ex=_DIGEST_PROGRESS_RETENTION
            )
        except Exception as error:
            raise NotificationPreferencesUnavailable("Digest receipt unavailable") from error

    async def snapshot(self, event):
        """Keep confirmed progress with the durable broker envelope across Redis TTL expiry."""
        audience = prepared_audience(event)
        delivered = set(event["_bot_prepared"].get("delivered_user_ids", []))
        pending = sorted(audience.keys() - delivered)
        if pending:
            try:
                values = await self._redis.mget([self._sent_key(event["event_id"], uid) for uid in pending])
                if len(values) != len(pending) or any(value not in (None, "sent") for value in values):
                    raise ValueError("Invalid notification receipt snapshot")
                delivered.update(uid for uid, value in zip(pending, values) if value == "sent")
            except (RedisConnectionError, RedisTimeoutError) as error:
                if event["event_type"] not in _CHECKIN_REMINDERS:
                    raise NotificationPreferencesUnavailable("Notification receipt snapshot unavailable") from error
                # Every known Telegram response for these events is captured in the
                # in-memory prepared body before any Redis operation.
            except Exception as error:
                raise NotificationPreferencesUnavailable("Notification progress snapshot unavailable") from error
        snapshot = {**event["_bot_prepared"], "delivered_user_ids": sorted(delivered)}
        if event["event_type"] == "homework.weekly_digest":
            saved_progress = {entry["user_id"]: entry["pages_sent"] for entry in snapshot.pop("digest_progress", [])}
            page_count = len(digest_pages(event))
            progress = []
            for uid in sorted(audience.keys() - delivered) if page_count > 1 else ():
                count = max(
                    saved_progress.get(uid, 0), await self.digest_pages_sent(event["event_id"], uid, page_count)
                )
                if count:
                    progress.append({"user_id": uid, "pages_sent": count})
            if progress:
                snapshot["digest_progress"] = progress
        return {**event, "_bot_prepared": snapshot}


async def handle_business(
    event,
    *,
    bot,
    academic,
    queue,
    reminders,
    config,
    state,
    attendance=None,
    tracker=None,
):
    requests = audience_requests(event)
    request = requests[0]
    kind, payload, event_id = event["event_type"], event["payload"], event["event_id"]
    lesson_id = payload.get("lesson_id")
    final_mark = kind == "attendance.marked" and payload.get("status") in _FINAL_MARKS
    if (final_mark or kind in _CHECKIN_REMINDERS) and not positive(lesson_id):
        raise NotificationPreferencesUnavailable("Invalid reminder lesson")
    saved = prepared_audience(event)
    cleanup_failure = None
    if final_mark:
        await reminders.mark_student_marked(lesson_id, payload["user_id"], payload["status"], strict=True)
        try:
            await cleanup_reminders(event, bot=bot, academic=academic, reminders=reminders)
        except (ReminderLifecycleUnavailable, grpc.aio.AioRpcError) as error:
            cleanup_failure = error
    for delivery in tuple(event["_bot_prepared"].get("reminder_recovery", ())):
        await settle_reminder_delivery(event, delivery, bot, reminders, state)
    pending_context = None
    if kind.endswith(".requested") and kind in _REQUESTS:
        if attendance is None:
            raise NotificationPreferencesUnavailable("Canonical request authority unavailable")
        request_id = payload.get("ticket_id" if kind.startswith("excuse.") else "request_id")
        request_kind = (
            attendance_pb2.STUDENT_REQUEST_KIND_EXCUSE
            if kind.startswith("excuse.")
            else attendance_pb2.STUDENT_REQUEST_KIND_LATE_CHECKIN
        )
        try:
            pending_context = await attendance.resolve_request_notification(request_kind, request_id)
            summary = pending_context.detail.summary
            if (
                summary.id != request_id
                or summary.kind != request_kind
                or pending_context.group_id != request.group_id
                or pending_context.student_id != request.affected_user_id
            ):
                raise ValueError
            if summary.status != attendance_pb2.STUDENT_REQUEST_STATUS_PENDING:
                return
        except Exception as error:
            raise NotificationPreferencesUnavailable("Canonical request context unavailable") from error
    current = await resolve_all(academic, requests)
    # Saved event/user role set prevents late grants from joining replay/queue delivery.
    delivered = set(event["_bot_prepared"].get("delivered_user_ids", []))
    tracked = set()
    if kind in {"excuse.decided", "late_checkin.decided"} and tracker is not None:
        tracked = await queue_request_cleanup(
            event,
            bot=bot,
            academic=academic,
            queue=queue,
            attendance=attendance,
            tracker=tracker,
            state=state,
            decision_requests=requests,
            saved=saved,
            delivered=delivered,
            config=config,
        )
    pages = digest_pages(event) if kind == "homework.weekly_digest" else None
    actor = payload.get("actor_user_id")
    for user_id, saved_roles in saved.items():
        roles = saved_roles & current.get(user_id, frozenset())
        if not roles or user_id in delivered or await state.completed(event_id, user_id):
            continue
        if kind == "attendance.marked" and (
            payload.get("marked_by") != "headman" or user_id != payload["user_id"]
        ):
            continue
        own_sync = kind in _OWN_SYNC and positive(actor) and actor == user_id
        self_notice = own_sync and kind in _HEADMAN_STUDENT_SELF_EVENTS and _HEADMAN_STUDENT_ROLES <= roles
        if own_sync and not self_notice:
            continue
        if kind == "group.renamed" or kind == "group.archived":
            if payload.get("notification_cause") == "AUTOMATIC_GROUP_CYCLE":
                roles -= {"ADMIN"}
                if not roles:
                    continue
        if kind in _CHECKIN_REMINDERS and await reminders.is_student_marked(lesson_id, user_id, strict=True):
            continue
        try:
            user = await academic.get_user_by_id(user_id)
            if user.id != user_id:
                raise ValueError
            chat_id = user.telegram_id
            if chat_id == 0:
                continue  # Explicit unbound account, never an unknown authority.
            if not positive(chat_id):
                raise ValueError
        except Exception as error:
            raise NotificationPreferencesUnavailable("Recipient binding unavailable") from error

        if user_id != payload.get("user_id") and (user_id, chat_id) in tracked:
            continue

        live_roles = [roles]

        async def authorized(uid=user_id, retained=roles, role_state=live_roles, require_both=self_notice):
            live = await resolve_all(academic, requests)
            role_state[0] = retained & live.get(uid, frozenset())
            if not role_state[0] or (require_both and not _HEADMAN_STUDENT_ROLES <= role_state[0]):
                return False
            if uid in delivered or await state.completed(event_id, uid):
                return False
            if kind in _CHECKIN_REMINDERS and await reminders.is_student_marked(lesson_id, uid, strict=True):
                return False
            if pending_context is not None:
                try:
                    latest = await attendance.resolve_request_notification(request_kind, request_id)
                    summary = latest.detail.summary
                    if (
                        summary.id != request_id
                        or summary.kind != request_kind
                        or latest.group_id != request.group_id
                        or latest.student_id != request.affected_user_id
                    ):
                        raise ValueError
                    return summary.status == attendance_pb2.STUDENT_REQUEST_STATUS_PENDING
                except Exception as error:
                    raise NotificationPreferencesUnavailable("Current request authority unavailable") from error
            return True

        # One event/user, regardless of how many role projections are authorized.
        display_event = private_request_event(event) if kind in _REQUESTS else event

        async def send(
            chat=chat_id,
            uid=user_id,
            display=display_event,
            check=authorized,
            role_state=live_roles,
        ):
            async def eligible():
                return await queue._eligible(
                    SendTask(
                        send,
                        user_id=uid,
                        chat_id=chat,
                        category=category_for_event(kind),
                        notification_type=type_for_event(event),
                        audience_check=check,
                    )
                )

            role_order = (
                ("HEADMAN", "STUDENT", "TEACHER", "ADMIN")
                if pending_context is not None and uid != request.affected_user_id
                else ("STUDENT", "HEADMAN", "TEACHER", "ADMIN")
            )
            role = next(role for role in role_order if role in role_state[0])
            fallback = None
            facts = None
            if pending_context is not None and role == "HEADMAN" and uid != request.affected_user_id:
                facts = request_facts(pending_context, show_student=True)
                prefix = "ex" if kind.startswith("excuse.") else "lcr"
                fallback = InlineKeyboardMarkup(
                    inline_keyboard=[
                        [
                            InlineKeyboardButton(
                                text="Подтвердить" if prefix == "lcr" else "Одобрить",
                                callback_data=f"{prefix}:approve:{request_id}",
                            ),
                            InlineKeyboardButton(text="Отклонить", callback_data=f"{prefix}:reject:{request_id}"),
                        ]
                    ]
                )
            if pages is not None:
                result = await send_digest(
                    display,
                    pages,
                    bot=bot,
                    chat=chat,
                    user_id=uid,
                    config=config,
                    role=role,
                    queue=queue,
                    eligible=eligible,
                    state=state,
                )
            else:
                result = await bot.send_message(
                    chat_id=chat,
                    **message_options(display, config=config, facts=facts, role=role, fallback_markup=fallback),
                )
            if pending_context is not None and tracker is not None:
                tracking_kind = "excuse" if kind.startswith("excuse.") else "late_checkin"
                await state.complete_request(
                    event_id,
                    uid,
                    tracker=tracker,
                    kind=tracking_kind,
                    request_id=str(request_id),
                    chat_id=chat,
                    message_id=result.message_id,
                )
            elif kind in _CHECKIN_REMINDERS:
                delivery = {"user_id": uid, "chat_id": chat, "message_id": result.message_id}
                prepared = event["_bot_prepared"]
                prepared.setdefault("delivered_user_ids", []).append(uid)
                prepared.setdefault("reminder_recovery", []).append(delivery)
                await settle_reminder_delivery(event, delivery, bot, reminders, state)
            else:
                await state.complete(event_id, uid)
            return result

        await queue.put(
            SendTask(
                send,
                user_id=user_id,
                chat_id=chat_id,
                category=category_for_event(kind),
                notification_type=type_for_event(event),
                audience_check=authorized,
            )
        )

    if cleanup_failure is not None:
        # Retry the original event only after its independent confirmation has a receipt.
        async def report_cleanup_failure():
            raise cleanup_failure

        return report_cleanup_failure
    if kind == "lesson.closed":
        await cleanup_reminders(event, bot=bot, academic=academic, reminders=reminders)
    if kind in _CHECKIN_REMINDERS:
        return lambda: cleanup_reminders(event, bot=bot, academic=academic, reminders=reminders)


async def send_digest(
    event,
    pages,
    *,
    bot,
    chat,
    user_id,
    config,
    role,
    queue,
    eligible,
    state,
):
    if len(pages) == 1:
        return await bot.send_message(
            chat_id=chat, **message_options(event, config=config, role=role, text=escape(pages[0]))
        )

    event_id = event["event_id"]
    # Pages are delivered in order. The broker retains confirmed progress even after Redis expiry.
    saved = next(
        (
            entry["pages_sent"]
            for entry in event["_bot_prepared"].get("digest_progress", [])
            if entry["user_id"] == user_id
        ),
        0,
    )
    start = max(saved, await state.digest_pages_sent(event_id, user_id, len(pages)))
    result = None
    for index in range(start, len(pages)):
        if index != start:
            await queue._consume_token()
        if not await eligible():
            raise RecipientSuppressed
        text = escape(f"{pages[index]}\n\nСтраница {index + 1} из {len(pages)}")
        result = await bot.send_message(chat_id=chat, **message_options(event, config=config, role=role, text=text))
        await state.record_digest_pages(event_id, user_id, index + 1)
    return result


async def queue_request_cleanup(
    event, *, bot, academic, queue, attendance, tracker, state, decision_requests, saved, delivered=(), config=None
):
    """Edit only original cards with canonical identity and current capability."""
    kind, payload = event["event_type"], event["payload"]
    tracking_kind = "excuse" if kind.startswith("excuse.") else "late_checkin"
    request_field = "ticket_id" if tracking_kind == "excuse" else "request_id"
    tracking_id = payload.get(request_field)
    try:
        entries = await tracker.get_all(tracking_kind, str(tracking_id))
    except Exception as error:
        raise NotificationPreferencesUnavailable("Tracked request cards unavailable") from error
    cards = {}
    for entry in entries:
        if all(positive(entry.get(key)) for key in ("user_id", "chat_id", "message_id")):
            cards.setdefault((entry["user_id"], entry["chat_id"]), []).append(entry)
    if not cards:
        return set()
    if attendance is None:
        raise NotificationPreferencesUnavailable("Canonical cleanup authority unavailable")
    request_kind = (
        attendance_pb2.STUDENT_REQUEST_KIND_EXCUSE
        if tracking_kind == "excuse"
        else attendance_pb2.STUDENT_REQUEST_KIND_LATE_CHECKIN
    )
    expected_status = {
        "approved": attendance_pb2.STUDENT_REQUEST_STATUS_APPROVED,
        "rejected": attendance_pb2.STUDENT_REQUEST_STATUS_REJECTED,
        "cancelled": attendance_pb2.STUDENT_REQUEST_STATUS_CANCELLED,
    }[payload["status"]]
    owner = payload.get("user_id")
    capability_event = {
        **event,
        "event_type": f"{tracking_kind}.requested",
        "payload": {"group_id": payload.get("group_id"), "user_id": owner, request_field: tracking_id},
    }
    capability_requests = audience_requests(capability_event)

    async def current_capability(uid):
        try:
            context = await attendance.resolve_request_notification(request_kind, tracking_id)
            summary = context.detail.summary
            if (
                summary.id != tracking_id
                or summary.kind != request_kind
                or context.group_id != payload.get("group_id")
                or context.student_id != owner
                or not positive(context.group_id)
                or not positive(context.student_id)
            ):
                raise ValueError("Mismatched canonical cleanup identity")
            if summary.status != expected_status:
                raise NotificationPreferencesUnavailable("Canonical request outcome not current")
            if uid == owner:
                return context, (await resolve_all(academic, decision_requests)).get(uid, frozenset())
            roles = (await resolve_all(academic, capability_requests)).get(uid, frozenset())
            return context, roles & {"HEADMAN", "STUDENT"}
        except NotificationPreferencesUnavailable:
            raise
        except Exception as error:
            raise NotificationPreferencesUnavailable("Current cleanup authority unavailable") from error

    for (user_id, chat_id), saved_cards in cards.items():
        authority = []

        async def authorized(uid=user_id, current=authority):
            context, roles = await current_capability(uid)
            current[:] = [context, roles]
            return bool(roles)

        async def cleanup(uid=user_id, chat=chat_id, retained=saved_cards, check=authorized, current=authority):
            async def eligible():
                return await queue._eligible(
                    SendTask(
                        cleanup,
                        user_id=uid,
                        chat_id=chat,
                        category="tickets",
                        notification_type=type_for_event(event),
                        audience_check=check,
                    )
                )

            replaces_push = uid != owner and uid in saved and payload["status"] == "cancelled"
            for entry in retained:
                if not await eligible():
                    raise RecipientSuppressed
                context, roles = current
                facts = request_facts(context, show_student=uid != owner and "HEADMAN" in roles)
                text = render(private_request_event(event), facts=facts)
                visible = await _edit_request_card(bot, chat, entry["message_id"], text, eligible)
                if visible and replaces_push:
                    # A visible terminal edit satisfies the cancellation push on broker replay.
                    await state.complete(event["event_id"], uid)
                await tracker.delete_entry(tracking_kind, str(tracking_id), entry)

            if replaces_push and uid not in delivered and not await state.completed(event["event_id"], uid):
                decision_roles = set()

                async def may_send_fallback():
                    live = await resolve_all(academic, decision_requests)
                    decision_roles.update(saved[uid] & live.get(uid, frozenset()))
                    return bool(decision_roles)

                if await queue._eligible(
                    SendTask(
                        cleanup,
                        user_id=uid,
                        chat_id=chat,
                        category="tickets",
                        notification_type=type_for_event(event),
                        audience_check=may_send_fallback,
                    )
                ):
                    role = next(role for role in ("STUDENT", "HEADMAN", "TEACHER", "ADMIN") if role in decision_roles)
                    await bot.send_message(
                        chat_id=chat,
                        **message_options(private_request_event(event), config=config, role=role),
                    )
                    await state.complete(event["event_id"], uid)

        await queue.put(
            SendTask(
                cleanup,
                user_id=user_id,
                chat_id=chat_id,
                category="tickets",
                notification_type=type_for_event(event),
                audience_check=authorized,
            )
        )

    return set(cards)


async def _edit_request_card(bot, chat_id, message_id, text, eligible):
    try:
        await bot.edit_message_text(
            chat_id=chat_id, message_id=message_id, text=text, parse_mode="HTML", reply_markup=None
        )
        return True
    except TelegramBadRequest as error:
        message = error.message.lower()
        if "message is not modified" in message:
            return True
        if "message to edit not found" in message:
            return False
        if "there is no text in the message to edit" not in message:
            raise
    # Legacy entries can refer to a document/photo. Leave its caption and contents intact.
    if not await eligible():
        raise RecipientSuppressed
    try:
        await bot.edit_message_reply_markup(chat_id=chat_id, message_id=message_id, reply_markup=None)
    except TelegramBadRequest as error:
        message = error.message.lower()
        if "message is not modified" not in message and "message to edit not found" not in message:
            raise
    return False


async def cleanup_reminders(event, *, bot, academic, reminders):
    payload, kind = event["payload"], event["event_type"]
    lesson_id = payload.get("lesson_id")
    if not positive(lesson_id):
        return
    if kind == "attendance.marked":
        user_ids = [payload["user_id"]]
    elif kind in _CHECKIN_REMINDERS:
        # The mark handler may have already scanned the list while Telegram was sending.
        # Recheck after recording the send, including on replay with a delivery receipt.
        user_ids = [
            uid for uid in prepared_audience(event)
            if await reminders.is_student_marked(lesson_id, uid, strict=True)
        ]
    else:
        # Roster is used only to retire existing reminder artifacts, never to send facts.
        members = await academic.get_current_group_members(payload["group_id"])
        user_ids = [member.user_id for member in members if positive(member.user_id)]
    # Markers expire by TTL, so a delayed start or reminder cannot recreate a retired CTA.
    for user_id in user_ids:
        for chat_id, message_id in await reminders.get_messages(lesson_id, user_id, strict=True):
            if chat_id is None:
                # Numeric legacy IDs have no chat provenance and expire with the list.
                continue
            await retire_reminder(bot, chat_id, message_id)
            await reminders.remove_message_id(lesson_id, user_id, message_id, chat_id=chat_id)


async def settle_reminder_delivery(event, delivery, bot, reminders, state):
    lesson_id = event["payload"]["lesson_id"]
    user_id, chat_id, message_id = delivery["user_id"], delivery["chat_id"], delivery["message_id"]
    await reminders.add_message_id(lesson_id, user_id, message_id, chat_id=chat_id, strict=True)
    # The mark event may have completed before the provider returned this message ID.
    if await reminders.is_student_marked(lesson_id, user_id, strict=True):
        await retire_reminder(bot, chat_id, message_id)
        await reminders.remove_message_id(lesson_id, user_id, message_id, chat_id=chat_id)
    await state.complete(event["event_id"], user_id)
    prepared = event["_bot_prepared"]
    prepared["reminder_recovery"].remove(delivery)
    if not prepared["reminder_recovery"]:
        del prepared["reminder_recovery"]


async def retire_reminder(bot, chat_id, message_id):
    try:
        try:
            await bot.delete_message(chat_id=chat_id, message_id=message_id)
        except TelegramBadRequest as error:
            if "message to delete not found" in error.message.lower():
                return
            await bot.edit_message_reply_markup(chat_id=chat_id, message_id=message_id, reply_markup=None)
    except (TelegramRetryAfter, TelegramNetworkError, TelegramServerError) as error:
        raise ReminderLifecycleUnavailable("Telegram reminder retirement unavailable") from error
    except (TelegramBadRequest, TelegramForbiddenError) as error:
        logger.info("Telegram cannot retire tracked reminder error=%s", type(error).__name__)
