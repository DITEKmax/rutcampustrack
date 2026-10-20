"""Content trust and single-recipient business notification regression."""

from types import SimpleNamespace
from urllib.parse import parse_qs, urlsplit

import pytest

from bot.grpc_client import attendance_pb2 as pb
from bot.notifications.presentation import render
from bot.services.notification_prefs import type_for_event
from tests.business_delivery import assert_business_delivery, assert_terminal_cards


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
    event_type, request_kind, request_field, callback_prefix, fake_redis
):
    alternate_field = "request_id" if request_field == "ticket_id" else "ticket_id"
    await assert_business_delivery(
        fake_redis,
        event_type,
        canonical=True,
        extra={alternate_field: "forged-alternate-request-id"},
        absent=("forged-alternate-request-id",),
        app_action={
            "kind": "request",
            "role": "HEADMAN",
            "requestKind": "EXCUSE" if request_kind == pb.STUDENT_REQUEST_KIND_EXCUSE else "LATE_CHECKIN",
            "requestId": "request-1",
        },
        callback_prefix=callback_prefix,
    )


async def test_multiple_legacy_cards_emit_one_decision_with_fresh_authority(fake_redis):
    await assert_terminal_cards(fake_redis)


def test_addressed_action_uses_only_existing_target_and_verified_context():

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
