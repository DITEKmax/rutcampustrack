"""Local consumer evidence: no Telegram network or dependency installation."""

import asyncio
import sys
from pathlib import Path
from types import ModuleType, SimpleNamespace

sys.dont_write_bytecode = True
worktree = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(worktree / "services/notification-bot"))
# Bot is only an annotation in the handler. Real handler and SendTask are imported.
aiogram = ModuleType("aiogram")
aiogram.Bot = type("Bot", (), {})
sys.modules["aiogram"] = aiogram
from bot.notifications.homework import handle_homework


class Academic:
    async def get_subjects_by_ids(self, ids):
        assert ids == [42]
        return SimpleNamespace(subjects=[SimpleNamespace(subject_name="Математика")])

    async def get_group_members(self, group_id):
        assert group_id == 5
        return [SimpleNamespace(user_id=7, telegram_id=777), SimpleNamespace(user_id=8, telegram_id=0)]


class RecordingBot:
    async def send_message(self, **kwargs):
        self.message = kwargs


class RecordingQueue:
    def __init__(self):
        self.tasks = []

    async def put(self, task):
        self.tasks.append(task)


async def main():
    failures = []
    for event_type in ("homework.published", "homework.updated"):
        for mode, number in (("DATE", None), (None, None), ("DATE", 2), ("LESSON", 2), (None, 2)):
            payload = dict(group_id=5, subject_id=42, title="Задачи 1–5", lesson_date="2026-10-05",
                           lesson_number=number, description=" Реши задачи ", link=" https://example.invalid/hw ")
            if mode:
                payload["binding_mode"] = mode
            bot, queue = RecordingBot(), RecordingQueue()
            await handle_homework(dict(event_type=event_type, payload=payload), bot, Academic(), queue)
            assert len(queue.tasks) == 1
            task = queue.tasks[0]
            assert (task.user_id, task.chat_id, task.category) == (7, 777, "homework")
            await task.coroutine_factory()
            assert bot.message["chat_id"] == 777
            text = bot.message["text"]
            expected_pair = number is not None and mode != "DATE"
            date_ok = "2026-10-05" in text
            pair_ok = ("Пара: №2, 2026-10-05" in text) if expected_pair else ("Пара:" not in text)
            content_ok = all(value in text for value in ("Математика", "Задачи 1–5", "Описание:\nРеши задачи", "Ссылка:\nhttps://example.invalid/hw"))
            result = date_ok and pair_ok and content_ok
            case = f"{event_type} mode={mode} number={number}"
            print(f"{'PASS' if result else 'FAIL'} {case}: date={date_ok} pair={pair_ok} content={content_ok} audience=PASS")
            if mode == "DATE" and number is None:
                print(text)
            if not result:
                failures.append(case)
    print(f"Cases=10 failures={len(failures)}; local handler only, no external Telegram delivery")
    return bool(failures)


raise SystemExit(asyncio.run(main()))
