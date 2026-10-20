"""Tests for the headman alert notification handler."""

import pytest

from bot.grpc_client import attendance_pb2 as pb
from tests.business_delivery import assert_business_delivery


@pytest.mark.asyncio
async def test_excuse_requested_sends_only_to_headman(fake_redis):
    await assert_business_delivery(fake_redis, "excuse.requested")


@pytest.mark.asyncio
async def test_excuse_requested_text_contains_student_name_and_excuse_type(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "excuse.requested",
        canonical=True,
        canonical_name="Иван <Иванов>",
        expected=("Иван &lt;Иванов&gt;", "Болезнь"),
    )


@pytest.mark.asyncio
async def test_late_checkin_requested_sends_only_to_headman(fake_redis):
    await assert_business_delivery(fake_redis, "late_checkin.requested")


@pytest.mark.asyncio
async def test_late_checkin_text_contains_canonical_student_name(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "late_checkin.requested",
        canonical=True,
        canonical_name="Василий <Васильев>",
        canonical_date="2026-10-11",
        expected=("Василий &lt;Васильев&gt;", "11.10.2026"),
    )


@pytest.mark.asyncio
async def test_late_checkin_text_contains_subject_and_lesson_number(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "late_checkin.requested",
        canonical=True,
        canonical_subject="Канонический <анализ>",
        canonical_number=3,
        expected=("Канонический &lt;анализ&gt;", "Пара №3"),
    )


@pytest.mark.asyncio
async def test_headman_alert_student_name_from_canonical_request_fixture(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "excuse.requested",
        canonical=True,
        canonical_name="Известный <Студент>",
        expected=("Известный &lt;Студент&gt;",),
        absent=("forged student",),
    )


@pytest.mark.asyncio
async def test_headman_alert_no_headman_with_telegram_skips_send(fake_redis):
    await assert_business_delivery(fake_redis, "excuse.requested", chats={2: 0}, expected_users=())


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("reason", "label"),
    [
        (pb.STUDENT_EXCUSE_REASON_UNIVERSITY_ORDER, "По приказу вуза или иному документу"),
        (pb.STUDENT_EXCUSE_REASON_SUMMONS, "Повестка от военкомата"),
    ],
)
async def test_accepted_reason_from_canonical_rpc_reaches_headman_queue(reason, label, fake_redis):
    await assert_business_delivery(
        fake_redis, "excuse.requested", canonical=True, canonical_reason=reason, expected=(label,)
    )
