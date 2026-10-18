"""Exact bot-only MobileBFF reads; no cached user JWT or caller-chosen role/group/date."""

from collections import Counter
from pathlib import Path

import grpc
import grpc.aio

from bot.grpc_client import directed_bot_reads_pb2 as pb
from bot.grpc_client import directed_bot_reads_pb2_grpc
from bot.grpc_client.bot_service_credentials import BotReadCredentials, canonical_token
from bot.services.bot_menu import (
    BotAttendanceRow,
    BotLesson,
    BotReadDenied,
    BotReadUnavailable,
    HeadmanCurrent,
    MenuScope,
    TeacherToday,
)


class BotReadGrpcClient:
    def __init__(self, host: str, port: int, *, tls_enabled=False, tls_ca_path="", service_token=""):
        self._channel = self._stub = self._credentials = None
        if not tls_enabled or not tls_ca_path or not canonical_token(service_token):
            return
        if type(port) is not int or not 1 <= port <= 65535:
            return
        try:
            authority = f"{host}:{port}"
            plugin = BotReadCredentials(authority, service_token)
            ca = Path(tls_ca_path).read_bytes()
            if not ca:
                return
            self._channel = grpc.aio.secure_channel(authority, grpc.ssl_channel_credentials(root_certificates=ca))
            self._stub = directed_bot_reads_pb2_grpc.DirectedBotReadServiceStub(self._channel)
            self._credentials = grpc.metadata_call_credentials(plugin)
        except Exception:
            self._stub = self._credentials = None

    async def _call(self, method, request):
        if self._stub is None or self._credentials is None:
            raise BotReadUnavailable("Own domain read capability unavailable")
        try:
            return await getattr(self._stub, method)(request, metadata=(), credentials=self._credentials, timeout=3.0)
        except grpc.aio.AioRpcError as error:
            if error.code() in {
                grpc.StatusCode.PERMISSION_DENIED,
                grpc.StatusCode.FAILED_PRECONDITION,
                grpc.StatusCode.NOT_FOUND,
            }:
                raise BotReadDenied("Own domain scope or lesson changed") from None
            raise BotReadUnavailable("Own domain read unavailable") from None
        except Exception:
            raise BotReadUnavailable("Own domain read unavailable") from None

    @staticmethod
    def _identity(sender: int, scope: MenuScope):
        if not isinstance(scope, MenuScope) or any(
            type(value) is not int or value <= 0
            for value in (sender, scope.user_id, scope.telegram_binding_version, scope.roles_version)
        ):
            raise BotReadDenied("Invalid own domain identity")
        return pb.DirectedBotIdentity(
            telegram_sender_id=sender,
            private_chat_id=sender,
            expected_user_id=scope.user_id,
            expected_binding_version=scope.telegram_binding_version,
            expected_roles_version=scope.roles_version,
        )

    async def teacher_today(self, sender: int, scope: MenuScope) -> TeacherToday:
        if not scope.teacher_enabled:
            raise BotReadDenied("Teacher role required")
        response = await self._call("GetOwnTeacherToday", self._identity(sender, scope))
        return TeacherToday(
            response.server_now, response.educational_date, tuple(_lesson(lesson) for lesson in response.lessons)
        )

    async def headman_current(self, sender: int, scope: MenuScope, selected_lesson_id=0) -> HeadmanCurrent:
        if not scope.headman_enabled or type(selected_lesson_id) is not int or selected_lesson_id < 0:
            raise BotReadDenied("Own headman scope required")
        response = await self._call(
            "GetOwnHeadmanCurrentAttendance",
            pb.BotHeadmanCurrentAttendanceRequest(
                identity=self._identity(sender, scope), selected_lesson_id=selected_lesson_id
            ),
        )
        state = {
            pb.BOT_CURRENT_ATTENDANCE_STATE_NO_CURRENT: "NO_CURRENT",
            pb.BOT_CURRENT_ATTENDANCE_STATE_CHOOSE: "CHOOSE",
            pb.BOT_CURRENT_ATTENDANCE_STATE_READY: "READY",
        }.get(response.state)
        if state is None:
            raise BotReadUnavailable("Unknown current lesson state")
        if state == "READY":
            _validate_statistics(response)
        return HeadmanCurrent(
            response.server_now,
            response.educational_date,
            state,
            tuple(_lesson(lesson) for lesson in response.choices),
            _lesson(response.lesson) if response.HasField("lesson") else None,
            tuple(
                BotAttendanceRow(row.student_id, row.display_name, row.status, row.record_present, row.source)
                for row in response.rows
            ),
        )

    async def close(self):
        if self._channel is not None:
            await self._channel.close()


def _lesson(value) -> BotLesson:
    return BotLesson(
        value.lesson_id,
        value.group_name,
        value.subject_name,
        value.lesson_type,
        value.lesson_date,
        value.lesson_number,
        value.start_time,
        value.end_time,
        value.room,
        value.status,
        value.cancelled,
        value.moved,
        value.one_off,
        value.group_id,
        value.subject_id,
        value.semester_id,
    )


def _validate_statistics(response):
    if not response.HasField("statistics"):
        raise BotReadUnavailable("Attendance statistics unavailable")
    counts = Counter(row.status.upper() if row.record_present else "UNMARKED" for row in response.rows)
    expected = dict(
        roster_count=len(response.rows),
        marked_count=sum(row.record_present for row in response.rows),
        present_count=counts["PRESENT"],
        late_count=0,
        absent_count=counts["ABSENT"],
        excused_count=counts["EXCUSED"],
        unmarked_count=counts["UNMARKED"],
        free_attendance_count=counts["FREE_ATTENDANCE"],
        cancelled_count=counts["CANCELLED"],
    )
    if any(getattr(response.statistics, name) != value for name, value in expected.items()):
        raise BotReadUnavailable("Attendance statistics do not match roster snapshot")
