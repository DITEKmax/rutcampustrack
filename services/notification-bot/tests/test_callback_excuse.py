"""Unit-тесты excuse callback handler (M09 G4.2, 14 P0-2).

Проверяют:
- approve → publish excuse.decision(approved=True) + edit_text с «✅ Одобрено»
- reject  → publish excuse.decision(approved=False) + edit_text с «❌ Отклонено»
- document caption path (edit_caption вместо edit_text)
- некорректный callback_data → answer без publish
- publish error → callback.answer с error-текстом, нет edit_text
- отсутствие event_publisher в workflow data → 503 answer
"""

from unittest.mock import AsyncMock

import pytest

from bot.handlers.excuse import handle_excuse_decision


@pytest.mark.asyncio
async def test_approve_publishes_decision_and_edits_text(callback_query_factory, event_publisher_mock):
    cb = callback_query_factory("ex:approve:ticket-42", user_id=777)

    await handle_excuse_decision(cb, event_publisher=event_publisher_mock)

    event_publisher_mock.publish.assert_awaited_once_with(
        "excuse.decision",
        {"ticket_id": "ticket-42", "approved": True, "decision_by": 777},
    )
    cb.message.edit_text.assert_awaited_once()
    edited_text = cb.message.edit_text.await_args.args[0]
    assert "✅ Одобрено" in edited_text
    assert "Иванов И." in edited_text  # original text сохранён
    cb.answer.assert_awaited_once_with("✅ Одобрено")


@pytest.mark.asyncio
async def test_reject_publishes_decision_with_approved_false(callback_query_factory, event_publisher_mock):
    cb = callback_query_factory("ex:reject:ticket-99", user_id=777)

    await handle_excuse_decision(cb, event_publisher=event_publisher_mock)

    event_publisher_mock.publish.assert_awaited_once_with(
        "excuse.decision",
        {"ticket_id": "ticket-99", "approved": False, "decision_by": 777},
    )
    edited_text = cb.message.edit_text.await_args.args[0]
    assert "❌ Отклонено" in edited_text
    cb.answer.assert_awaited_once_with("❌ Отклонено")


@pytest.mark.asyncio
async def test_document_caption_path_edits_caption(callback_query_factory, event_publisher_mock):
    cb = callback_query_factory(
        "ex:approve:ticket-1",
        message_text="",
        caption="Справка о болезни",
        has_document=True,
    )

    await handle_excuse_decision(cb, event_publisher=event_publisher_mock)

    cb.message.edit_caption.assert_awaited_once()
    cb.message.edit_text.assert_not_called()
    edited_caption = cb.message.edit_caption.await_args.kwargs["caption"]
    assert "Справка о болезни" in edited_caption
    assert "✅ Одобрено" in edited_caption


@pytest.mark.asyncio
async def test_malformed_callback_data_only_answers(callback_query_factory, event_publisher_mock):
    cb = callback_query_factory("ex:bogus")  # 2 parts, не 3

    await handle_excuse_decision(cb, event_publisher=event_publisher_mock)

    event_publisher_mock.publish.assert_not_called()
    cb.message.edit_text.assert_not_called()
    cb.answer.assert_awaited_once_with("Некорректный запрос", show_alert=False)


@pytest.mark.asyncio
async def test_wrong_action_verb_only_answers(callback_query_factory, event_publisher_mock):
    cb = callback_query_factory("ex:delete:ticket-42")  # неверный verb

    await handle_excuse_decision(cb, event_publisher=event_publisher_mock)

    event_publisher_mock.publish.assert_not_called()
    cb.answer.assert_awaited_once_with("Некорректный запрос", show_alert=False)


@pytest.mark.asyncio
async def test_publisher_error_shows_retry_alert_without_edit(callback_query_factory, event_publisher_mock):
    cb = callback_query_factory("ex:approve:ticket-42")
    event_publisher_mock.publish = AsyncMock(side_effect=RuntimeError("rabbit down"))

    await handle_excuse_decision(cb, event_publisher=event_publisher_mock)

    cb.message.edit_text.assert_not_called()
    cb.answer.assert_awaited_once_with(
        "Не удалось отправить решение, попробуйте ещё раз", show_alert=True
    )


@pytest.mark.asyncio
async def test_missing_event_publisher_returns_503_like(callback_query_factory):
    cb = callback_query_factory("ex:approve:ticket-42")

    # event_publisher=None симулирует Dispatcher, в который не инжектнули publisher.
    await handle_excuse_decision(cb, event_publisher=None)

    cb.answer.assert_awaited_once_with("Сервис временно недоступен", show_alert=True)
    cb.message.edit_text.assert_not_called()


@pytest.mark.asyncio
async def test_edit_text_failure_still_answers(callback_query_factory, event_publisher_mock):
    """Если Telegram отказал в edit (например, сообщение слишком старое),
    handler не должен ронять всё — publish уже прошёл, бот должен хотя бы
    закрыть callback."""
    cb = callback_query_factory("ex:approve:ticket-42")
    cb.message.edit_text = AsyncMock(side_effect=RuntimeError("message too old"))

    await handle_excuse_decision(cb, event_publisher=event_publisher_mock)

    event_publisher_mock.publish.assert_awaited_once()
    cb.answer.assert_awaited_once_with("✅ Одобрено")
