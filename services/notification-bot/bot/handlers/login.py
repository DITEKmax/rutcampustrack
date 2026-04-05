import logging

import aiohttp
from aiogram import Router
from aiogram.filters import Command
from aiogram.fsm.context import FSMContext
from aiogram.fsm.state import State, StatesGroup
from aiogram.types import Message

logger = logging.getLogger(__name__)

login_router = Router()


class LoginStates(StatesGroup):
    waiting_for_code = State()


@login_router.message(Command("login"))
async def cmd_login(message: Message, state: FSMContext,
                    auth_client, jwt_redis) -> None:
    """Handle /login command — request OTP (D-05, D-06)."""
    telegram_id = message.from_user.id

    # Check if already logged in
    existing = await jwt_redis.get(telegram_id)
    if existing:
        await message.answer("Вы уже вошли в систему. Используйте /status для проверки.")
        return

    try:
        code = await auth_client.request_otp(telegram_id)
        await message.answer(
            f"Ваш код для входа: {code}\n\n"
            "Введите его в следующем сообщении:"
        )
        await state.set_state(LoginStates.waiting_for_code)

    except aiohttp.ClientResponseError as e:
        if e.status == 429:
            # D-13: rate limit
            await message.answer("Слишком много попыток. Подождите.")
        elif e.status == 401:
            await message.answer(
                "Ваш аккаунт не найден. Обратитесь к старосте."
            )
        else:
            # D-14: service unavailable
            logger.warning("OTP request failed: %s", e)
            await message.answer("Сервис временно недоступен. Попробуйте позже.")
    except Exception:
        logger.warning("OTP request failed unexpectedly", exc_info=True)
        await message.answer("Сервис временно недоступен. Попробуйте позже.")


@login_router.message(LoginStates.waiting_for_code)
async def process_otp_code(message: Message, state: FSMContext,
                           auth_client, jwt_redis) -> None:
    """Handle OTP code input in FSM state (D-05, D-07)."""
    code = message.text.strip() if message.text else ""
    telegram_id = message.from_user.id

    if not code or len(code) != 6 or not code.isdigit():
        await message.answer("Код должен содержать 6 цифр. Попробуйте ещё раз.")
        return

    try:
        tokens = await auth_client.verify_otp(telegram_id, code)
        # D-07: store JWT in Redis
        await jwt_redis.save(
            telegram_id,
            tokens["accessToken"],
            tokens["refreshToken"],
            tokens.get("expiresIn", 0),
        )
        await state.clear()
        await message.answer("Вы успешно вошли в систему! Используйте /status для проверки посещаемости.")

    except aiohttp.ClientResponseError as e:
        if e.status == 401:
            # D-13: invalid code
            await message.answer("Код неверный. Попробуйте ещё раз.")
        else:
            await state.clear()
            logger.warning("OTP verify failed: %s", e)
            await message.answer("Сервис временно недоступен. Попробуйте позже.")
    except Exception:
        await state.clear()
        logger.warning("OTP verify failed unexpectedly", exc_info=True)
        await message.answer("Сервис временно недоступен. Попробуйте позже.")
