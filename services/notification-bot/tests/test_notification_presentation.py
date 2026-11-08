"""Content trust and single-recipient business notification regression."""

import asyncio
from types import SimpleNamespace
from urllib.parse import parse_qs, urlsplit

import pytest

from bot.grpc_client import attendance_pb2 as pb
from bot.notifications.presentation import message_options, notification_action, render
from bot.services.mini_app_links import encode_notification_action
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


@pytest.mark.parametrize("kind", ["excuse.requested", "late_checkin.requested"])
@pytest.mark.parametrize("change", ["headman_revoked", "request_terminal"])
async def test_queued_canonical_summary_is_private_after_authority_changes(kind, change, fake_redis):
    from tests.business_delivery import BusinessDelivery, fact_event

    h = BusinessDelivery(fake_redis)
    h.roles = {2: ["HEADMAN"]}
    if kind == "late_checkin.requested":
        h.request_context.detail.summary.kind = pb.STUDENT_REQUEST_KIND_LATE_CHECKIN
    waiting, release = asyncio.Event(), asyncio.Event()

    async def rate_wait():
        waiting.set()
        await release.wait()

    h.queue._consume_token = rate_wait
    h.queue.start()
    raw = fact_event(kind)
    delivery = asyncio.create_task(h.dispatch(raw))
    try:
        await asyncio.wait_for(waiting.wait(), 2)
        if change == "headman_revoked":
            h.roles.clear()
        else:
            h.request_context.detail.summary.status = pb.STUDENT_REQUEST_STATUS_APPROVED
        release.set()
        await asyncio.wait_for(delivery, 2)
        assert not h.sent and not h.edited
        assert await fake_redis.get(f"notification-bot:sent:{raw['event_id']}:2") is None
    finally:
        delivery.cancel()
        await asyncio.gather(delivery, return_exceptions=True)
        await h.queue.shutdown()


def test_addressed_action_uses_only_existing_target_and_verified_context():
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


@pytest.mark.parametrize(
    "payload",
    [
        {
            "homework_id": 7,
            "lesson_date": "2026-10-10",
            "due_date": "2026-10-17",
            "homework": {"homework_id": 73, "lesson_date": "2026-10-15"},
        },
        {"homework_id": 73, "date": "2026-10-15"},
        {"lesson_date": "2026-10-15", "homework": {"homework_id": 73}},
        {"homework": {"id": 73, "lesson_date": "2026-10-15"}},
    ],
)
def test_due_reminder_opens_the_exact_homework_on_the_lesson_date(payload):
    assert notification_action({"event_type": "homework.due_reminder", "payload": payload}) == {
        "kind": "homework",
        "role": "STUDENT",
        "homeworkId": "73",
        "lessonDate": "2026-10-15",
    }


@pytest.mark.parametrize(
    "homework",
    [
        {"homework_id": 0, "id": 73, "lesson_date": "2026-10-15"},
        {"homework_id": None, "lesson_date": "2026-10-15"},
        {"lesson_date": "2026-10-15"},
        {"homework_id": 73, "lesson_date": "2026-02-30"},
        {"homework_id": 73},
    ],
)
def test_due_reminder_does_not_guess_a_student_target_from_an_invalid_snapshot(homework):
    event = {
        "event_type": "homework.due_reminder",
        "payload": {"homework_id": 7, "lesson_date": "2026-10-10", "homework": homework},
    }
    assert notification_action(event) is None


def test_student_due_reminder_cannot_open_homework_using_the_due_date():
    event = {
        "event_type": "homework.due_reminder",
        "payload": {"due_date": "2026-10-15", "homework": {"homework_id": 73}},
    }
    assert notification_action(event) is None


@pytest.mark.parametrize(
    "event_type, role",
    [
        ("homework.published", "TEACHER"),
        ("homework.updated", "TEACHER"),
        ("homework.withdrawn", "STUDENT"),
        ("homework.withdrawn", "HEADMAN"),
    ],
)
def test_unsupported_homework_actions_keep_the_message(event_type, role):
    event = {
        "event_type": event_type,
        "payload": {"homework_id": 73, "lesson_date": "2026-10-15", "title": "Упражнение"},
    }
    options = message_options(event, role=role, config=SimpleNamespace(mini_app_web_url="https://safe.test/mini-app/"))
    assert "Упражнение" in options["text"]
    assert options["reply_markup"].inline_keyboard[0][0].web_app.url == "https://safe.test/mini-app/"


def test_headman_homework_action_still_accepts_a_target_without_a_lesson_date():
    event = {"event_type": "homework.updated", "payload": {"homework_id": 73}}
    assert notification_action(event, role="HEADMAN") == {
        "kind": "homework",
        "role": "HEADMAN",
        "homeworkId": "73",
    }


@pytest.mark.parametrize(
    "config, start_key",
    [
        (SimpleNamespace(mini_app_web_url="https://safe.test/mini-app/"), "tgWebAppStartParam"),
        (SimpleNamespace(mini_app_url="https://t.me/ruttrack_bot/ruttrack"), "startapp"),
    ],
)
def test_student_lesson_started_button_uses_checkin_wire_format(config, start_key):
    event = {"event_type": "lesson.started", "payload": {"lesson_id": 77, "date": "2026-10-10"}}
    options = message_options(event, config=config, role="STUDENT")
    button = options["reply_markup"].inline_keyboard[0][0]
    url = button.web_app.url if start_key == "tgWebAppStartParam" else button.url
    assert button.text == "Открыть отметку"
    assert button.callback_data is None
    assert parse_qs(urlsplit(url).query) == {
        start_key: ["n2_eyJ2IjoyLCJhY3Rpb24iOnsia2luZCI6ImNoZWNraW4iLCJyb2xlIjoiU1RVREVOVCIsImxlc3NvbklkIjoiNzcifX0"]
    }


@pytest.mark.parametrize(
    "event_type, role",
    [
        ("lesson.started", "HEADMAN"),
        ("lesson.started", "TEACHER"),
        ("lesson.started", "ADMIN"),
        ("lesson.reminder", "STUDENT"),
        ("lesson.closed", "STUDENT"),
        ("lesson.cancelled", "STUDENT"),
    ],
)
def test_other_lesson_notifications_keep_the_lesson_action(event_type, role):
    event = {"event_type": event_type, "payload": {"lesson_id": 77, "date": "2026-10-10"}}
    assert notification_action(event, role=role) == {
        "kind": "lesson",
        "role": role,
        "lessonId": "77",
        "lessonDate": "2026-10-10",
    }
