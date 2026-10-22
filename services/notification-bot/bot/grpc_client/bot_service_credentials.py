"""Separate per-call directed identities for Auth reads and Academic metadata RPCs."""

from __future__ import annotations

import base64
import re

import grpc

ACADEMIC_SERVICE = "rutcampustrack.academic.AcademicGrpcService"
PROTECTED_METHODS = frozenset({"GetOwnBotHomeworksForWeek"})


class BotCredentialTransportUnavailable(RuntimeError):
    """Safe failure containing no token or credential request data."""


def canonical_token(value: object) -> bool:
    if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_-]{43}", value):
        return False
    try:
        decoded = base64.urlsafe_b64decode(value + "=")
    except (ValueError, TypeError):
        return False
    return len(decoded) == 32 and base64.urlsafe_b64encode(decoded).decode().rstrip("=") == value


class BotServiceCredentials(grpc.AuthMetadataPlugin):
    """Never attach the dedicated token to an unrelated destination or method.

    The caller installs this plugin as call credentials on a secure channel,
    separately on each protected call. It is never channel/global metadata.
    """

    _allowed_methods = PROTECTED_METHODS
    _service_name = ACADEMIC_SERVICE
    _header_name = "x-service-token"

    def __init__(self, authority: str, token: str) -> None:
        if not authority or any(char in authority for char in "/@?#\\"):
            raise BotCredentialTransportUnavailable("Protected bot TLS authority is invalid")
        if not canonical_token(token):
            raise BotCredentialTransportUnavailable("Directed bot identity is unavailable")
        self._service_url = f"https://{authority}/{self._service_name}"
        self._token = token

    def __call__(self, context, callback) -> None:
        if context.service_url != self._service_url or context.method_name not in self._allowed_methods:
            callback((), BotCredentialTransportUnavailable("Directed bot identity destination is denied"))
            return
        callback(((self._header_name, self._token),), None)


class BotAuthReadCredentials(BotServiceCredentials):
    """The new Auth credential can accompany only the existing own-read wire method."""

    _allowed_methods = frozenset({"GetOwnBotInitialCredentials", "ManageOwnBotCredentialMessages"})


class BotMenuCredentials(BotServiceCredentials):
    """Own menu/profile only; never attach the bot identity to domain authority checks."""

    _service_name = "rutcampustrack.authauthority.AuthIdentityGrpcService"
    _header_name = "x-service-token"
    _allowed_methods = frozenset({"GetOwnBotMenuScope", "GetOwnBotProfile"})


class BotReadCredentials(BotServiceCredentials):
    """The bot can call only its two public own-read methods on MobileBFF."""

    _service_name = "rutcampustrack.botreads.DirectedBotReadService"
    _allowed_methods = frozenset({"GetOwnTeacherToday", "GetOwnHeadmanCurrentAttendance"})
