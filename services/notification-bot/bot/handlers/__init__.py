from bot.handlers.login import login_router
from bot.handlers.prefs import prefs_router
from bot.handlers.start import start_router
from bot.handlers.status import status_router

__all__ = ["start_router", "login_router", "status_router", "prefs_router"]
