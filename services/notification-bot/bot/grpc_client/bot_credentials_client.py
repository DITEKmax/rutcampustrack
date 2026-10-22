"""Uncached private Auth credential/card lifecycle; Academic owns homework."""

from __future__ import annotations

from pathlib import Path

import grpc
import grpc.aio

from bot.grpc_client import academic_pb2 as pb
from bot.grpc_client import academic_pb2_grpc
from bot.grpc_client.bot_service_credentials import BotAuthReadCredentials, BotServiceCredentials, canonical_token
from bot.services.notification_prefs import NotificationPreferencesUnavailable


class BotCredentialRequestDenied(RuntimeError):
    """A safe denied/stale request; never contains provider or credential material."""


class BotCredentialsGrpcClient:
    def __init__(
        self,
        host: str,
        port: int,
        grpc_secret: str = "",
        tls_enabled: bool = False,
        tls_ca_path: str = "",
        service_token: str = "",
        *,
        auth_host: str = "auth-service",
        auth_port: int = 19090,
        auth_tls_enabled: bool = False,
        auth_tls_ca_path: str = "",
        auth_service_token: str = "",
    ) -> None:
        self._metadata = (("x-grpc-secret", grpc_secret),) if grpc_secret else ()
        self._channel, self._stub, self._call_credentials = self._capability(
            host, port, tls_enabled, tls_ca_path, service_token, BotServiceCredentials
        )
        self._auth_channel, self._auth_stub, self._auth_call_credentials = self._capability(
            auth_host, auth_port, auth_tls_enabled, auth_tls_ca_path, auth_service_token, BotAuthReadCredentials
        )

    @staticmethod
    def _capability(host, port, tls_enabled, tls_ca_path, service_token, plugin_type):
        channel = None
        # Capability configuration does not alter the existing ordinary bot startup.
        if not tls_enabled or not tls_ca_path or not canonical_token(service_token):
            return None, None, None
        if type(port) is not int or not 1 <= port <= 65535:
            return None, None, None
        try:
            authority = f"{host}:{port}"
            plugin = plugin_type(authority, service_token)
            ca = Path(tls_ca_path).read_bytes()
            if not ca:
                return None, None, None
            channel = grpc.aio.secure_channel(authority, grpc.ssl_channel_credentials(root_certificates=ca))
            credentials = grpc.metadata_call_credentials(plugin)
            return channel, academic_pb2_grpc.AcademicGrpcServiceStub(channel), credentials
        except Exception:
            return channel, None, None

    @property
    def configured(self) -> bool:
        """Configuration only; this does not promise a reachable or authorized server."""
        return all(value is not None for value in (self._auth_stub, self._auth_call_credentials))

    async def _call(self, method: str, request):
        auth_read = method in {"GetOwnBotInitialCredentials", "ManageOwnBotCredentialMessages"}
        stub = self._auth_stub if auth_read else self._stub
        credentials = self._auth_call_credentials if auth_read else self._call_credentials
        if stub is None or credentials is None:
            raise NotificationPreferencesUnavailable("Protected bot capability unavailable")
        try:
            return await getattr(stub, method)(
                request,
                metadata=() if auth_read else self._metadata,
                credentials=credentials,
                timeout=3.0,
            )
        except grpc.aio.AioRpcError as error:
            if error.code() in {
                grpc.StatusCode.PERMISSION_DENIED,
                grpc.StatusCode.FAILED_PRECONDITION,
                grpc.StatusCode.NOT_FOUND,
            }:
                raise BotCredentialRequestDenied("Own credential scope or revision changed") from None
            raise NotificationPreferencesUnavailable("Protected bot authority unavailable") from None
        except Exception:
            raise NotificationPreferencesUnavailable("Protected bot authority unavailable") from None

    async def read(self, sender: int, user_id: int, *, binding_version=0, credentials_version=0):
        snapshot = binding_version == 0 and credentials_version == 0
        request = pb.BotOwnInitialCredentialsRequest(
            telegram_sender_id=sender,
            private_chat_id=sender,
            expected_user_id=user_id,
            expected_telegram_binding_version=binding_version,
            expected_credentials_version=credentials_version,
            mode=(pb.BOT_CREDENTIAL_READ_MODE_SNAPSHOT if snapshot else pb.BOT_CREDENTIAL_READ_MODE_RECHECK),
        )
        return await self._call("GetOwnBotInitialCredentials", request)

    async def homeworks(
        self,
        *,
        telegram_sender_id,
        expected_user_id,
        expected_roles_version,
        date_from,
        date_to,
        expected_telegram_binding_version=0,
    ):
        request = pb.BotOwnHomeworksForWeekRequest(
            telegram_sender_id=telegram_sender_id,
            expected_user_id=expected_user_id,
            expected_roles_version=expected_roles_version,
            expected_telegram_binding_version=expected_telegram_binding_version,
            date_from=date_from,
            date_to=date_to,
        )
        response = await self._call("GetOwnBotHomeworksForWeek", request)
        return list(response.homeworks)

    async def register(self, *, user_id, chat_id, message_id, binding_version, credentials_version):
        reference = pb.BotCredentialMessageReference(
            user_id=user_id,
            private_chat_id=chat_id,
            message_id=message_id,
            telegram_binding_version=binding_version,
            credentials_version=credentials_version,
        )
        return await self._call(
            "ManageOwnBotCredentialMessages",
            pb.BotCredentialMessagesRequest(register=pb.RegisterBotCredentialMessage(message=reference)),
        )

    async def complete_render(self, reference, outcome):
        return await self._call(
            "ManageOwnBotCredentialMessages",
            pb.BotCredentialMessagesRequest(
                complete_render=pb.CompleteBotCredentialMessageRender(message=reference, outcome=outcome)
            ),
        )

    async def invalidated(self, *, user_id, credentials_version, event_id, after_record_id=0):
        return await self._call(
            "ManageOwnBotCredentialMessages",
            pb.BotCredentialMessagesRequest(
                list_invalidated=pb.ListInvalidatedBotCredentialMessages(
                    user_id=user_id,
                    invalidated_credentials_version=credentials_version,
                    event_id=event_id,
                    after_record_id=after_record_id,
                    page_size=100,
                )
            ),
        )

    async def complete_cleanup(self, reference, *, event_id, outcome):
        return await self._call(
            "ManageOwnBotCredentialMessages",
            pb.BotCredentialMessagesRequest(
                complete_cleanup=pb.CompleteBotCredentialMessageCleanup(
                    message=reference,
                    invalidated_credentials_version=reference.invalidated_credentials_version,
                    event_id=event_id,
                    outcome=outcome,
                )
            ),
        )

    async def close(self) -> None:
        try:
            if self._channel is not None:
                await self._channel.close()
        finally:
            if self._auth_channel is not None:
                await self._auth_channel.close()
