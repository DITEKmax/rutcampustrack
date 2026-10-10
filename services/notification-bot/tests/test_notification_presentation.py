"""Content trust and single-recipient business notification regression."""

import base64
import json
from types import SimpleNamespace
from unittest.mock import AsyncMock
from urllib.parse import parse_qs, urlsplit

import pytest

from bot.grpc_client import attendance_pb2 as pb
from bot.notifications.headman_alerts import handle_headman_alert
from bot.notifications.presentation import render
from bot.services.notification_prefs import type_for_event
from bot.services.send_queue import TelegramSendQueue


def test_decision_remains_cancelled_and_untrusted_values_are_escaped_without_raw_ids():
    event = {
        "event_type": "late_checkin.decided",
        "payload": {
            "status": "cancelled",
            "resolution_reason": "geo_confirmed",
            "decision_comment": '<a href="https://evil.test">текст</a>',
            "subject_name": "<b>Предмет</b>",
        },
    }
    assert type_for_event(event) == "LATE_CHECKIN_CANCELLED"
    text = render(event)
    assert "<a href=" not in text and "<b>Предмет</b>" not in text
    assert "&lt;a href=" in text and "&lt;b&gt;Предмет&lt;/b&gt;" in text
    partial = render(
        {
            "event_type": "excuse.decided",
            "payload": {
                "status": "approved",
                "excluded_lesson_ids": [987654321],
                "excluded_occurrence_ids": ["private-occurrence"],
            },
        }
    )
    assert "987654321" not in partial and "private-occurrence" not in partial
    assert "частично" in partial  # Actual exclusions, not inferred from unrelated status.
    assert "частично" not in render({"event_type": "excuse.decided", "payload": {"status": "approved"}})


@pytest.mark.parametrize(
    "event_type, request_kind, request_field, callback_prefix",
    [
        ("excuse.requested", pb.STUDENT_REQUEST_KIND_EXCUSE, "ticket_id", "ex"),
        ("late_checkin.requested", pb.STUDENT_REQUEST_KIND_LATE_CHECKIN, "request_id", "lcr"),
    ],
)
async def test_requested_summary_uses_canonical_context_once_without_private_documents(
    event_type,
    request_kind,
    request_field,
    callback_prefix,
):
    context = pb.ResolveRequestNotificationResponse(
        group_id=7,
        student_id=12,
        student_name="<b>Студент</b>",
        detail=pb.StudentRequestDetail(
            reason=pb.STUDENT_EXCUSE_REASON_ILLNESS,
            comment="private medical details",
            summary=pb.StudentRequestSummary(
                id="request-1",
                kind=request_kind,
                status=pb.STUDENT_REQUEST_STATUS_PENDING,
                lessons=[pb.StudentRequestLesson(id=23, subject_name="Математика", date="2026-10-10", lesson_number=2)],
            ),
            attachments=[
                pb.StudentRequestAttachment(
                    id="attachment-1",
                    name="private.pdf",
                    content_type="application/pdf",
                    size_bytes=100,
                    state=pb.STUDENT_REQUEST_ATTACHMENT_STATE_ACTIVE,
                )
            ],
        ),
    )
    member = SimpleNamespace(user_id=11, telegram_id=111, is_headman=True)
    academic = SimpleNamespace(get_current_group_members=AsyncMock(return_value=[member]))
    attendance = SimpleNamespace(
        resolve_request_notification=AsyncMock(return_value=context), fetch_excuse_attachment=AsyncMock()
    )
    bot = SimpleNamespace(send_message=AsyncMock(), send_document=AsyncMock())
    queue = TelegramSendQueue(SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    queue.start()
    try:
        async with queue.staged() as batch:
            await handle_headman_alert(
                {
                    "event_type": event_type,
                    "payload": {
                        "group_id": 7,
                        request_field: "request-1",
                        ("request_id" if request_field == "ticket_id" else "ticket_id"): "payload-fake-id",
                        "student_name": "payload impostor",
                        "comment": "payload secret",
                    },
                },
                bot,
                academic,
                queue,
                attendance_client=attendance,
                config=SimpleNamespace(mini_app_web_url="https://safe.test/mini-app/"),
            )
        await batch.wait()
        bot.send_message.assert_awaited_once()
        bot.send_document.assert_not_awaited()
        attendance.fetch_excuse_attachment.assert_not_awaited()
        options = bot.send_message.call_args.kwargs
        assert "&lt;b&gt;Студент&lt;/b&gt;" in options["text"]
        assert all(
            value not in options["text"]
            for value in (
                "payload impostor",
                "payload secret",
                "private medical details",
                "private.pdf",
                "attachment-1",
            )
        )
        assert options["parse_mode"] == "HTML"
        rows = options["reply_markup"].inline_keyboard
        start_param = parse_qs(urlsplit(rows[0][0].web_app.url).query)["tgWebAppStartParam"][0]
        assert start_param.startswith("n2_")
        encoded = start_param[3:]
        launch = json.loads(base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4)))
        assert launch == {
            "v": 2,
            "action": {
                "kind": "request",
                "role": "HEADMAN",
                "requestId": context.detail.summary.id,
                "requestKind": "EXCUSE" if event_type == "excuse.requested" else "LATE_CHECKIN",
            },
        }
        assert all(
            private not in json.dumps(launch)
            for private in (
                "Студент",
                "Математика",
                "private medical details",
                "private.pdf",
                "attachment-1",
                "payload-fake-id",
            )
        )
        assert rows[1][0].callback_data == f"{callback_prefix}:approve:request-1"
    finally:
        await queue.shutdown()


async def test_multiple_legacy_cards_emit_one_decision_with_fresh_authority():
    from bot.consumers.event_dispatcher import EventDispatcher

    member = SimpleNamespace(user_id=11, telegram_id=111, is_headman=True)
    academic = SimpleNamespace(get_current_group_members=AsyncMock(return_value=[member]))
    context = pb.ResolveRequestNotificationResponse(
        group_id=7,
        detail=pb.StudentRequestDetail(
            summary=pb.StudentRequestSummary(
                id="request-1", kind=pb.STUDENT_REQUEST_KIND_EXCUSE, status=pb.STUDENT_REQUEST_STATUS_APPROVED
            )
        ),
    )
    attendance = SimpleNamespace(resolve_request_notification=AsyncMock(return_value=context))
    entries = [{"user_id": 11, "chat_id": 111, "message_id": mid} for mid in (1, 2)]
    tracker = SimpleNamespace(get_all=AsyncMock(return_value=entries), delete_entry=AsyncMock())
    bot = SimpleNamespace(edit_message_reply_markup=AsyncMock(), send_message=AsyncMock())
    queue = TelegramSendQueue(SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    dispatcher = EventDispatcher(
        bot,
        academic,
        queue,
        None,
        SimpleNamespace(admin_telegram_ids=""),
        None,
        request_tracker=tracker,
        attendance_client=attendance,
    )
    queue.start()
    try:
        async with queue.staged() as batch:
            await dispatcher._close_tracked_messages(
                "excuse",
                "request-1",
                "Одобрено",
                {"event_type": "excuse.decided", "payload": {"status": "approved", "user_id": 12}},
            )
        await batch.wait()
        assert bot.edit_message_reply_markup.await_count == 2
        bot.send_message.assert_awaited_once()
        assert tracker.delete_entry.await_count == 2
    finally:
        await queue.shutdown()


def test_addressed_action_uses_only_existing_target_and_verified_context():
    from urllib.parse import parse_qs, urlsplit

    from bot.notifications.presentation import message_options, notification_action
    from bot.services.mini_app_links import encode_notification_action

    event = {"event_type": "excuse.requested", "payload": {"ticket_id": "request-1"}}
    action = notification_action(event, role="HEADMAN")
    assert action == {"kind": "request", "role": "HEADMAN", "requestKind": "EXCUSE", "requestId": "request-1"}
    options = message_options(
        event, role="HEADMAN", config=SimpleNamespace(mini_app_web_url="https://safe.test/mini-app/")
    )
    button = options["reply_markup"].inline_keyboard[0][0]
    assert parse_qs(urlsplit(button.web_app.url).query) == {
        "tgWebAppStartParam": ["n2_" + encode_notification_action(action)]
    }
    assert notification_action({"event_type": "lesson.cancelled", "payload": {}}) is None
    assert notification_action(
        {"event_type": "homework.due_reminder", "payload": {"homework": {"homework_id": 3}}}
    ) == {"kind": "homework", "role": "STUDENT", "homeworkId": "3"}
