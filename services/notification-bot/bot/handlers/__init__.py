from bot.handlers.excuse import excuse_router
from bot.handlers.homework import homework_router
from bot.handlers.late_checkin import late_checkin_router
from bot.handlers.login import login_router
from bot.handlers.prefs import prefs_router
from bot.handlers.start import start_router
from bot.handlers.status import status_router

__all__ = [
    "start_router",
    "homework_router",
    "login_router",
    "status_router",
    "prefs_router",
    "late_checkin_router",
    "excuse_router",
]
