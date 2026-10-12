"""Directed service identity controls without retained token values."""

import base64
from types import SimpleNamespace

import pytest

from bot.grpc_client.bot_service_credentials import (
    ACADEMIC_SERVICE,
    BotCredentialTransportUnavailable,
    BotServiceCredentials,
    canonical_token,
)


def _token() -> str:
    return base64.urlsafe_b64encode(bytes(range(32))).decode().rstrip("=")


@pytest.mark.parametrize("method", ["GetOwnBotInitialCredentials", "ManageOwnBotCredentialMessages"])
def test_dedicated_token_is_emitted_only_for_exact_service_and_method(method):
    observed = {}

    def callback(metadata, error):
        observed["allowed"] = error is None
        observed["dedicated"] = len(metadata) == 1 and metadata[0][0] == "x-service-token"
        observed["correct"] = len(metadata) == 1 and metadata[0][1] == _token()

    BotServiceCredentials("academic.test:19491", _token())(
        SimpleNamespace(service_url=f"https://academic.test:19491/{ACADEMIC_SERVICE}", method_name=method),
        callback,
    )
    assert all(observed.values())


@pytest.mark.parametrize(
    "service_url,method",
    [
        (f"http://academic.test:19491/{ACADEMIC_SERVICE}", "GetOwnBotInitialCredentials"),
        (f"https://other.test:19491/{ACADEMIC_SERVICE}", "GetOwnBotInitialCredentials"),
        (f"https://academic.test:19492/{ACADEMIC_SERVICE}", "GetOwnBotInitialCredentials"),
        (f"https://academic.test:19491/{ACADEMIC_SERVICE}/", "GetOwnBotInitialCredentials"),
        ("https://academic.test:19491/rutcampustrack.schedule.ScheduleGrpcService", "GetOwnBotInitialCredentials"),
        (f"https://academic.test:19491/{ACADEMIC_SERVICE}", "GetUserByTelegramId"),
        (f"https://academic.test:19491/{ACADEMIC_SERVICE}", ""),
    ],
)
def test_wrong_destination_never_receives_token(service_url, method):
    observed = {}

    def callback(metadata, error):
        observed["empty"] = not metadata
        observed["denied"] = isinstance(error, BotCredentialTransportUnavailable)

    BotServiceCredentials("academic.test:19491", _token())(
        SimpleNamespace(service_url=service_url, method_name=method), callback
    )
    assert all(observed.values())


@pytest.mark.parametrize("value", [None, "", "x", "a" * 44, "=" * 43, " " + "a" * 43])
def test_noncanonical_identity_is_denied(value):
    assert not canonical_token(value)
    with pytest.raises(BotCredentialTransportUnavailable):
        BotServiceCredentials("academic.test:19491", value)
