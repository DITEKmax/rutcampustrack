"""Public dispatch harness: real queue, protobuf authorities and Redis lifecycle; synthetic provider."""

import asyncio
import base64
import json
from types import SimpleNamespace
from unittest.mock import AsyncMock
from urllib.parse import parse_qs, urlsplit
from uuid import uuid4

import pytest
from aiogram.exceptions import TelegramBadRequest

from bot.consumers.event_dispatcher import EventDispatcher
from bot.grpc_client import academic_pb2 as academic_pb
from bot.grpc_client import attendance_pb2 as request_pb
from bot.services.notification_prefs import NotificationPreferencesUnavailable
from bot.services.otp_message_tracker import OtpMessageTracker
from bot.services.redis_client import ReminderRedisClient
from bot.services.request_message_tracker import RequestMessageTracker
from bot.services.send_queue import TelegramSendQueue


class BusinessDelivery:
    def __init__(self, redis, *, app_url="https://safe.test/mini-app/"):
        self.redis = redis
        self.roles = {1: ["STUDENT"], 2: ["STUDENT"], 3: ["STUDENT"]}
        self.authority_by_event = {}
        self.chats = {1: 101, 2: 202, 3: 0, 99: 909}
        self.roster_ids = (1, 2, 3)
        self.audience_requests = []
        self.sent, self.deleted, self.edited = [], [], []
        self.before_edit = None
        self.bad_delete = False
        self.prefs_on = True
        self.request_context = request_pb.ResolveRequestNotificationResponse(
            group_id=7,
            student_id=1,
            student_name="Канонический <студент>",
            detail=request_pb.StudentRequestDetail(
                summary=request_pb.StudentRequestSummary(
                    id="request-1",
                    kind=request_pb.STUDENT_REQUEST_KIND_EXCUSE,
                    status=request_pb.STUDENT_REQUEST_STATUS_PENDING,
                    lessons=[
                        request_pb.StudentRequestLesson(
                            id=11, date="2026-10-10", subject_name="Канонический предмет", lesson_number=2
                        )
                    ],
                ),
                reason=request_pb.STUDENT_EXCUSE_REASON_ILLNESS,
                comment="private canonical reason",
            ),
        )

        async def audience(request):
            self.audience_requests.append(request)
            # Controlled Academic authority retains its required-group RPC boundary (the
            # actual resolver rejects an omitted group for these accepted group meanings).
            if request.event_type in {
                "lesson.started",
                "lesson.reminder",
                "lesson.closed",
                "lesson.blocked",
                "lesson.cancelled",
                "lesson.one_off.created",
                "lesson.one_off.cancelled",
                "group.renamed",
                "group.archived",
            } and not request.HasField("group_id"):
                raise NotificationPreferencesUnavailable("Academic group context is required")
            return academic_pb.NotificationAudienceResponse(
                request_id=request.request_id,
                event_type=request.event_type,
                recipients=[
                    academic_pb.NotificationAudienceRecipient(user_id=uid, roles=roles)
                    for uid, roles in self.authority_by_event.get(request.event_type, self.roles).items()
                ],
            )

        async def user(uid):
            return academic_pb.UserResponse(id=uid, telegram_id=self.chats[uid])

        async def roster(group):
            return [academic_pb.StudentInfo(user_id=uid, telegram_id=self.chats[uid]) for uid in self.roster_ids]

        async def send(**kwargs):
            self.sent.append(kwargs)
            return SimpleNamespace(message_id=1000 + len(self.sent))

        async def delete(**kwargs):
            self.deleted.append((kwargs["chat_id"], kwargs["message_id"]))
            if self.bad_delete:
                raise TelegramBadRequest(method=SimpleNamespace(), message="synthetic missing message")

        async def edit(**kwargs):
            self.edited.append((kwargs["chat_id"], kwargs["message_id"], kwargs["reply_markup"]))
            if self.before_edit:
                self.before_edit()

        self.bot = SimpleNamespace(
            send_message=send, delete_message=delete, edit_message_reply_markup=edit, send_document=AsyncMock()
        )
        self.academic = SimpleNamespace(
            resolve_notification_audience=audience,
            get_user_by_id=user,
            get_current_group_members=roster,
            get_subjects_by_ids=AsyncMock(side_effect=RuntimeError("unused legacy lookup")),
        )
        self.attendance = SimpleNamespace(
            resolve_request_notification=AsyncMock(side_effect=lambda *args: self.request_context),
            fetch_excuse_attachment=AsyncMock(),
        )
        self.reminders = ReminderRedisClient(
            key_template="reminder:msgs:{lesson_id}:{user_id}", ttl=86400, redis_client=redis
        )
        self.tracker = RequestMessageTracker(redis_client=redis)
        self.otp = OtpMessageTracker(300, redis_client=redis)
        self.queue = TelegramSendQueue(
            SimpleNamespace(
                is_enabled=AsyncMock(
                    side_effect=lambda chat, category, *, user_id, **kwargs: (
                        self.prefs_on and self.chats.get(user_id) == chat
                    )
                )
            )
        )
        self.dispatcher = EventDispatcher(
            self.bot,
            self.academic,
            self.queue,
            self.reminders,
            SimpleNamespace(admin_telegram_ids="", mini_app_web_url=app_url or "", mini_app_url=""),
            self.otp,
            request_tracker=self.tracker,
            attendance_client=self.attendance,
        )

    async def dispatch(self, event):
        prepared = await self.dispatcher.prepare(event)
        batch = await self.dispatcher.dispatch(prepared)
        if batch is not None:
            await asyncio.wait_for(batch.wait(), 2)


def fact_event(kind, **overrides):
    payload = dict(
        lesson_id=11,
        group_id=7,
        subject_id=8,
        semester_id=9,
        lesson_type="lecture",
        lesson_date="2026-10-10",
        date="2026-10-10",
        subject_name="Теормех <snapshot>",
        lesson_number=2,
        start_time="10:05",
        end_time="11:25",
        room="А-305",
        user_id=1,
        target_user_id=1,
        marked_by="headman",
        status="present",
        title="ДЗ <snapshot>",
        group_name="Группа snapshot",
        new_name="Группа snapshot",
        cancel_reason="Причина snapshot",
        ticket_id="request-1",
        request_id="request-1",
        student_name="forged student",
        comment="private event comment",
        decision_comment="private decision comment",
    )
    if not kind.startswith(("excuse.", "late_checkin.")):
        payload.pop("comment")
        payload.pop("decision_comment")
    if kind.endswith(".decided"):
        payload["status"] = "approved"
    payload.update(overrides)
    return {
        "event_id": str(uuid4()),
        "event_type": kind,
        "occurred_at": "2026-10-10T13:00:00+03:00",
        "payload": payload,
    }


async def assert_business_delivery(
    redis,
    kind,
    *,
    status=None,
    invalid=False,
    reject=False,
    missing_fields=(),
    empty=False,
    marked=False,
    marked_users=(),
    no_messages=False,
    start_keys=False,
    bad_delete=False,
    expected=(),
    absent=(),
    ordered=(),
    fact_groups=(),
    no_reason=False,
    canonical=False,
    canonical_reason=None,
    canonical_name=None,
    canonical_subject=None,
    canonical_date=None,
    canonical_number=None,
    extra=None,
    chats=None,
    payload_fields=None,
    expected_users=None,
    app_url="https://safe.test/mini-app/",
    app_action=None,
    callback_prefix=None,
):
    h = BusinessDelivery(redis, app_url=app_url)
    event = fact_event(kind)
    if status is not None:
        event["payload"]["status"] = status
    if extra:
        event["payload"].update(extra)
    if payload_fields is not None:
        event["payload"] = {key: value for key, value in event["payload"].items() if key in payload_fields}
    for field in missing_fields:
        event["payload"].pop(field, None)
    if chats is not None:
        h.chats.update(chats)
    personal = kind in {
        "attendance.marked",
        "excuse.decided",
        "late_checkin.decided",
        "homework.due_reminder",
        "homework.weekly_digest",
    }
    if personal:
        h.roles = {1: ["STUDENT"]}
    if canonical_reason is not None:
        h.request_context.detail.reason = canonical_reason
    if canonical_name is not None:
        h.request_context.student_name = canonical_name
    lesson = h.request_context.detail.summary.lessons[0]
    if canonical_subject is not None:
        lesson.subject_name = canonical_subject
    if canonical_date is not None:
        lesson.date = canonical_date
    if canonical_number is not None:
        lesson.lesson_number = canonical_number
    if kind.endswith(".requested"):
        h.roles = {2: ["HEADMAN"]}
        if kind == "late_checkin.requested":
            h.request_context.detail.summary.kind = request_pb.STUDENT_REQUEST_KIND_LATE_CHECKIN
    if empty:
        h.roles = {}
    if no_reason:
        event["payload"].pop("cancel_reason", None)
    if invalid:
        event["payload"]["group_id"] = -1
        reject = True
    if marked:
        marked_users = (1, 2)
    for uid in marked_users:
        await h.reminders.mark_student_marked(11, uid, "present")

    cleanup = kind in {"lesson.closed", "attendance.marked"}
    # Multiple distinct provider artifacts are essential: deleting only the first is a bug.
    own_messages = {1: [111, 112, 113], 2: [221, 222], 3: [331, 332]}
    if (cleanup or start_keys) and not no_messages:
        for uid, mids in own_messages.items():
            for mid in mids:
                await h.reminders.add_message_id(11, uid, mid)
    else:
        own_messages = {uid: [] for uid in own_messages}
    if kind == "lesson.reminder" and not start_keys:
        assert await h.reminders.get_message_ids(11, 1) == []
        assert await h.reminders.get_message_ids(11, 2) == []
    if kind == "lesson.closed":
        for uid in h.roster_ids:
            await h.reminders.mark_student_marked(11, uid, "present")
    # Unrelated lesson and an out-of-group owner must survive every cleanup path.
    await h.reminders.add_message_id(12, 1, 1201)
    await h.reminders.mark_student_marked(12, 1, "present")
    await h.reminders.add_message_id(11, 99, 9901)
    await h.reminders.mark_student_marked(11, 99, "present")
    h.bad_delete = bad_delete
    if expected_users is None:
        expected_users = () if empty else (1,) if personal else (2,) if kind.endswith(".requested") else (1, 2)
        if marked:
            expected_users = ()
    expected_chats = sorted(h.chats[uid] for uid in expected_users)
    assert all(chat > 0 for chat in expected_chats), "explicit expected destinations must be bound"
    h.queue.start()
    try:
        if reject:
            with pytest.raises(NotificationPreferencesUnavailable):
                await h.dispatch(event)
            assert not h.sent and not h.deleted
            assert await redis.get(f"notification-bot:sent:{event['event_id']}:1") is None
            return
        await h.dispatch(event)
        assert sorted(item["chat_id"] for item in h.sent) == expected_chats
        assert 909 not in [item["chat_id"] for item in h.sent]
        for item in h.sent:
            assert item["parse_mode"] == "HTML"
            text = item["text"]
            for private in (*absent, "private event comment", "private decision comment", "forged student"):
                assert private not in text
            if callback_prefix is not None:
                rows = item["reply_markup"].inline_keyboard
                assert len(rows[0]) == 1
                callbacks = rows[1:]
                assert [[button.callback_data for button in row] for row in callbacks] == [
                    [f"{callback_prefix}:approve:request-1", f"{callback_prefix}:reject:request-1"]
                ]
                assert all(button.url is None and button.web_app is None for row in callbacks for button in row)
                for private in absent:
                    assert all(private not in button.callback_data for row in callbacks for button in row)
            if app_url is None:
                assert "reply_markup" not in item
            if app_action is not None:
                button = item["reply_markup"].inline_keyboard[0][0]
                assert button.url is None and button.web_app is not None
                actual_url, configured_url = urlsplit(button.web_app.url), urlsplit(app_url)
                assert (actual_url.scheme, actual_url.netloc, actual_url.path) == (
                    configured_url.scheme,
                    configured_url.netloc,
                    configured_url.path,
                )
                query = parse_qs(actual_url.query)
                assert set(query) == {"tgWebAppStartParam"}
                parameter = query["tgWebAppStartParam"][0]
                assert parameter.startswith("n2_")
                encoded = parameter[3:]
                launch = json.loads(base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4)))
                for private in (*absent, "private", "forged", "student_name", "subject_name", "comment", "token"):
                    assert private not in json.dumps(launch)
                assert launch == {"v": 2, "action": app_action}
            for fact in expected:
                assert fact in text
            assert [text.index(fact) for fact in ordered] == sorted(text.index(fact) for fact in ordered)
            for group in fact_groups:
                anchor = text.index(group[0])
                positions = [text.index(fact, anchor) for fact in group]
                assert positions == sorted(positions)
            if canonical:
                from html import escape

                assert escape(h.request_context.student_name) in text
                assert escape(lesson.subject_name) in text
        if kind in {"lesson.started", "lesson.reminder"}:
            for index, item in enumerate(h.sent, start=1):
                uid = next(uid for uid, chat in h.chats.items() if chat == item["chat_id"])
                assert await h.reminders.get_message_ids(event["payload"]["lesson_id"], uid) == own_messages.get(
                    uid, []
                ) + [1000 + index]
        if cleanup:
            targets = (1,) if kind == "attendance.marked" else tuple(uid for uid in h.roster_ids if h.chats[uid] > 0)
            expected_deleted = [(h.chats[uid], mid) for uid in targets for mid in own_messages[uid]]
            assert sorted(h.deleted) == sorted(expected_deleted)
            for uid in h.roster_ids:
                assert await h.reminders.get_message_ids(11, uid) == ([] if uid in targets else own_messages[uid])
            if kind == "lesson.closed":
                for uid in h.roster_ids:
                    assert await h.reminders.is_student_marked(11, uid) == (uid not in targets)
            else:
                assert await h.reminders.is_student_marked(11, 1)
        assert await h.reminders.get_message_ids(12, 1) == [1201]
        assert await h.reminders.is_student_marked(12, 1)
        assert await h.reminders.get_message_ids(11, 99) == [9901]
        assert await h.reminders.is_student_marked(11, 99)
        for uid in h.roles:
            receipt = await redis.get(f"notification-bot:sent:{event['event_id']}:{uid}")
            assert receipt == ("sent" if uid in expected_users else None)
        before = len(h.sent)
        deleted_before = list(h.deleted)
        await h.dispatch(event)
        assert len(h.sent) == before
        assert h.deleted == deleted_before
        h.bot.send_document.assert_not_awaited()
        h.attendance.fetch_excuse_attachment.assert_not_awaited()
    finally:
        await h.queue.shutdown()


async def assert_terminal_cards(redis, *, revoke_after_first=False, rebound=False, cancelled=False):
    h = BusinessDelivery(redis)
    h.roles = {1: ["STUDENT"]}
    h.authority_by_event = {"excuse.requested": {2: ["HEADMAN"]}, "late_checkin.requested": {2: ["HEADMAN"]}}
    kind = "late_checkin.decided" if cancelled else "excuse.decided"
    tracking = "late_checkin" if cancelled else "excuse"
    h.request_context.detail.summary.status = (
        request_pb.STUDENT_REQUEST_STATUS_CANCELLED if cancelled else request_pb.STUDENT_REQUEST_STATUS_APPROVED
    )
    if cancelled:
        h.request_context.detail.summary.kind = request_pb.STUDENT_REQUEST_KIND_LATE_CHECKIN
    for mid in (71, 72):
        await h.tracker.add(tracking, "request-1", 202, mid, 2)
    event = fact_event(kind, status="cancelled" if cancelled else "approved", resolution_reason="geo_confirmed")
    h.queue.start()
    try:
        if rebound:
            # Materialize stale routing before the provider rate wait, then change binding.
            entered, release = asyncio.Event(), asyncio.Event()

            async def rate_wait():
                entered.set()
                await release.wait()

            h.queue._consume_token = rate_wait
            prepared = await h.dispatcher.prepare(event)
            batch = await h.dispatcher.dispatch(prepared)
            await asyncio.wait_for(entered.wait(), 1)
            h.chats[1] = 404
            release.set()
            await asyncio.wait_for(batch.wait(), 2)
            assert not h.sent
            await h.dispatch(event)
            assert [item["chat_id"] for item in h.sent] == [404]
            assert all(item["chat_id"] != 101 for item in h.sent)
        else:
            if revoke_after_first:
                h.before_edit = lambda: h.authority_by_event.update({tracking + ".requested": {}})
            await h.dispatch(event)
            assert [item["chat_id"] for item in h.sent] == [101]
            assert h.edited == ([(202, 71, None)] if revoke_after_first else [(202, 71, None), (202, 72, None)])
            remaining = await h.tracker.get_all(tracking, "request-1")
            assert [item["message_id"] for item in remaining] == ([72] if revoke_after_first else [])
            text = h.sent[0]["text"]
            assert "private" not in text and "forged" not in text
            if cancelled:
                assert "отменена" in text and "отклонена" not in text
                assert "Присутствие подтверждено по геолокации" in text
    finally:
        await h.queue.shutdown()
