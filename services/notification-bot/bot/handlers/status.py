import logging

from aiogram import Router
from aiogram.filters import Command
from aiogram.types import Message

from bot.services.attendance_http_client import TokenExpiredError

logger = logging.getLogger(__name__)

status_router = Router()

# Russian status labels
_STATUS_LABELS = {
    "present": "Присутствует",
    "absent": "Отсутствует",
    "excused": "Уважительная причина",
    "free_attendance": "Свободное посещение",
}


@status_router.message(Command("status"))
async def cmd_status(
    message: Message,
    jwt_redis,
    schedule_client,
    attendance_client,
    academic_client,
) -> None:
    """Handle /status command — show current lesson and attendance (D-08 to D-11)."""
    telegram_id = message.from_user.id

    # D-11: check for stored JWT
    tokens = await jwt_redis.get(telegram_id)
    if not tokens:
        await message.answer("🔐 Требуется вход\n\nСначала получите код через /login.")
        return

    try:
        # Look up user to get group_id
        user_response = await academic_client.get_user_by_telegram_id(telegram_id)
        if not user_response.found:
            await message.answer("⚠️ Аккаунт не найден\n\nОбратитесь к старосте, чтобы проверить привязку.")
            return

        group_id = user_response.group_id
        if not group_id:
            await message.answer("⚠️ Группа не указана\n\nВаш аккаунт пока не привязан к учебной группе.")
            return

        # D-08: get active lesson
        lesson = await schedule_client.get_active_lesson(group_id)
        if lesson is None:
            await message.answer("🕒 Сейчас нет активной пары")
            return

        # Resolve subject name via Academic gRPC (Pitfall 7 from RESEARCH.md)
        subject_name = "Неизвестный предмет"
        try:
            subjects_resp = await academic_client.get_subjects_by_ids([lesson.subject_id])
            if subjects_resp.subjects:
                subject_name = subjects_resp.subjects[0].subject_name
        except Exception:
            logger.warning("Failed to resolve subject name for id=%d", lesson.subject_id)

        # D-09: get attendance status
        attendance_status = "Не отмечен"
        try:
            records = await attendance_client.get_student_records(tokens["access_token"])
            for record in records:
                if record.get("lessonId") == lesson.id:
                    raw_status = record.get("status", "")
                    attendance_status = _STATUS_LABELS.get(raw_status, raw_status)
                    break
        except TokenExpiredError:
            # Expired JWT — delete from Redis, ask to re-login (Pitfall 8)
            await jwt_redis.delete(telegram_id)
            await message.answer("🔐 Сессия истекла\n\nПолучите новый код через /login.")
            return
        except Exception:
            logger.warning("Failed to fetch attendance records", exc_info=True)
            attendance_status = "Не удалось получить"

        # D-10: format status message
        time_range = f"{lesson.start_time} — {lesson.end_time}"
        await message.answer(
            f"📚 Текущая пара\n\n"
            f"Предмет: {subject_name}\n"
            f"Аудитория: {lesson.room}\n"
            f"Время: {time_range}\n"
            f"Статус: {attendance_status}"
        )

    except Exception:
        # D-14: service unavailability
        logger.warning("Error in /status handler", exc_info=True)
        await message.answer("⚠️ Сервис временно недоступен\n\nПопробуйте ещё раз чуть позже.")
