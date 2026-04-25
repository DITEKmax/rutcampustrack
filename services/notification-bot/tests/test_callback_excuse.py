"""Unit-тесты excuse callback handler (M09 G4.2 + G6.4, 14 P0-2, 06 P1-1).

Проверяют:
- approve → publish excuse.decision(approved=True) + edit_text с «✅ Одобрено»
- reject  → publish excuse.decision(approved=False) + edit_text с «❌ Отклонено»
- document caption path (edit_caption вместо edit_text)
- некорректный callback_data → answer без publish
- publish error → callback.answer с error-текстом, нет edit_text
- отсутствие event_publisher в workflow data → 503 answer
- M09 G6: is_headman=False / found=False / gRPC error → Недостаточно прав,
  publish НЕ вызывается
"""

from unittest.mock import AsyncMock, MagicMock

import pytest

from bot.handlers.excuse import handle_excuse_decision


@pytest.mark.asyncio
async def test_approve_publishes_decision_and_edits_text(
    callback_query_factory, event_publisher_mock, academic_client_mock
):
    cb = callback_query_factory("ex:approve:ticket-42", user_id=777)

    await handle_excuse_decision(cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock)

    event_publisher_mock.publish.assert_awaited_once_with(
        "excuse.decision",
        {"ticket_id": "ticket-42", "approved": True, "decision_by": 777},
    )
    cb.message.edit_text.assert_awaited_once()
    edited_text = cb.message.edit_text.await_args.args[0]
    assert "✅ Одобрено" in edited_text
    assert "Иванов И." in edited_text
    cb.answer.assert_awaited_once_with("✅ Одобрено")


@pytest.mark.asyncio
async def test_reject_publishes_decision_with_approved_false(
    callback_query_factory, event_publisher_mock, academic_client_mock
):
    cb = callback_query_factory("ex:reject:ticket-99", user_id=777)

    await handle_excuse_decision(cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock)

    event_publisher_mock.publish.assert_awaited_once_with(
        "excuse.decision",
        {"ticket_id": "ticket-99", "approved": False, "decision_by": 777},
    )
    edited_text = cb.message.edit_text.await_args.args[0]
    assert "❌ Отклонено" in edited_text
    cb.answer.assert_awaited_once_with("❌ Отклонено")


@pytest.mark.asyncio
async def test_document_caption_path_edits_caption(callback_query_factory, event_publisher_mock, academic_client_mock):
    cb = callback_query_factory(
        "ex:approve:ticket-1",
        message_text="",
        caption="Справка о болезни",
        has_document=True,
    )

    await handle_excuse_decision(cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock)

    cb.message.edit_caption.assert_awaited_once()
    cb.message.edit_text.assert_not_called()
    edited_caption = cb.message.edit_caption.await_args.kwargs["caption"]
    assert "Справка о болезни" in edited_caption
    assert "✅ Одобрено" in edited_caption


@pytest.mark.asyncio
async def test_malformed_callback_data_only_answers(callback_query_factory, event_publisher_mock, academic_client_mock):
    cb = callback_query_factory("ex:bogus")

    await handle_excuse_decision(cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock)

    event_publisher_mock.publish.assert_not_called()
    # Role check должен даже не происходить — shortcut по malformed callback.
    academic_client_mock.get_user_by_telegram_id.assert_not_called()
    cb.answer.assert_awaited_once_with("Некорректный запрос", show_alert=False)


@pytest.mark.asyncio
async def test_wrong_action_verb_only_answers(callback_query_factory, event_publisher_mock, academic_client_mock):
    cb = callback_query_factory("ex:delete:ticket-42")

    await handle_excuse_decision(cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock)

    event_publisher_mock.publish.assert_not_called()
    cb.answer.assert_awaited_once_with("Некорректный запрос", show_alert=False)


@pytest.mark.asyncio
async def test_publisher_error_shows_retry_alert_without_edit(
    callback_query_factory, event_publisher_mock, academic_client_mock
):
    cb = callback_query_factory("ex:approve:ticket-42")
    event_publisher_mock.publish = AsyncMock(side_effect=RuntimeError("rabbit down"))

    await handle_excuse_decision(cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock)

    cb.message.edit_text.assert_not_called()
    cb.answer.assert_awaited_once_with("Не удалось отправить решение, попробуйте ещё раз", show_alert=True)


@pytest.mark.asyncio
async def test_missing_event_publisher_returns_503_like(callback_query_factory, academic_client_mock):
    cb = callback_query_factory("ex:approve:ticket-42")

    await handle_excuse_decision(cb, event_publisher=None, academic_client=academic_client_mock)

    cb.answer.assert_awaited_once_with("Сервис временно недоступен", show_alert=True)
    cb.message.edit_text.assert_not_called()


@pytest.mark.asyncio
async def test_edit_text_failure_still_answers(callback_query_factory, event_publisher_mock, academic_client_mock):
    cb = callback_query_factory("ex:approve:ticket-42")
    cb.message.edit_text = AsyncMock(side_effect=RuntimeError("message too old"))

    await handle_excuse_decision(cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock)

    event_publisher_mock.publish.assert_awaited_once()
    cb.answer.assert_awaited_once_with("✅ Одобрено")


# ================================================== M09 G6 (06 P1-1) role check


@pytest.mark.asyncio
async def test_non_headman_user_gets_denied_without_publish(
    callback_query_factory, event_publisher_mock, academic_client_mock
):
    """Student (linked Telegram, но is_headman=False) → Недостаточно прав."""
    cb = callback_query_factory("ex:approve:ticket-42", user_id=999)
    non_headman = MagicMock()
    non_headman.found = True
    non_headman.is_headman = False
    academic_client_mock.get_user_by_telegram_id = AsyncMock(return_value=non_headman)

    await handle_excuse_decision(cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock)

    academic_client_mock.get_user_by_telegram_id.assert_awaited_once_with(999)
    event_publisher_mock.publish.assert_not_called()
    cb.message.edit_text.assert_not_called()
    cb.answer.assert_awaited_once_with("Недостаточно прав", show_alert=True)


@pytest.mark.asyncio
async def test_unlinked_telegram_user_gets_denied_without_publish(
    callback_query_factory, event_publisher_mock, academic_client_mock
):
    """user.found=False (Telegram не привязан к аккаунту) → отказ."""
    cb = callback_query_factory("ex:approve:ticket-42", user_id=12345)
    not_found = MagicMock()
    not_found.found = False
    not_found.is_headman = False
    academic_client_mock.get_user_by_telegram_id = AsyncMock(return_value=not_found)

    await handle_excuse_decision(cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock)

    event_publisher_mock.publish.assert_not_called()
    cb.answer.assert_awaited_once_with("Недостаточно прав", show_alert=True)


@pytest.mark.asyncio
async def test_grpc_error_fail_closed_denies_without_publish(
    callback_query_factory, event_publisher_mock, academic_client_mock
):
    """Если academic gRPC упал — fail-closed (лучше отказать, чем пропустить)."""
    cb = callback_query_factory("ex:approve:ticket-42")
    academic_client_mock.get_user_by_telegram_id = AsyncMock(side_effect=RuntimeError("academic unreachable"))

    await handle_excuse_decision(cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock)

    event_publisher_mock.publish.assert_not_called()
    cb.answer.assert_awaited_once_with("Не удалось проверить права, попробуйте ещё раз", show_alert=True)


@pytest.mark.asyncio
async def test_missing_academic_client_returns_503_like(callback_query_factory, event_publisher_mock):
    """academic_client=None (misconfigured dispatcher) → Сервис недоступен."""
    cb = callback_query_factory("ex:approve:ticket-42")

    await handle_excuse_decision(cb, event_publisher=event_publisher_mock, academic_client=None)

    event_publisher_mock.publish.assert_not_called()
    cb.answer.assert_awaited_once_with("Сервис временно недоступен", show_alert=True)
