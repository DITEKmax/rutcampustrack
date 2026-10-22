"""Typed own-read boundary; the bot never chooses a domain actor or group."""

from dataclasses import dataclass
from datetime import date, datetime
from typing import Protocol
from zoneinfo import ZoneInfo


class BotReadUnavailable(RuntimeError):
    """Safe dependency failure without private upstream details."""


class BotReadDenied(BotReadUnavailable):
    """The binding, grant, or selected lesson is no longer eligible."""


@dataclass(frozen=True)
class BotGrant:
    role: str
    group_id: int = 0
    status: str = "ACTIVE"


@dataclass(frozen=True)
class MenuScope:
    user_id: int
    telegram_binding_version: int
    roles_version: int
    grants: tuple[BotGrant, ...]

    @property
    def student_groups(self) -> frozenset[int]:
        return self.groups_for("STUDENT")

    def groups_for(self, role: str) -> frozenset[int]:
        return frozenset(grant.group_id for grant in self.grants if grant.role == role and grant.group_id > 0)

    @property
    def homework_enabled(self) -> bool:
        return len(self.student_groups) == 1

    @property
    def headman_enabled(self) -> bool:
        return bool(self.groups_for("HEADMAN"))

    @property
    def teacher_enabled(self) -> bool:
        return any(grant.role == "TEACHER" for grant in self.grants)

    @property
    def keyboard_options(self) -> dict[str, bool]:
        return dict(
            homework_enabled=self.homework_enabled,
            headman_enabled=self.headman_enabled,
            teacher_enabled=self.teacher_enabled,
        )


@dataclass(frozen=True)
class BotLesson:
    lesson_id: int
    group_name: str
    subject_name: str
    lesson_type: str
    lesson_date: str
    lesson_number: int
    start_time: str
    end_time: str
    room: str
    status: str
    cancelled: bool = False
    moved: bool = False
    one_off: bool = False
    group_id: int = 0
    subject_id: int = 0
    semester_id: int = 0


@dataclass(frozen=True)
class BotAttendanceRow:
    student_id: int
    display_name: str
    status: str
    record_present: bool
    source: str = ""


@dataclass(frozen=True)
class TeacherToday:
    server_now: str
    educational_date: str
    lessons: tuple[BotLesson, ...]


@dataclass(frozen=True)
class HeadmanCurrent:
    server_now: str
    educational_date: str
    state: str
    choices: tuple[BotLesson, ...] = ()
    lesson: BotLesson | None = None
    rows: tuple[BotAttendanceRow, ...] = ()


class BotMenuClient(Protocol):
    async def menu_scope(self, telegram_sender_id: int) -> MenuScope | None: ...
    async def own_profile(self, telegram_sender_id: int): ...
    async def teacher_today(self, telegram_sender_id: int, scope: MenuScope) -> TeacherToday: ...
    async def headman_current(
        self, telegram_sender_id: int, scope: MenuScope, selected_lesson_id: int = 0
    ) -> HeadmanCurrent: ...


async def resolve_menu_scope(client: BotMenuClient | None, sender: int) -> MenuScope | None:
    if client is None or type(sender) is not int or sender <= 0:
        return None
    try:
        result = await client.menu_scope(sender)
        if not isinstance(result, MenuScope) or not all(
            type(value) is int and value > 0
            for value in (result.user_id, result.telegram_binding_version, result.roles_version)
        ):
            return None
        grants = tuple(
            sorted(
                {
                    grant
                    for grant in result.grants
                    if isinstance(grant, BotGrant)
                    and grant.status == "ACTIVE"
                    and grant.role in {"STUDENT", "HEADMAN", "TEACHER", "ADMIN"}
                    and type(grant.group_id) is int
                    and grant.group_id >= 0
                },
                key=lambda grant: (grant.role, grant.group_id),
            )
        )
        return MenuScope(result.user_id, result.telegram_binding_version, result.roles_version, grants)
    except Exception:
        return None


def educational_now(server_now: str, educational_date: str) -> datetime:
    """Validate the authoritative date instead of consulting the worker's clock."""
    try:
        value = datetime.fromisoformat(server_now.replace("Z", "+00:00"))
        if value.tzinfo is None:
            raise ValueError()
        moscow = value.astimezone(ZoneInfo("Europe/Moscow"))
        if date.fromisoformat(educational_date) != moscow.date():
            raise ValueError()
        return moscow
    except (TypeError, ValueError, AttributeError):
        raise BotReadUnavailable("Invalid educational time snapshot") from None
