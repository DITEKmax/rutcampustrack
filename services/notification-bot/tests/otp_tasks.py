"""Own only cleanup tasks scheduled by otp_requested; never inspect the event loop."""

import asyncio
from types import SimpleNamespace

import pytest_asyncio


@pytest_asyncio.fixture
async def otp_tasks(monkeypatch):
    from bot.notifications import otp_requested

    tasks = []
    waits = []
    entered = asyncio.Event()
    release = asyncio.Event()
    sentinel_release = asyncio.Event()
    sentinel = asyncio.create_task(sentinel_release.wait())

    async def expiry_sleep(delay):
        waits.append(delay)
        entered.set()
        await release.wait()

    def schedule(coro):
        task = asyncio.create_task(coro)
        tasks.append(task)
        return task

    monkeypatch.setattr(
        otp_requested,
        "asyncio",
        SimpleNamespace(
            create_task=schedule,
            sleep=expiry_sleep,
            CancelledError=asyncio.CancelledError,
        ),
    )
    harness = SimpleNamespace(tasks=tasks, waits=waits, entered=entered, release=release)
    try:
        yield harness
    finally:
        for task in tasks:
            task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)
        assert not sentinel.done(), "OTP cleanup cancelled an unrelated task"
        sentinel_release.set()
        await sentinel
