"""Uncached protected Academic reads and metadata-only exact-card lifecycle calls."""

from __future__ import annotations

from pathlib import Path

import grpc
import grpc.aio

from bot.grpc_client import academic_pb2 as pb
from bot.grpc_client import academic_pb2_grpc
from bot.grpc_client.bot_service_credentials import BotServiceCredentials, canonical_token
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
    ) -> None:
        self._channel = None
        self._stub = None
        self._call_credentials = None
        self._metadata = (("x-grpc-secret", grpc_secret),) if grpc_secret else ()
        # Capability configuration does not alter the existing ordinary bot startup.
        if not tls_enabled or not tls_ca_path or not canonical_token(service_token):
            return
        if type(port) is not int or not 1 <= port <= 65535:
            return
        try:
            authority = f"{host}:{port}"
            plugin = BotServiceCredentials(authority, service_token)
            ca = Path(tls_ca_path).read_bytes()
            if not ca:
                return
            self._channel = grpc.aio.secure_channel(
                authority, grpc.ssl_channel_credentials(root_certificates=ca)
            )
            self._call_credentials = grpc.metadata_call_credentials(plugin)
            self._stub = academic_pb2_grpc.AcademicGrpcServiceStub(self._channel)
        except Exception:
            self._stub = None

    async def _call(self, method: str, request):
        if self._stub is None or self._call_credentials is None:
            raise NotificationPreferencesUnavailable("Protected Academic bot capability unavailable")
        try:
            return await getattr(self._stub, method)(
                request,
                metadata=self._metadata,
                credentials=self._call_credentials,
                timeout=3.0,
            )
        except grpc.aio.AioRpcError as error:
            if error.code() in {
                grpc.StatusCode.PERMISSION_DENIED,
                grpc.StatusCode.FAILED_PRECONDITION,
                grpc.StatusCode.NOT_FOUND,
            }:
                raise BotCredentialRequestDenied("Own credential scope or revision changed") from None
            raise NotificationPreferencesUnavailable("Protected Academic bot authority unavailable") from None
        except Exception:
            raise NotificationPreferencesUnavailable("Protected Academic bot authority unavailable") from None

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
        if self._channel is not None:
            await self._channel.close()
