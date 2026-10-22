import logging
from collections import defaultdict
from datetime import date, datetime, timedelta, timezone

from aiogram import F, Router
from aiogram.filters import Command
from aiogram.types import Message

from bot.handlers.prefs import HOMEWORK_TODAY_LABEL, HOMEWORK_WEEK_LABEL
from bot.handlers.start import private_sender
from bot.services.bot_menu import resolve_menu_scope

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
) -> None:
    """Show homework for the nearest 7 days for the current Telegram student."""
    await _show_homework(message, academic_client, bot_homework_client, today, days=7, bot_menu_client=bot_menu_client)


@homework_router.message(Command("homework_today"))
@homework_router.message(F.text == HOMEWORK_TODAY_LABEL)
async def cmd_homework_today(
    message: Message, academic_client, today: date | None = None, bot_homework_client=None, bot_menu_client=None
) -> None:
    await _show_homework(message, academic_client, bot_homework_client, today, days=1, bot_menu_client=bot_menu_client)


async def _show_homework(
    message: Message, academic_client, bot_homework_client, today: date | None, *, days: int, bot_menu_client=None
) -> None:
    telegram_id = private_sender(message)
    if telegram_id is None:
        await message.answer("Открой личный чат с ботом, чтобы посмотреть свои задания.")
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
            _homework_proto_to_item(homework)
            for homework in await bot_homework_client.homeworks(
                telegram_sender_id=telegram_id,
                expected_user_id=scope.user_id,
                expected_roles_version=scope.roles_version,
                expected_telegram_binding_version=scope.telegram_binding_version,
                date_from=start.isoformat(),
                date_to=end.isoformat(),
            )
        ]
        upcoming = _filter_week_homeworks(homeworks, start, end)

        if await resolve_menu_scope(bot_menu_client, telegram_id) != scope:
            raise RuntimeError("Own homework authority changed")
        await message.answer(_build_homework_week_text(upcoming, {}, start, end))
    except Exception:
        logger.warning("Error in /homework handler", exc_info=True)
        await message.answer("⚠️ Сервис временно недоступен\n\nПопробуйте ещё раз чуть позже.")


def _homework_proto_to_item(homework) -> dict:
    return {
        "id": getattr(homework, "homework_id", 0),
        "subjectId": getattr(homework, "subject_id", 0),
        "subjectName": getattr(homework, "subject_name", ""),
        "title": getattr(homework, "title", ""),
        "description": getattr(homework, "description", ""),
        "link": getattr(homework, "link", ""),
        "lessonDate": getattr(homework, "lesson_date", ""),
        "lessonNumber": getattr(homework, "lesson_number", 0),
        "completed": bool(getattr(homework, "completed", False)),
    }


def _filter_week_homeworks(items: list[dict], start: date, end: date) -> list[dict]:
    result = []
    for item in items:
        lesson_date = _parse_date(_field(item, "lessonDate", "lesson_date", "dueDate"))
        if lesson_date is None or lesson_date < start or lesson_date > end:
            continue
        result.append(item)
    return sorted(
        result,
        key=lambda hw: (
            _field(hw, "lessonDate", "lesson_date", "dueDate") or "",
            _field(hw, "lessonNumber", "lesson_number") or 0,
            _field(hw, "title") or "",
        ),
    )


async def _resolve_subject_names(items: list[dict], academic_client) -> dict[int, str]:
    subject_ids = sorted(
        {int(subject_id) for item in items if (subject_id := _field(item, "subjectId", "subject_id")) is not None}
    )
    if not subject_ids:
        return {}
    try:
        response = await academic_client.get_subjects_by_ids(subject_ids)
        return {int(subject.subject_id): subject.subject_name for subject in response.subjects}
    except Exception:
        logger.warning("Failed to resolve subject names for homework week", exc_info=True)
        return {}


def _build_homework_week_text(items: list[dict], subject_names: dict[int, str], start: date, end: date) -> str:
    lines = [
        "📚 Домашнее задание на сегодня" if start == end else "📚 Домашнее задание на ближайшую неделю",
        f"{_format_date(start)} — {_format_date(end)}",
        "",
    ]
    if not items:
        lines.append(
            "На сегодня домашних заданий нет." if start == end else "На ближайшие 7 дней домашних заданий нет."
        )
        return "\n".join(lines)

    by_date: dict[str, list[dict]] = defaultdict(list)
    for item in items:
        by_date[str(_field(item, "lessonDate", "lesson_date", "dueDate") or "")].append(item)

    for raw_date in sorted(by_date):
        lines.append(_format_date(raw_date))
        for item in by_date[raw_date]:
            lines.extend(_format_homework_item(item, subject_names))
        lines.append("")

    return _truncate("\n".join(lines).strip())


def _format_homework_item(item: dict, subject_names: dict[int, str]) -> list[str]:
    subject_id = _field(item, "subjectId", "subject_id")
    subject_name = _compact(_field(item, "subjectName", "subject_name"))
    if not subject_name:
        subject_name = subject_names.get(int(subject_id), "Предмет") if subject_id is not None else "Предмет"
    lesson_number = _field(item, "lessonNumber", "lesson_number")
    lesson_part = f" · пара №{lesson_number}" if lesson_number else ""
    completed = bool(_field(item, "completed"))
    marker = "✅" if completed else "⬜"
    title = _compact(_field(item, "title")) or "Домашнее задание"

    lines = [f"{marker} {subject_name}{lesson_part}", f"   {title}"]
    description = _compact(_field(item, "description"))
    if description:
        lines.append(f"   {description}")
    link = _compact(_field(item, "link"))
    if link:
        lines.append(f"   {link}")
    return lines


def _field(item: dict, *names: str):
    for name in names:
        if name in item:
            return item.get(name)
    return None


def _compact(value) -> str:
    if not isinstance(value, str):
        return ""
    return " ".join(value.split())


def _parse_date(value) -> date | None:
    if not isinstance(value, str) or len(value) < 10:
        return None
    try:
        return date.fromisoformat(value[:10])
    except ValueError:
        return None


def _format_date(value) -> str:
    if isinstance(value, date):
        return value.strftime("%d.%m.%Y")
    if isinstance(value, str) and len(value) >= 10 and value[4] == "-" and value[7] == "-":
        return f"{value[8:10]}.{value[5:7]}.{value[0:4]}"
    return str(value or "")


def _truncate(text: str, limit: int = 3900) -> str:
    if len(text) <= limit:
        return text
    return text[: limit - 16].rstrip() + "\n\n..."
