"""Uncached Auth menu/profile capability over exact TLS destination and methods."""

from pathlib import Path

import grpc
import grpc.aio

from bot.grpc_client import auth_authority_pb2 as pb
from bot.grpc_client import auth_authority_pb2_grpc
from bot.grpc_client.bot_service_credentials import BotMenuCredentials, canonical_token
from bot.services.bot_menu import BotGrant, BotReadDenied, BotReadUnavailable, MenuScope


class BotMenuGrpcClient:
    def __init__(self, host: str, port: int, *, tls_enabled=False, tls_ca_path="", service_token="", read_client=None):
        self._channel = self._stub = self._credentials = None
        self._read_client = read_client
        if not tls_enabled or not tls_ca_path or not canonical_token(service_token):
            return
        if type(port) is not int or not 1 <= port <= 65535:
            return
        try:
            authority = f"{host}:{port}"
            plugin = BotMenuCredentials(authority, service_token)
            ca = Path(tls_ca_path).read_bytes()
            if not ca:
                return
            self._channel = grpc.aio.secure_channel(authority, grpc.ssl_channel_credentials(root_certificates=ca))
            self._stub = auth_authority_pb2_grpc.AuthIdentityGrpcServiceStub(self._channel)
            self._credentials = grpc.metadata_call_credentials(plugin)
        except Exception:
            self._stub = self._credentials = None

    async def _call(self, method, request):
        if self._stub is None or self._credentials is None:
            raise BotReadUnavailable("Own Auth capability unavailable")
        try:
            return await getattr(self._stub, method)(request, metadata=(), credentials=self._credentials, timeout=3.0)
        except grpc.aio.AioRpcError as error:
            if error.code() in {
                grpc.StatusCode.PERMISSION_DENIED,
                grpc.StatusCode.FAILED_PRECONDITION,
                grpc.StatusCode.NOT_FOUND,
            }:
                raise BotReadDenied("Own Auth binding or authority changed") from None
            raise BotReadUnavailable("Own Auth authority unavailable") from None
        except Exception:
            raise BotReadUnavailable("Own Auth authority unavailable") from None

    @staticmethod
    def _valid_sender(sender):
        if type(sender) is not int or sender <= 0:
            raise BotReadDenied("Invalid private sender")

    async def menu_scope(self, telegram_sender_id: int) -> MenuScope | None:
        self._valid_sender(telegram_sender_id)
        response = await self._call(
            "GetOwnBotMenuScope",
            pb.GetOwnBotMenuScopeRequest(telegram_sender_id=telegram_sender_id, private_chat_id=telegram_sender_id),
        )
        if not response.found:
            return None
        return MenuScope(
            response.user_id,
            response.telegram_binding_version,
            response.roles_version,
            tuple(BotGrant(grant.role, grant.group_id, grant.status) for grant in response.grants),
        )

    async def own_profile(self, telegram_sender_id: int):
        self._valid_sender(telegram_sender_id)
        response = await self._call(
            "GetOwnBotProfile",
            pb.GetOwnBotProfileRequest(telegram_sender_id=telegram_sender_id, private_chat_id=telegram_sender_id),
        )
        if response.found and (
            not response.login
            or any(
                value <= 0 for value in (response.user_id, response.telegram_binding_version, response.roles_version)
            )
        ):
            raise BotReadUnavailable("Own profile snapshot unavailable")
        return response

    async def teacher_today(self, telegram_sender_id: int, scope: MenuScope):
        if self._read_client is None:
            raise BotReadUnavailable("Own domain read unavailable")
        return await self._read_client.teacher_today(telegram_sender_id, scope)

    async def headman_current(self, telegram_sender_id: int, scope: MenuScope, selected_lesson_id=0):
        if self._read_client is None:
            raise BotReadUnavailable("Own domain read unavailable")
        return await self._read_client.headman_current(telegram_sender_id, scope, selected_lesson_id)

    async def close(self):
        if self._channel is not None:
            await self._channel.close()
