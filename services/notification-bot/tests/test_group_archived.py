"""Tests for the group.archived notification handler (58-07 / BUG-006-6)."""

import pytest

from tests.business_delivery import assert_business_delivery


@pytest.mark.asyncio
async def test_group_archived_sends_to_students_with_telegram_id(fake_redis):
    await assert_business_delivery(fake_redis, "group.archived", expected=("Группа snapshot",))


@pytest.mark.asyncio
async def test_group_archived_message_contains_graduation_text_and_name(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "group.archived",
        extra={"group_name": "Выпуск <2026>"},
        expected=("Группа архивирована", "Выпуск &lt;2026&gt;"),
        absent=("<2026>",),
    )


@pytest.mark.asyncio
async def test_group_archived_missing_group_id_returns_early(fake_redis):
    await assert_business_delivery(fake_redis, "group.archived", missing_fields=("group_id",), reject=True)
