"""Tests for the student alert notification handler (excuse.decided).

Covers AC-8 (Phase 59): student gets Telegram notification after headman
decides the excuse ticket, with distinct text for approved vs rejected and
graceful handling of malformed payloads (T-59-06-03).
"""

from pathlib import Path

import pytest

from tests.business_delivery import assert_business_delivery

FIXTURE_PATH = Path(__file__).parent / "fixtures" / "excuse_decided.json"


@pytest.mark.asyncio
async def test_approved_event_sends_ru_message_to_student(fake_redis):
    await assert_business_delivery(fake_redis, "excuse.decided", expected=("одобрена",))


@pytest.mark.asyncio
async def test_rejected_event_sends_ru_message_with_comment(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "excuse.decided",
        status="rejected",
        extra={"decision_comment": "private rejection marker"},
        expected=("отклонена",),
        absent=("private rejection marker", "Причина:"),
    )


@pytest.mark.asyncio
async def test_rejected_event_without_comment_does_not_invent_a_reason(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "excuse.decided",
        status="rejected",
        missing_fields=("decision_comment", "comment"),
        expected=("отклонена",),
        absent=("Причина:",),
    )


@pytest.mark.asyncio
async def test_missing_user_id_skips_send_gracefully(fake_redis):
    await assert_business_delivery(fake_redis, "excuse.decided", missing_fields=("user_id",), reject=True)


@pytest.mark.asyncio
async def test_unknown_status_skips_send(fake_redis):
    await assert_business_delivery(fake_redis, "excuse.decided", status="unrecognized-decision", reject=True)


@pytest.mark.asyncio
async def test_student_without_telegram_id_skips_send(fake_redis):
    await assert_business_delivery(fake_redis, "excuse.decided", chats={1: 0}, expected_users=())


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("extra", "expected", "absent"),
    [
        (
            {
                "excluded_lessons": [
                    {"lesson_id": 12, "date": "2026-10-07", "lesson_number": 3, "subject_name": "<b>Математика</b>"}
                ]
            },
            ["частично", "07.10.2026", "Пара №3", "&lt;b&gt;Математика&lt;/b&gt;"],
            ["<b>Математика</b>"],
        ),
        ({"excluded_lesson_ids": [12]}, ["частично", "Исключено занятий: 1"], ["Пара ID 12"]),
        ({"excluded_occurrence_ids": ["102"]}, ["частично", "Исключено занятий: 1"], ["102"]),
        (
            {
                "excluded_lessons": [
                    {"lesson_id": 12, "date": "bad", "lesson_number": "bad"},
                    None,
                    True,
                    {"lesson_id": 15, "subject_name": "Hidden fourth"},
                    16,
                ]
            },
            ["частично", "Исключено занятий: 5", "Ещё исключено: 2"],
            ["Пара ID 12", "Hidden fourth", "bad"],
        ),
        (
            {"excluded_lessons": "invalid", "excluded_lesson_ids": [12]},
            ["частично", "Исключено занятий: 1"],
            ["invalid", "Пара ID 12"],
        ),
        ({"excluded_lessons": []}, ["Уважительная причина одобрена"], ["частично", "Исключённые пары"]),
    ],
)
async def test_partial_approval_escapes_facts_hides_identifiers_and_reports_display_limit(
    extra, expected, absent, fake_redis
):
    excluded = extra.get("excluded_lessons")
    ids = extra.get("excluded_lesson_ids") or extra.get("excluded_occurrence_ids")
    count = len(excluded) if isinstance(excluded, list) and excluded else len(ids) if isinstance(ids, list) else 0
    facts = ("частично", f"Исключено занятий: {count}") if count else ("одобрена",)
    extra = dict(extra)
    if isinstance(excluded, list) and excluded and not ids:
        # Current producer publishes canonical identity lists alongside display snapshots.
        # Malformed untrusted display metadata still cannot leak facts or override this count.
        extra["excluded_lesson_ids"] = list(range(100, 100 + count))

    # Neither raw exclusion identities nor display metadata belongs in the private terminal fact.
    private_ids = extra.get("excluded_lesson_ids") or extra.get("excluded_occurrence_ids") or []
    absent = [*absent, *(str(value) for value in private_ids)]
    await assert_business_delivery(fake_redis, "excuse.decided", extra=extra, expected=facts, absent=absent)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("status", "reason", "expected"),
    [
        ("approved", "present_priority", "уже стоит отметка присутствия"),
        ("rejected", "", "отклонена"),
        ("cancelled", "geo_confirmed", "Присутствие подтверждено по геолокации"),
    ],
)
async def test_late_decision_does_not_treat_excluded_metadata_as_partial(status, reason, expected, fake_redis):
    await assert_business_delivery(
        fake_redis,
        "late_checkin.decided",
        status=status,
        extra={"resolution_reason": reason, "excluded_lessons": [12]},
        expected=(expected,),
        absent=("частично",),
    )
