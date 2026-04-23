"""Unit-тесты late_checkin callback handler (M09 G4.3 + G6.4, 14 P0-2, 06 P1-1).

Симметричны test_callback_excuse — тот же callback-decision паттерн
(event_type=late_checkin.decision, verdict labels другие) + те же role
check guards (M09 G6).
"""

from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.handlers.late_checkin import handle_late_checkin_decision


@pytest.mark.asyncio
async def test_approve_publishes_decision_and_edits_text(
    callback_query_factory, event_publisher_mock, academic_client_mock
):
    cb = callback_query_factory("lcr:approve:req-42", user_id=777)

    await handle_late_checkin_decision(
        cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock
    )

    event_publisher_mock.publish.assert_awaited_once_with(
        "late_checkin.decision",
        {"request_id": "req-42", "approved": True, "decision_by": 777},
    )
    edited = cb.message.edit_text.await_args.args[0]
    assert "✅ Подтверждено" in edited
    cb.answer.assert_awaited_once_with("✅ Подтверждено")


@pytest.mark.asyncio
async def test_reject_publishes_decision_with_approved_false(
    callback_query_factory, event_publisher_mock, academic_client_mock
):
    cb = callback_query_factory("lcr:reject:req-99", user_id=777)

    await handle_late_checkin_decision(
        cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock
    )

    event_publisher_mock.publish.assert_awaited_once_with(
        "late_checkin.decision",
        {"request_id": "req-99", "approved": False, "decision_by": 777},
    )
    edited = cb.message.edit_text.await_args.args[0]
    assert "❌ Отклонено" in edited
    cb.answer.assert_awaited_once_with("❌ Отклонено")


@pytest.mark.asyncio
async def test_malformed_callback_data_only_answers(
    callback_query_factory, event_publisher_mock, academic_client_mock
):
    cb = callback_query_factory("lcr:bogus")

    await handle_late_checkin_decision(
        cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock
    )

    event_publisher_mock.publish.assert_not_called()
    academic_client_mock.get_user_by_telegram_id.assert_not_called()
    cb.answer.assert_awaited_once_with("Некорректный запрос", show_alert=False)


@pytest.mark.asyncio
async def test_wrong_action_verb_only_answers(
    callback_query_factory, event_publisher_mock, academic_client_mock
):
    cb = callback_query_factory("lcr:delete:req-42")

    await handle_late_checkin_decision(
        cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock
    )

    event_publisher_mock.publish.assert_not_called()
    cb.answer.assert_awaited_once_with("Некорректный запрос", show_alert=False)


@pytest.mark.asyncio
async def test_publisher_error_shows_retry_alert(
    callback_query_factory, event_publisher_mock, academic_client_mock
):
    cb = callback_query_factory("lcr:approve:req-42")
    event_publisher_mock.publish = AsyncMock(side_effect=RuntimeError("rabbit down"))

    await handle_late_checkin_decision(
        cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock
    )

    cb.message.edit_text.assert_not_called()
    cb.answer.assert_awaited_once_with(
        "Не удалось отправить решение, попробуйте ещё раз", show_alert=True
    )


@pytest.mark.asyncio
async def test_missing_event_publisher_returns_503_like(
    callback_query_factory, academic_client_mock
):
    cb = callback_query_factory("lcr:approve:req-42")

    await handle_late_checkin_decision(
        cb, event_publisher=None, academic_client=academic_client_mock
    )

    cb.answer.assert_awaited_once_with("Сервис временно недоступен", show_alert=True)
    cb.message.edit_text.assert_not_called()


@pytest.mark.asyncio
async def test_edit_text_failure_still_answers(
    callback_query_factory, event_publisher_mock, academic_client_mock
):
    cb = callback_query_factory("lcr:approve:req-42")
    cb.message.edit_text = AsyncMock(side_effect=RuntimeError("message too old"))

    await handle_late_checkin_decision(
        cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock
    )

    event_publisher_mock.publish.assert_awaited_once()
    cb.answer.assert_awaited_once_with("✅ Подтверждено")


# ================================================== M09 G6 role check

@pytest.mark.asyncio
async def test_non_headman_user_gets_denied_without_publish(
    callback_query_factory, event_publisher_mock, academic_client_mock
):
    cb = callback_query_factory("lcr:approve:req-42", user_id=999)
    non_headman = MagicMock()
    non_headman.found = True
    non_headman.is_headman = False
    academic_client_mock.get_user_by_telegram_id = AsyncMock(return_value=non_headman)

    await handle_late_checkin_decision(
        cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock
    )

    event_publisher_mock.publish.assert_not_called()
    cb.answer.assert_awaited_once_with("Недостаточно прав", show_alert=True)


@pytest.mark.asyncio
async def test_unlinked_telegram_user_gets_denied_without_publish(
    callback_query_factory, event_publisher_mock, academic_client_mock
):
    cb = callback_query_factory("lcr:approve:req-42")
    not_found = MagicMock()
    not_found.found = False
    not_found.is_headman = False
    academic_client_mock.get_user_by_telegram_id = AsyncMock(return_value=not_found)

    await handle_late_checkin_decision(
        cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock
    )

    event_publisher_mock.publish.assert_not_called()
    cb.answer.assert_awaited_once_with("Недостаточно прав", show_alert=True)


@pytest.mark.asyncio
async def test_grpc_error_fail_closed_denies_without_publish(
    callback_query_factory, event_publisher_mock, academic_client_mock
):
    cb = callback_query_factory("lcr:approve:req-42")
    academic_client_mock.get_user_by_telegram_id = AsyncMock(
        side_effect=RuntimeError("academic unreachable")
    )

    await handle_late_checkin_decision(
        cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock
    )

    event_publisher_mock.publish.assert_not_called()
    cb.answer.assert_awaited_once_with(
        "Не удалось проверить права, попробуйте ещё раз", show_alert=True
    )


@pytest.mark.asyncio
async def test_missing_academic_client_returns_503_like(
    callback_query_factory, event_publisher_mock
):
    cb = callback_query_factory("lcr:approve:req-42")

    await handle_late_checkin_decision(
        cb, event_publisher=event_publisher_mock, academic_client=None
    )

    event_publisher_mock.publish.assert_not_called()
    cb.answer.assert_awaited_once_with("Сервис временно недоступен", show_alert=True)
