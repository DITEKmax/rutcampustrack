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


@pytest.mark.parametrize(
    "method", ["GetOwnBotInitialCredentials", "ManageOwnBotCredentialMessages", "GetOwnBotHomeworksForWeek"]
)
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
        (f"https://academic.test:19491/{ACADEMIC_SERVICE}", "GetHomeworksForWeek"),
        (f"https://academic.test:19491/{ACADEMIC_SERVICE}", "GetOwnBotHomeworksForWeekOther"),
        (f"http://academic.test:19491/{ACADEMIC_SERVICE}", "GetOwnBotHomeworksForWeek"),
        (f"https://other.test:19491/{ACADEMIC_SERVICE}", "GetOwnBotHomeworksForWeek"),
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


@pytest.mark.parametrize("tls,ca,token", [(False, "", ""), (True, "", ""), (False, "unused", "")])
def test_legacy_configuration_never_opens_protected_channel(tls, ca, token):
    from bot.grpc_client.bot_credentials_client import BotCredentialsGrpcClient

    client = BotCredentialsGrpcClient("academic.test", 19491, tls_enabled=tls, tls_ca_path=ca, service_token=token)
    assert not client.configured


@pytest.mark.asyncio
async def test_attendance_tls_uses_only_ca_trust_and_preserves_attachment_limits_and_deadline(tmp_path, monkeypatch):
    from unittest.mock import AsyncMock, Mock

    from bot.grpc_client import attendance_client

    ca = tmp_path / "synthetic-ca.pem"
    ca.write_bytes(b"synthetic-ca-marker")
    channel = SimpleNamespace(close=AsyncMock())
    secure = Mock(return_value=channel)
    insecure = Mock(side_effect=AssertionError("TLS must not fall back to plaintext"))
    credentials = object()
    ssl = Mock(return_value=credentials)
    stub = SimpleNamespace(FetchExcuseAttachment=AsyncMock(return_value=object()))
    monkeypatch.setattr(attendance_client.grpc, "ssl_channel_credentials", ssl)
    monkeypatch.setattr(attendance_client.grpc.aio, "secure_channel", secure)
    monkeypatch.setattr(attendance_client.grpc.aio, "insecure_channel", insecure)
    monkeypatch.setattr(
        attendance_client.attendance_pb2_grpc, "AttendanceRequestBotGrpcServiceStub", Mock(return_value=stub)
    )
    client = attendance_client.AttendanceRequestGrpcClient(
        "attendance.test", 19093, "synthetic-shared-secret", 2.0, tls_enabled=True, tls_ca_path=str(ca)
    )
    assert ssl.call_count == 1 and ssl.call_args.kwargs["root_certificates"] == ca.read_bytes()
    assert secure.call_count == 1
    assert secure.call_args.args == ("attendance.test:19093", credentials)
    assert secure.call_args.kwargs == {"options": [("grpc.max_receive_message_length", 24 * 1024 * 1024)]}
    assert not insecure.called
    await client.fetch_excuse_attachment(1, "synthetic-request", "synthetic-attachment")
    assert stub.FetchExcuseAttachment.await_count == 1
    call = stub.FetchExcuseAttachment.call_args
    assert call.kwargs["timeout"] == 2.0
    metadata_matches = call.kwargs["metadata"] == (("x-grpc-secret", "synthetic-shared-secret"),)
    assert metadata_matches
    await client.close()
    assert channel.close.await_count == 1


@pytest.mark.parametrize("missing", ["", "missing-ca.pem"])
def test_attendance_enabled_tls_never_opens_plaintext_when_ca_is_unavailable(tmp_path, monkeypatch, missing):
    from unittest.mock import Mock

    from bot.grpc_client import attendance_client

    insecure = Mock(side_effect=AssertionError("TLS must not fall back to plaintext"))
    secure = Mock(side_effect=AssertionError("No channel may open without configured trust"))
    monkeypatch.setattr(attendance_client.grpc.aio, "secure_channel", secure)
    monkeypatch.setattr(attendance_client.grpc.aio, "insecure_channel", insecure)
    with pytest.raises(ValueError, match="ATTENDANCE_GRPC_TLS_CA_PATH"):
        attendance_client.AttendanceRequestGrpcClient(
            "attendance.test", 19093, tls_enabled=True, tls_ca_path=str(tmp_path / missing) if missing else ""
        )
    assert not secure.called and not insecure.called


@pytest.mark.asyncio
async def test_attendance_legacy_channel_and_positional_timeout_remain_compatible(monkeypatch):
    from unittest.mock import AsyncMock, Mock

    from bot.grpc_client import attendance_client

    channel = SimpleNamespace(close=AsyncMock())
    insecure = Mock(return_value=channel)
    secure = Mock(side_effect=AssertionError("Legacy channel selection changed"))
    stub = SimpleNamespace(ResolveRequestNotification=AsyncMock(return_value=object()))
    monkeypatch.setattr(attendance_client.grpc.aio, "insecure_channel", insecure)
    monkeypatch.setattr(attendance_client.grpc.aio, "secure_channel", secure)
    monkeypatch.setattr(
        attendance_client.attendance_pb2_grpc, "AttendanceRequestBotGrpcServiceStub", Mock(return_value=stub)
    )
    client = attendance_client.AttendanceRequestGrpcClient("attendance.test", 19093, "", 1.5)
    assert insecure.call_args.kwargs == {"options": [("grpc.max_receive_message_length", 24 * 1024 * 1024)]}
    assert not secure.called
    await client.resolve_request_notification(attendance_client.attendance_pb2.STUDENT_REQUEST_KIND_EXCUSE, "synthetic")
    assert stub.ResolveRequestNotification.call_args.kwargs == {"metadata": (), "timeout": 1.5}
    await client.close()
    assert channel.close.await_count == 1


@pytest.mark.asyncio
async def test_attendance_real_tls_accepts_own_ca_and_metadata_but_rejects_wrong_trust_and_hostname():
    """Real loopback transport; this does not claim real Attendance domain authorization."""
    import asyncio
    import subprocess
    import tempfile
    from pathlib import Path

    import grpc

    from bot.grpc_client import attendance_pb2, attendance_pb2_grpc
    from bot.grpc_client.attendance_client import AttendanceRequestGrpcClient

    effects = []
    shared = "synthetic-loopback-metadata"

    class Service(attendance_pb2_grpc.AttendanceRequestBotGrpcServiceServicer):
        async def accepted(self, context):
            matches = tuple(context.invocation_metadata()).count(("x-grpc-secret", shared)) == 1
            if not matches:
                await context.abort(grpc.StatusCode.UNAUTHENTICATED, "Required synthetic metadata missing")

        async def FetchExcuseAttachment(self, request, context):
            await self.accepted(context)
            effects.append("FETCH")
            return attendance_pb2.FetchExcuseAttachmentResponse(
                data=b"known-loopback-body", content_type="text/plain", filename="attachment.txt", size=19
            )

        async def ResolveRequestNotification(self, request, context):
            await self.accepted(context)
            effects.append("RESOLVE")
            return attendance_pb2.ResolveRequestNotificationResponse(group_id=7, student_id=11)

    with tempfile.TemporaryDirectory(prefix="rct-attendance-tls-") as directory:
        folder = Path(directory)
        for name in ("server", "unrelated"):
            # Same stock openssl self-signed/SAN pattern as generate-test-certs.sh.
            subprocess.run(
                [
                    "openssl",
                    "req",
                    "-x509",
                    "-nodes",
                    "-days",
                    "1",
                    "-newkey",
                    "rsa:2048",
                    "-keyout",
                    str(folder / f"{name}.key"),
                    "-out",
                    str(folder / f"{name}.crt"),
                    "-subj",
                    "/CN=localhost",
                    "-addext",
                    "subjectAltName=DNS:localhost",
                ],
                check=True,
                timeout=5,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            (folder / f"{name}.key").chmod(0o600)

        server = grpc.aio.server()
        clients = []
        body_error = None
        cleanup_errors = []
        try:
            attendance_pb2_grpc.add_AttendanceRequestBotGrpcServiceServicer_to_server(Service(), server)
            credentials = grpc.ssl_server_credentials(
                (((folder / "server.key").read_bytes(), (folder / "server.crt").read_bytes()),)
            )
            port = server.add_secure_port("127.0.0.1:0", credentials)
            assert port > 0
            await asyncio.wait_for(server.start(), timeout=2)

            async def exercise():
                good = AttendanceRequestGrpcClient(
                    "localhost", port, shared, 2.0, tls_enabled=True, tls_ca_path=str(folder / "server.crt")
                )
                clients.append(good)
                body = await good.fetch_excuse_attachment(1, "synthetic-request", "synthetic-attachment")
                body_matches = body.data == b"known-loopback-body" and body.size == 19
                assert body_matches
                resolved = await good.resolve_request_notification(
                    attendance_pb2.STUDENT_REQUEST_KIND_EXCUSE, "synthetic-request"
                )
                assert resolved.group_id == 7 and resolved.student_id == 11
                assert effects == ["FETCH", "RESOLVE"]
                for host, ca_name in (("localhost", "unrelated.crt"), ("127.0.0.1", "server.crt")):
                    refused = AttendanceRequestGrpcClient(
                        host, port, shared, 2.0, tls_enabled=True, tls_ca_path=str(folder / ca_name)
                    )
                    clients.append(refused)
                    with pytest.raises(grpc.aio.AioRpcError) as failure:
                        await refused.fetch_excuse_attachment(1, "synthetic-request", "synthetic-attachment")
                    assert failure.value.code() == grpc.StatusCode.UNAVAILABLE
                    assert effects == ["FETCH", "RESOLVE"]
                # The same server still works: refusal was not an unavailable listener.
                await good.resolve_request_notification(attendance_pb2.STUDENT_REQUEST_KIND_EXCUSE, "synthetic-request")
                assert effects == ["FETCH", "RESOLVE", "RESOLVE"]

            await asyncio.wait_for(exercise(), timeout=12)
        except BaseException as error:
            body_error = error
        finally:
            for client in clients:
                try:
                    await asyncio.wait_for(client.close(), timeout=2)
                except BaseException:
                    cleanup_errors.append(RuntimeError("Attendance TLS client cleanup failed"))
            try:
                await asyncio.wait_for(server.stop(0), timeout=2)
            except BaseException:
                cleanup_errors.append(RuntimeError("Attendance TLS server stop failed"))
            try:
                await asyncio.wait_for(server.wait_for_termination(), timeout=2)
            except BaseException:
                cleanup_errors.append(RuntimeError("Attendance TLS server termination failed"))
        if body_error is not None:
            if cleanup_errors:
                raise BaseExceptionGroup("Attendance TLS body and cleanup failed", [body_error, *cleanup_errors])
            raise body_error
        if cleanup_errors:
            raise ExceptionGroup("Attendance TLS cleanup failed", cleanup_errors)
