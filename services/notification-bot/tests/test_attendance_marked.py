"""Tests for the attendance.marked notification handler (NOTIF-05).

Verifies that when a student checks in (status=present), their reminder
messages are deleted from Telegram and Redis keys are cleared.
For status!=present, no deletion occurs.
"""

import pytest

from tests.business_delivery import assert_business_delivery


@pytest.mark.asyncio
async def test_attendance_marked_headman_present_identifies_mark_and_author(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "attendance.marked",
        status="present",
        extra={"old_status": "absent"},
        expected=("отсутствует → присутствует", "Автор: староста"),
        absent=("(б)",),
    )


@pytest.mark.asyncio
async def test_attendance_marked_present_deletes_messages_and_clears_redis(fake_redis):
    await assert_business_delivery(fake_redis, "attendance.marked", status="present", expected=("присутствует",))


@pytest.mark.asyncio
async def test_attendance_marked_absent_deletes_messages(fake_redis):
    await assert_business_delivery(fake_redis, "attendance.marked", status="absent", expected=("отсутствует",))


@pytest.mark.asyncio
async def test_attendance_marked_excused_deletes_messages(fake_redis):
    await assert_business_delivery(
        fake_redis, "attendance.marked", status="excused", expected=("уважительная причина",)
    )


@pytest.mark.asyncio
async def test_attendance_marked_free_attendance_deletes_messages(fake_redis):
    await assert_business_delivery(
        fake_redis, "attendance.marked", status="free_attendance", expected=("свободное посещение",)
    )


@pytest.mark.asyncio
async def test_attendance_marked_present_silently_ignores_telegram_bad_request(fake_redis):
    await assert_business_delivery(
        fake_redis, "attendance.marked", bad_delete=True, status="present", expected=("присутствует",)
    )


@pytest.mark.asyncio
async def test_attendance_marked_present_no_messages_is_noop(fake_redis):
    await assert_business_delivery(
        fake_redis, "attendance.marked", no_messages=True, status="present", expected=("присутствует",)
    )


@pytest.mark.asyncio
async def test_attendance_marked_missing_user_id_returns_early(fake_redis):
    await assert_business_delivery(fake_redis, "attendance.marked", missing_fields=("user_id",), reject=True)


@pytest.mark.asyncio
async def test_attendance_marked_student_not_in_group_members_is_noop(fake_redis):
    await assert_business_delivery(
        fake_redis, "attendance.marked", empty=True, status="present", expected=("присутствует",)
    )
