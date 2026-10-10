"""Central Academic notification authority and broker-owned Telegram delivery."""

from __future__ import annotations

from datetime import date, datetime
from uuid import UUID, uuid4

from aiogram.exceptions import TelegramBadRequest
from aiogram.types import InlineKeyboardButton, InlineKeyboardMarkup

from bot.grpc_client import academic_pb2 as pb
from bot.grpc_client import attendance_pb2
from bot.notifications.presentation import message_options
from bot.services.notification_prefs import (
    BUSINESS_EVENTS,
    NotificationPreferencesUnavailable,
    category_for_event,
    type_for_event,
)
from bot.services.send_queue import RecipientSuppressed, SendTask

ROLES = frozenset({"STUDENT", "HEADMAN", "TEACHER", "ADMIN"})
RETENTION = 7 * 24 * 60 * 60
_PERSONAL = {
    "attendance.marked", "excuse.requested", "excuse.decided", "late_checkin.requested",
    "late_checkin.decided", "homework.weekly_digest", "homework.due_reminder",
    "group.membership.changed", "headman.access.changed", "assistant.access.changed", "admin.access.changed",
}
_OWN_SYNC = {
    "lesson.cancelled", "lesson.one_off.created", "lesson.one_off.cancelled",
    "lesson.restored",
    "homework.published", "homework.updated", "homework.withdrawn", "group.renamed", "group.archived",
    "semester.activated",
}
_REQUESTS = {"excuse.requested", "excuse.decided", "late_checkin.requested", "late_checkin.decided"}
_PRIVATE_FIELDS = {
    "ticket_id", "request_id", "status", "group_id", "user_id", "created_at", "decided_at",
    "excluded_lesson_ids", "excluded_occurrence_ids",
}
_TEACHER_EVENTS = {
    "lesson.cancelled", "lesson.one_off.created", "lesson.one_off.cancelled",
    "lesson.transferred", "lesson.restored", "lesson.updated",
    "homework.published", "homework.updated", "homework.withdrawn",
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
            not positive(user_id) or user_id in result or not roles
            or len(roles) != len(set(roles)) or not set(roles) <= ROLES
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
        request_id=str(uuid4()), event_type=kind, occurred_at=occurred_at,
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
        else [{**payload, **payload.get("homework", {})}] if isinstance(payload.get("homework"), dict)
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
            "lecture", "practice", "lab", "LECTURE", "PRACTICE", "LAB",
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
        or set(envelope) != {"version", "event_id", "event_type", "recipients"}
        or type(envelope["version"]) is not int or envelope["version"] != 1
        or envelope["event_id"] != event.get("event_id")
        or envelope["event_type"] != event.get("event_type")
        or not isinstance(envelope["recipients"], list)
    ):
        raise ValueError("Invalid internal notification envelope")
    recipients = []
    try:
        for item in envelope["recipients"]:
            if (
                not isinstance(item, dict) or set(item) != {"user_id", "roles"}
                or not positive(item["user_id"]) or not isinstance(item["roles"], list)
                or any(not isinstance(role, str) for role in item["roles"])
            ):
                raise ValueError
            recipients.append(pb.NotificationAudienceRecipient(**item))
        return validate_audience(recipients)
    except (ValueError, TypeError, NotificationPreferencesUnavailable) as error:
        raise ValueError("Invalid internal notification recipients") from error


async def prepare_business(event, academic):
    """Return factual event plus immutable minimal audience for same-queue atomic replace."""
    requests = audience_requests(event)
    audience = await resolve_all(academic, requests)
    return {
        **event,
        "_bot_prepared": {
            "version": 1, "event_id": event["event_id"], "event_type": event["event_type"],
            "recipients": [{"user_id": uid, "roles": sorted(roles)} for uid, roles in sorted(audience.items())],
        },
    }


class NotificationDeliveryState:
    """Optional partial-delivery receipts; immutable roles are held by the durable broker envelope."""

    def __init__(self, redis_client):
        self._redis = redis_client

    async def completed(self, event_id, user_id):
        try:
            return await self._redis.get(f"notification-bot:sent:{event_id}:{user_id}") == "sent"
        except Exception as error:
            raise NotificationPreferencesUnavailable("Notification receipt unavailable") from error

    async def complete(self, event_id, user_id):
        try:
            await self._redis.set(f"notification-bot:sent:{event_id}:{user_id}", "sent", ex=RETENTION)
        except Exception as error:
            raise NotificationPreferencesUnavailable("Notification receipt unavailable") from error


async def handle_business(event, *, bot, academic, queue, reminders, config, state, attendance=None, tracker=None):
    requests = audience_requests(event)
    request = requests[0]
    kind, payload, event_id = event["event_type"], event["payload"], event["event_id"]
    pending_context = None
    if kind.endswith(".requested") and kind in _REQUESTS:
        if attendance is None:
            raise NotificationPreferencesUnavailable("Canonical request authority unavailable")
        request_id = payload.get("ticket_id" if kind.startswith("excuse.") else "request_id")
        request_kind = (
            attendance_pb2.STUDENT_REQUEST_KIND_EXCUSE if kind.startswith("excuse.")
            else attendance_pb2.STUDENT_REQUEST_KIND_LATE_CHECKIN
        )
        try:
            pending_context = await attendance.resolve_request_notification(request_kind, request_id)
            summary = pending_context.detail.summary
            if (
                summary.id != request_id or summary.kind != request_kind
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
    saved = prepared_audience(event)
    lesson_id = payload.get("lesson_id")
    if kind == "attendance.marked" and payload.get("status") in {"present", "absent", "excused", "free_attendance"}:
        await reminders.mark_student_marked(lesson_id, payload["user_id"], payload["status"])
    for user_id, saved_roles in saved.items():
        roles = saved_roles & current.get(user_id, frozenset())
        if not roles or await state.completed(event_id, user_id):
            continue
        if kind == "attendance.marked" and payload.get("marked_by") != "headman":
            continue
        if (
            kind in _OWN_SYNC and positive(payload.get("actor_user_id"))
            and payload["actor_user_id"] == user_id
        ):
            continue
        if kind == "group.renamed" or kind == "group.archived":
            if payload.get("notification_cause") == "AUTOMATIC_GROUP_CYCLE":
                roles -= {"ADMIN"}
                if not roles:
                    continue
        if kind == "lesson.reminder" and await reminders.is_student_marked(lesson_id, user_id):
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

        live_roles = [roles]

        async def authorized(uid=user_id, retained=roles, role_state=live_roles):
            live = await resolve_all(academic, requests)
            role_state[0] = retained & live.get(uid, frozenset())
            if not role_state[0]:
                return False
            if await state.completed(event_id, uid):
                return False
            if kind == "lesson.reminder" and await reminders.is_student_marked(lesson_id, uid):
                return False
            if pending_context is not None:
                try:
                    latest = await attendance.resolve_request_notification(request_kind, request_id)
                    summary = latest.detail.summary
                    if (
                        summary.id != request_id or summary.kind != request_kind
                        or latest.group_id != request.group_id or latest.student_id != request.affected_user_id
                    ):
                        raise ValueError
                    return summary.status == attendance_pb2.STUDENT_REQUEST_STATUS_PENDING
                except Exception as error:
                    raise NotificationPreferencesUnavailable("Current request authority unavailable") from error
            return True

        # One event/user, regardless of how many role projections are authorized.
        display_event = event
        if kind in _REQUESTS:
            # Even private owner copy contains no reasons, files, comments or PII.
            display_event = {
                **event, "payload": {key: value for key, value in payload.items() if key in _PRIVATE_FIELDS},
            }

        async def send(chat=chat_id, uid=user_id, display=display_event, check=authorized, role_state=live_roles):
            async def eligible():
                return await queue._eligible(SendTask(
                    send, user_id=uid, chat_id=chat, category=category_for_event(kind),
                    notification_type=type_for_event(event), audience_check=check,
                ))
            role_order = (
                ("HEADMAN", "STUDENT", "TEACHER", "ADMIN") if pending_context is not None
                else ("STUDENT", "HEADMAN", "TEACHER", "ADMIN")
            )
            role = next(role for role in role_order if role in role_state[0])
            fallback = None
            if pending_context is not None and role == "HEADMAN":
                prefix = "ex" if kind.startswith("excuse.") else "lcr"
                fallback = InlineKeyboardMarkup(inline_keyboard=[[
                    InlineKeyboardButton(text="Одобрить", callback_data=f"{prefix}:approve:{request_id}"),
                    InlineKeyboardButton(text="Отклонить", callback_data=f"{prefix}:reject:{request_id}"),
                ]])
            result = await bot.send_message(
                chat_id=chat, **message_options(display, config=config, role=role, fallback_markup=fallback),
            )
            # Persist receipt before best-effort auxiliary state.
            await state.complete(event_id, uid)
            if kind in {"lesson.started", "lesson.reminder"}:
                await reminders.add_message_id(lesson_id, uid, result.message_id)
            if pending_context is not None and tracker is not None:
                tracking_kind = "excuse" if kind.startswith("excuse.") else "late_checkin"
                await tracker.add(tracking_kind, str(request_id), chat, result.message_id, uid)
            return result

        await queue.put(SendTask(
            send, user_id=user_id, chat_id=chat_id, category=category_for_event(kind),
            notification_type=type_for_event(event), audience_check=authorized,
        ))

    if kind in {"excuse.decided", "late_checkin.decided"} and tracker is not None:
        await queue_request_cleanup(
            event, bot=bot, academic=academic, queue=queue, attendance=attendance, tracker=tracker,
        )

    # Preserve reminder lifecycle independently of whether a business notification is eligible.
    if kind == "lesson.closed" or kind == "attendance.marked":
        await cleanup_reminders(event, bot=bot, academic=academic, reminders=reminders)


async def queue_request_cleanup(event, *, bot, academic, queue, attendance, tracker):
    """Retire only saved cards with canonical identity and fresh current capability."""
    kind, payload = event["event_type"], event["payload"]
    tracking_kind = "excuse" if kind.startswith("excuse.") else "late_checkin"
    tracking_id = payload.get("ticket_id" if tracking_kind == "excuse" else "request_id")
    entries = await tracker.get_all(tracking_kind, str(tracking_id))
    cards = {}
    for entry in entries:
        if all(positive(entry.get(key)) for key in ("user_id", "chat_id", "message_id")):
            cards.setdefault((entry["user_id"], entry["chat_id"]), []).append(entry)
    if not cards:
        return
    if attendance is None:
        raise NotificationPreferencesUnavailable("Canonical cleanup authority unavailable")
    request_kind = (
        attendance_pb2.STUDENT_REQUEST_KIND_EXCUSE if tracking_kind == "excuse"
        else attendance_pb2.STUDENT_REQUEST_KIND_LATE_CHECKIN
    )
    expected_status = {
        "approved": attendance_pb2.STUDENT_REQUEST_STATUS_APPROVED,
        "rejected": attendance_pb2.STUDENT_REQUEST_STATUS_REJECTED,
        "cancelled": attendance_pb2.STUDENT_REQUEST_STATUS_CANCELLED,
    }[payload["status"]]
    decision_requests = audience_requests(event)

    async def current_capability(uid):
        try:
            context = await attendance.resolve_request_notification(request_kind, tracking_id)
            summary = context.detail.summary
            if (
                summary.id != tracking_id or summary.kind != request_kind
                or context.group_id != payload.get("group_id")
                or context.student_id != payload.get("user_id")
                or not positive(context.group_id) or not positive(context.student_id)
            ):
                raise ValueError("Mismatched canonical cleanup identity")
            if summary.status != expected_status:
                raise NotificationPreferencesUnavailable("Canonical request outcome not current")
            if uid == context.student_id:
                return bool((await resolve_all(academic, decision_requests)).get(uid))
            capability_event = {
                **event, "event_type": f"{tracking_kind}.requested",
                "payload": {
                    "group_id": context.group_id, "user_id": context.student_id,
                    "ticket_id" if tracking_kind == "excuse" else "request_id": tracking_id,
                },
            }
            roles = (await resolve_all(academic, audience_requests(capability_event))).get(uid, frozenset())
            return bool(roles & {"HEADMAN", "STUDENT"})
        except NotificationPreferencesUnavailable:
            raise
        except Exception as error:
            raise NotificationPreferencesUnavailable("Current cleanup authority unavailable") from error

    for (user_id, chat_id), saved_cards in cards.items():
        async def authorized(uid=user_id):
            return await current_capability(uid)

        async def cleanup(uid=user_id, chat=chat_id, retained=saved_cards, check=authorized):
            for entry in retained:
                if not await queue._eligible(SendTask(
                    cleanup, user_id=uid, chat_id=chat, category=category_for_event(kind),
                    notification_type=type_for_event(event), audience_check=check,
                )):
                    raise RecipientSuppressed
                try:
                    await bot.edit_message_reply_markup(
                        chat_id=chat, message_id=entry["message_id"], reply_markup=None,
                    )
                except TelegramBadRequest as error:
                    if "message is not modified" not in str(error).lower():
                        raise
                await tracker.delete_entry(tracking_kind, str(tracking_id), entry)

        await queue.put(SendTask(
            cleanup, user_id=user_id, chat_id=chat_id, category=category_for_event(kind),
            notification_type=type_for_event(event), audience_check=authorized,
        ))


async def cleanup_reminders(event, *, bot, academic, reminders):
    payload, kind = event["payload"], event["event_type"]
    lesson_id = payload.get("lesson_id")
    if not positive(lesson_id):
        return
    if kind == "attendance.marked":
        if payload.get("status") not in {"present", "absent", "excused", "free_attendance"}:
            return
        user_ids = [payload["user_id"]]
    else:
        # Roster is used only to retire existing reminder artifacts, never to send facts.
        members = await academic.get_current_group_members(payload["group_id"])
        user_ids = [member.user_id for member in members if positive(member.user_id)]
    for user_id in user_ids:
        user = await academic.get_user_by_id(user_id)
        if user.id != user_id or not positive(user.telegram_id):
            continue
        for message_id in await reminders.get_message_ids(lesson_id, user_id):
            try:
                await bot.delete_message(chat_id=user.telegram_id, message_id=message_id)
            except TelegramBadRequest:
                pass
        await reminders.delete_key(lesson_id, user_id)
        if kind == "lesson.closed":
            await reminders.delete_marked_key(lesson_id, user_id)
