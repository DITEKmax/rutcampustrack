"""Per-call directed identity for the exact protected Academic bot RPCs."""

from __future__ import annotations

import base64
import re

import grpc

ACADEMIC_SERVICE = "rutcampustrack.academic.AcademicGrpcService"
PROTECTED_METHODS = frozenset(
    {"GetOwnBotInitialCredentials", "ManageOwnBotCredentialMessages", "GetOwnBotHomeworksForWeek"}
)


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

    def __init__(self, authority: str, token: str) -> None:
        if not authority or any(char in authority for char in "/@?#\\"):
            raise BotCredentialTransportUnavailable("Academic TLS authority is invalid")
        if not canonical_token(token):
            raise BotCredentialTransportUnavailable("Academic bot identity is unavailable")
        self._service_url = f"https://{authority}/{ACADEMIC_SERVICE}"
        self._token = token

    def __call__(self, context, callback) -> None:
        if context.service_url != self._service_url or context.method_name not in PROTECTED_METHODS:
            callback((), BotCredentialTransportUnavailable("Academic bot identity destination is denied"))
            return
        callback((("x-service-token", self._token),), None)
