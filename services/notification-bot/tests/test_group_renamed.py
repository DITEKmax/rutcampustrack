"""Tests for the group.renamed notification handler (58-07 / BUG-006-6)."""

import pytest

from tests.business_delivery import assert_business_delivery


@pytest.mark.asyncio
async def test_group_renamed_sends_to_students_with_telegram_id(fake_redis):
    await assert_business_delivery(fake_redis, "group.renamed", expected=("Название группы изменено",))


@pytest.mark.asyncio
async def test_group_renamed_message_contains_new_name(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "group.renamed",
        payload_fields=("group_id", "new_name"),
        extra={"new_name": "Новая группа <snapshot>"},
        expected=("Название группы изменено", "Новая группа &lt;snapshot&gt;"),
        absent=("<snapshot>",),
    )


@pytest.mark.asyncio
async def test_group_renamed_missing_group_id_returns_early(fake_redis):
    await assert_business_delivery(fake_redis, "group.renamed", missing_fields=("group_id",), reject=True)


@pytest.mark.asyncio
async def test_group_renamed_grpc_failure_still_notifies_with_fallback(fake_redis):
    await assert_business_delivery(
        fake_redis,
        "group.renamed",
        payload_fields=("group_id", "new_name"),
        extra={"new_name": "Новая группа snapshot"},
        expected=("Название группы изменено", "Новая группа snapshot"),
    )
