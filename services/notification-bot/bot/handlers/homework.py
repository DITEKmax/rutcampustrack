import logging
from collections import defaultdict
from datetime import date, datetime, timedelta, timezone

import aiohttp
from aiogram import F, Router
from aiogram.filters import Command
from aiogram.types import Message

from bot.handlers.prefs import HOMEWORK_WEEK_LABEL
from bot.services.academic_http_client import TokenExpiredError

logger = logging.getLogger(__name__)

homework_router = Router()

_MOSCOW_TZ = timezone(timedelta(hours=3))


@homework_router.message(Command("homework"))
@homework_router.message(F.text == HOMEWORK_WEEK_LABEL)
async def cmd_homework_week(
    message: Message,
    jwt_redis,
    academic_client,
    academic_http_client,
    today: date | None = None,
) -> None:
    """Show homework for the nearest 7 days for the current Telegram user."""
    telegram_id = message.from_user.id
    tokens = await jwt_redis.get(telegram_id)
    if not tokens:
        await message.answer("🔐 Требуется вход\n\nСначала получите код через /login.")
        return

    try:
        user_response = await academic_client.get_user_by_telegram_id(telegram_id)
        if not user_response.found:
            await message.answer("⚠️ Аккаунт не найден\n\nОбратитесь к старосте, чтобы проверить привязку.")
            return

        group_id = int(getattr(user_response, "group_id", 0) or 0)
        if not group_id:
            await message.answer("⚠️ Группа не указана\n\nВаш аккаунт пока не привязан к учебной группе.")
            return

        access_token = tokens["access_token"]
        semester = await academic_http_client.get_active_semester(access_token)
        if semester is None:
            await message.answer("Сейчас нет активного семестра, поэтому список ДЗ недоступен.")
            return

        semester_id = int(semester["id"])
        homeworks = await academic_http_client.get_homeworks(access_token, group_id=group_id, semester_id=semester_id)
        start = today or datetime.now(_MOSCOW_TZ).date()
        end = start + timedelta(days=6)
        upcoming = _filter_week_homeworks(homeworks, start, end)
        subject_names = await _resolve_subject_names(upcoming, academic_client)

        await message.answer(_build_homework_week_text(upcoming, subject_names, start, end))
    except TokenExpiredError:
        await jwt_redis.delete(telegram_id)
        await message.answer("🔐 Сессия истекла\n\nПолучите новый код через /login.")
    except aiohttp.ClientResponseError as exc:
        logger.warning("Academic REST request failed for homework week: %s", exc)
        if exc.status == 403:
            await message.answer("⚠️ Нет доступа к домашним заданиям для вашей группы.")
        else:
            await message.answer("⚠️ Сервис временно недоступен\n\nПопробуйте ещё раз чуть позже.")
    except Exception:
        logger.warning("Error in /homework handler", exc_info=True)
        await message.answer("⚠️ Сервис временно недоступен\n\nПопробуйте ещё раз чуть позже.")


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
        {
            int(subject_id)
            for item in items
            if (subject_id := _field(item, "subjectId", "subject_id")) is not None
        }
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
        "📚 Домашнее задание на ближайшую неделю",
        f"{_format_date(start)} — {_format_date(end)}",
        "",
    ]
    if not items:
        lines.append("На ближайшие 7 дней домашних заданий нет.")
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
