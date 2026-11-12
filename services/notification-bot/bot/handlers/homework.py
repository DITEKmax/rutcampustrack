import logging
from datetime import date, datetime, timedelta, timezone

from aiogram import F, Router
from aiogram.filters import Command
from aiogram.types import CallbackQuery, Message

from bot.handlers.prefs import HOMEWORK_TODAY_LABEL, HOMEWORK_WEEK_LABEL
from bot.handlers.start import private_sender
from bot.services.bot_menu import resolve_menu_scope
from bot.services.homework_pager import CALLBACK_PREFIX, HomeworkPager, format_homework_date, homework_pages

logger = logging.getLogger(__name__)

homework_router = Router()

_MOSCOW_TZ = timezone(timedelta(hours=3))


@homework_router.message(Command("homework"))
@homework_router.message(F.text == HOMEWORK_WEEK_LABEL)
async def cmd_homework_week(
    message: Message,
    academic_client,
    today: date | None = None,
    bot_homework_client=None,
    bot_menu_client=None,
    homework_pager: HomeworkPager | None = None,
) -> None:
    """Show homework for the nearest 7 days for the current Telegram student."""
    await _show_homework(message, academic_client, bot_homework_client, today, 7, bot_menu_client, homework_pager)


@homework_router.message(Command("homework_today"))
@homework_router.message(F.text == HOMEWORK_TODAY_LABEL)
async def cmd_homework_today(
    message: Message,
    academic_client,
    today: date | None = None,
    bot_homework_client=None,
    bot_menu_client=None,
    homework_pager: HomeworkPager | None = None,
) -> None:
    await _show_homework(message, academic_client, bot_homework_client, today, 1, bot_menu_client, homework_pager)


async def _show_homework(
    message: Message,
    academic_client,
    bot_homework_client,
    today: date | None,
    days: int,
    bot_menu_client,
    homework_pager: HomeworkPager | None,
) -> None:
    telegram_id = private_sender(message)
    if telegram_id is None:
        await message.answer("Откройте личный чат с ботом, чтобы посмотреть свои задания.")
        return
    try:
        scope = await resolve_menu_scope(bot_menu_client, telegram_id)
        if scope is None or not scope.homework_enabled:
            await message.answer("ДЗ недоступно. Нужна действующая роль студента с учебной группой.")
            return

        start = today or datetime.now(_MOSCOW_TZ).date()
        end = start + timedelta(days=days - 1)
        semester = await academic_client.get_active_semester()
        if semester is None:
            await message.answer("Сейчас нет активного семестра, поэтому список ДЗ недоступен.")
            return

        if bot_homework_client is None:
            raise RuntimeError("Own bot homework capability unavailable")
        homeworks = [
            {
                "subject_name": homework.subject_name,
                "title": homework.title,
                "description": homework.description,
                "link": homework.link,
                "lesson_date": homework.lesson_date,
                "lesson_number": homework.lesson_number,
                "completed": homework.completed,
            }
            for homework in await bot_homework_client.homeworks(
                telegram_sender_id=telegram_id,
                expected_user_id=scope.user_id,
                expected_roles_version=scope.roles_version,
                expected_telegram_binding_version=scope.telegram_binding_version,
                date_from=start.isoformat(),
                date_to=end.isoformat(),
            )
            if (lesson_date := _parse_date(homework.lesson_date)) is not None and start <= lesson_date <= end
        ]
        period = format_homework_date(start)
        if days == 1:
            title = "📚 Домашнее задание на сегодня"
            empty = "На сегодня домашних заданий нет."
        else:
            title = "📚 Домашнее задание на ближайшую неделю"
            empty = "На ближайшие 7 дней домашних заданий нет."
            period += f" — {format_homework_date(end)}"
        pages = homework_pages(title, period, empty, homeworks)

        if await resolve_menu_scope(bot_menu_client, telegram_id) != scope:
            raise RuntimeError("Own homework authority changed")
        if homework_pager is not None and homework_pager.can_paginate(pages):
            await homework_pager.send(message.answer, sender=telegram_id, scope=scope, pages=pages)
            return
        for index, page in enumerate(pages):
            if index and await resolve_menu_scope(bot_menu_client, telegram_id) != scope:
                raise RuntimeError("Own homework authority changed")
            await message.answer(page, parse_mode=None)
    except Exception:
        logger.warning("Own Telegram homework unavailable")
        await message.answer("⚠️ Сервис временно недоступен\n\nПопробуйте ещё раз чуть позже.")


@homework_router.callback_query(F.data.startswith(CALLBACK_PREFIX))
async def cb_homework_page(callback: CallbackQuery, bot_menu_client=None, homework_pager: HomeworkPager | None = None):
    try:
        changed = homework_pager is not None and await homework_pager.turn(callback, bot_menu_client)
    except Exception:
        logger.warning("Telegram homework page unavailable")
        await callback.answer("Не удалось открыть страницу. Попробуйте ещё раз.", show_alert=True)
        return
    if changed:
        await callback.answer()
    else:
        await callback.answer("Кнопка устарела. Откройте список домашних заданий ещё раз.", show_alert=True)


def _parse_date(value) -> date | None:
    if not isinstance(value, str) or len(value) < 10:
        return None
    try:
        return date.fromisoformat(value[:10])
    except ValueError:
        return None
