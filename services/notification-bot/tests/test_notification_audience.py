"""Consumer auth/role boundaries and durable prepared projection; no live providers."""

import asyncio
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from bot.grpc_client import academic_pb2 as pb
from bot.notifications.audience import (
    NotificationDeliveryState,
    audience_request,
    audience_requests,
    handle_business,
    prepare_business,
    prepared_audience,
    queue_request_cleanup,
    resolve,
)
from bot.services.notification_prefs import NOTIFICATION_TYPES, NotificationPreferencesUnavailable
from bot.services.send_queue import TelegramSendQueue

EVENT_ID = "b1e5df89-f73c-4c06-9b3c-231fb56fe41c"


def event(kind="lesson.transferred", **fields):
    scope = {
        "lesson_id": 11,
        "group_id": 7,
        "subject_id": 8,
        "semester_id": 9,
        "lesson_type": "lecture",
        "lesson_date": "2026-10-10",
    }
    payload = {"source": scope, "target": {**scope, "lesson_id": 12}, **fields}
    if kind not in {"lesson.transferred", "lesson.updated"}:
        payload = {**scope, **fields}
    return {
        "event_id": EVENT_ID,
        "event_type": kind,
        "occurred_at": "2026-10-10T13:00:00+03:00",
        "trace_id": "preserved",
        "payload": payload,
    }


def academic(audiences):
    source = iter(audiences)
    last = {}

    async def audience(request):
        nonlocal last
        last = next(source, last)
        return pb.NotificationAudienceResponse(
            request_id=request.request_id,
            event_type=request.event_type,
            recipients=[pb.NotificationAudienceRecipient(user_id=uid, roles=roles) for uid, roles in last.items()],
        )

    async def user(uid):
        return pb.UserResponse(id=uid, telegram_id=100 + uid)

    return SimpleNamespace(
        resolve_notification_audience=AsyncMock(side_effect=audience),
        get_user_by_id=AsyncMock(side_effect=user),
        get_current_group_members=AsyncMock(),
    )


def resources():
    return (
        SimpleNamespace(send_message=AsyncMock(return_value=SimpleNamespace(message_id=1))),
        SimpleNamespace(
            is_student_marked=AsyncMock(return_value=False),
            add_message_id=AsyncMock(),
            mark_student_marked=AsyncMock(),
            get_message_ids=AsyncMock(return_value=[]),
            delete_key=AsyncMock(),
            delete_marked_key=AsyncMock(),
        ),
        SimpleNamespace(completed=AsyncMock(return_value=False), complete=AsyncMock()),
    )


async def deliver(raw, authority, *, held=None):
    prepared = await prepare_business(raw, authority)
    bot, reminders, state = resources()
    prefs = SimpleNamespace(is_enabled=AsyncMock(return_value=True))
    queue = TelegramSendQueue(prefs)
    if held is not None:
        queue._consume_token = held.wait
    queue.start()
    async with queue.staged() as batch:
        await handle_business(
            prepared,
            bot=bot,
            academic=authority,
            queue=queue,
            reminders=reminders,
            config=None,
            state=state,
        )
    return queue, batch, bot, reminders, state, prefs


def test_exact32_and_scope_outcome_identity_no_external_role_override():
    assert len(NOTIFICATION_TYPES) == 32
    assert "ATTENDANCE_RED_ZONE" not in NOTIFICATION_TYPES
    request = audience_request(event())
    assert len(request.lesson_scopes) == 2
    assert [s.lesson_type for s in request.lesson_scopes] == ["lecture", "lecture"]
    assert request.lesson_scopes[1].group_id == 7
    decided = audience_request(event("excuse.decided", user_id=17, status="cancelled", roles=["ADMIN"]))
    assert decided.request_outcome == "cancelled"
    assert decided.affected_user_id == 17
    admin = audience_request(event("admin.access.changed", user_id=77, target_user_id=18))
    assert admin.affected_user_id == 18


@pytest.mark.parametrize(
    "payload",
    [
        {"status": "unknown", "user_id": 1},
        {"status": "cancelled", "user_id": -1},
    ],
)
def test_unknown_request_outcome_or_target_defers(payload):
    with pytest.raises(NotificationPreferencesUnavailable):
        audience_request(event("excuse.decided", **payload))


@pytest.mark.parametrize(
    "recipients",
    [
        [pb.NotificationAudienceRecipient(user_id=0, roles=["STUDENT"])],
        [pb.NotificationAudienceRecipient(user_id=1, roles=["HELPER"])],
        [pb.NotificationAudienceRecipient(user_id=1, roles=[])],
        [pb.NotificationAudienceRecipient(user_id=1, roles=["HEADMAN", "HEADMAN"])],
        [
            pb.NotificationAudienceRecipient(user_id=1, roles=["STUDENT"]),
            pb.NotificationAudienceRecipient(user_id=1, roles=["TEACHER"]),
        ],
    ],
)
async def test_invalid_central_recipient_response_defers(recipients):
    request = audience_request(event())
    client = SimpleNamespace(
        resolve_notification_audience=AsyncMock(
            return_value=pb.NotificationAudienceResponse(
                request_id=request.request_id,
                event_type=request.event_type,
                recipients=recipients,
            )
        )
    )
    with pytest.raises(NotificationPreferencesUnavailable):
        await resolve(client, request)


async def test_mismatched_echo_and_authority_outage_never_fallback():
    request = audience_request(event())
    client = academic([{1: ["TEACHER"]}])
    client.resolve_notification_audience = AsyncMock(
        return_value=pb.NotificationAudienceResponse(
            request_id="wrong",
            event_type=request.event_type,
        )
    )
    with pytest.raises(NotificationPreferencesUnavailable):
        await resolve(client, request)
    client.resolve_notification_audience.side_effect = RuntimeError("local authority unavailable")
    with pytest.raises(NotificationPreferencesUnavailable):
        await prepare_business(event(), client)
    client.get_current_group_members.assert_not_awaited()


async def test_dual_roles_deliver_one_fact_and_success_receipt():
    queue, batch, bot, _, state, prefs = await deliver(event(), academic([{1: ["STUDENT", "TEACHER"]}]))
    try:
        await asyncio.wait_for(batch.wait(), 2)
        bot.send_message.assert_awaited_once()
        state.complete.assert_awaited_once_with(EVENT_ID, 1)
        assert all(
            call.kwargs["notification_type"] == "LESSON_TRANSFERRED" for call in prefs.is_enabled.await_args_list
        )
    finally:
        await queue.shutdown()


def mutable_authority(current_roles):
    async def audience(request):
        return pb.NotificationAudienceResponse(
            request_id=request.request_id,
            event_type=request.event_type,
            recipients=[
                pb.NotificationAudienceRecipient(user_id=uid, roles=roles) for uid, roles in current_roles.items()
            ],
        )

    authority = academic([])
    authority.resolve_notification_audience = AsyncMock(side_effect=audience)
    return authority


async def delivery_at_rate_wait(initial_roles):
    roles = dict(initial_roles)
    authority = mutable_authority(roles)
    waiting, release = asyncio.Event(), asyncio.Event()

    class RateWait:
        async def wait(self):
            waiting.set()
            await release.wait()

    queue, batch, bot, *rest = await deliver(event(), authority, held=RateWait())
    await asyncio.wait_for(waiting.wait(), 2)
    return roles, release, queue, batch, bot, authority


@pytest.mark.parametrize("live", [{}, {1: ["TEACHER"], 2: ["HEADMAN"]}])
async def test_revoked_helper_and_late_new_role_or_user_cannot_join(live):
    roles, release, queue, batch, bot, authority = await delivery_at_rate_wait({1: ["STUDENT"]})
    try:
        roles.clear()
        roles.update(live)
        release.set()
        await asyncio.wait_for(batch.wait(), 2)
        bot.send_message.assert_not_awaited()
        authority.get_current_group_members.assert_not_awaited()
    finally:
        await queue.shutdown()


async def test_privileged_role_loss_uses_remaining_saved_live_role_once():
    roles, release, queue, batch, bot, _ = await delivery_at_rate_wait({1: ["STUDENT", "TEACHER"]})
    try:
        roles[1] = ["TEACHER"]
        release.set()
        await asyncio.wait_for(batch.wait(), 2)
        bot.send_message.assert_awaited_once()
    finally:
        await queue.shutdown()


async def test_off_and_unknown_never_call_provider():
    authority = academic([{1: ["TEACHER"]}])
    prepared = await prepare_business(event(), authority)
    for result in (False, NotificationPreferencesUnavailable("local unknown")):
        bot, reminders, state = resources()
        prefs = SimpleNamespace(
            is_enabled=AsyncMock(
                return_value=result if result is False else None,
                side_effect=result if isinstance(result, Exception) else None,
            )
        )
        queue = TelegramSendQueue(prefs)
        if isinstance(result, Exception):
            with pytest.raises(NotificationPreferencesUnavailable):
                async with queue.staged():
                    await handle_business(
                        prepared,
                        bot=bot,
                        academic=authority,
                        queue=queue,
                        reminders=reminders,
                        config=None,
                        state=state,
                    )
        else:
            async with queue.staged() as batch:
                await handle_business(
                    prepared,
                    bot=bot,
                    academic=authority,
                    queue=queue,
                    reminders=reminders,
                    config=None,
                    state=state,
                )
            assert not batch.futures
        bot.send_message.assert_not_awaited()


async def test_private_owner_decision_has_no_reason_file_comment_or_pii():
    raw = event(
        "excuse.decided",
        user_id=1,
        status="approved",
        student_name="privateName",
        reason_label="privateReason",
        comment="privateComment",
        decision_comment="privateDecision",
        attachments=[{"name": "privateFile"}],
        ticket_id="request-1",
    )
    queue, batch, bot, _, _, _ = await deliver(raw, academic([{1: ["STUDENT"]}]))
    try:
        await asyncio.wait_for(batch.wait(), 2)
        text = bot.send_message.await_args.kwargs["text"]
        assert all(
            value not in text
            for value in [
                "privateName",
                "privateReason",
                "privateComment",
                "privateDecision",
                "privateFile",
            ]
        )
    finally:
        await queue.shutdown()


async def test_automatic_cycle_drops_admin_view_without_dropping_student():
    for roles, sends in ((["ADMIN"], 0), (["ADMIN", "STUDENT"], 1)):
        queue, batch, bot, _, _, _ = await deliver(
            event("group.renamed", notification_cause="AUTOMATIC_GROUP_CYCLE"),
            academic([{1: roles}]),
        )
        try:
            await asyncio.wait_for(batch.wait(), 2)
            assert bot.send_message.await_count == sends
        finally:
            await queue.shutdown()


async def test_partial_delivery_receipt_and_prepared_snapshot_survive_replay(fake_redis):
    state = NotificationDeliveryState(fake_redis)
    raw = event()
    authority = academic([{1: ["TEACHER"]}])
    prepared = await prepare_business(raw, authority)
    await state.complete(EVENT_ID, 1)
    assert await state.completed(EVENT_ID, 1)
    # A new process reconstructs snapshot from immutable broker bytes, never Redis.
    fake_redis_snapshot = await fake_redis.get(f"notification-bot:audience:{EVENT_ID}")
    assert fake_redis_snapshot is None
    assert prepared_audience(prepared) == {1: frozenset({"TEACHER"})}
    authority = academic([{1: ["TEACHER"], 2: ["ADMIN"]}])
    bot, reminders, _ = resources()
    queue = TelegramSendQueue(SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    async with queue.staged() as batch:
        await handle_business(
            prepared,
            bot=bot,
            academic=authority,
            queue=queue,
            reminders=reminders,
            config=None,
            state=state,
        )
    assert not batch.futures
    bot.send_message.assert_not_awaited()


def test_prepared_roles_are_closed_minimal_and_cannot_disagree_with_event():
    raw = event()
    with pytest.raises(ValueError):
        prepared_audience(raw)
    raw["_bot_prepared"] = {
        "version": 1,
        "event_id": "different",
        "event_type": raw["event_type"],
        "recipients": [],
    }
    with pytest.raises(ValueError):
        prepared_audience(raw)


def test_partial_legacy_scopes_use_factual_group_union_without_teacher_tuple():
    raw = event()
    raw["payload"]["source"].pop("semester_id")
    raw["payload"]["target"].pop("semester_id")
    raw["payload"]["target"]["group_id"] = 17
    requests = audience_requests(raw)
    assert [request.group_id for request in requests] == [7, 17]
    assert all(not request.lesson_scopes for request in requests)
    raw["payload"]["source"]["subject_id"] = 0
    with pytest.raises(NotificationPreferencesUnavailable):
        audience_requests(raw)


async def test_duplicate_across_factual_groups_merges_roles_into_one_recipient():
    raw = event()
    raw["payload"]["source"].pop("semester_id")
    raw["payload"]["target"].pop("semester_id")
    raw["payload"]["target"]["group_id"] = 17
    authority = academic([{1: ["STUDENT"]}, {1: ["HEADMAN"]}])
    prepared = await prepare_business(raw, authority)
    assert prepared_audience(prepared) == {1: frozenset({"STUDENT", "HEADMAN"})}


async def test_pending_headman_preserves_canonical_callbacks_without_private_reason():
    from bot.grpc_client import attendance_pb2 as apb

    raw = event("excuse.requested", user_id=2, ticket_id="request-1", comment="privateReason")
    authority = academic([{1: ["HEADMAN"]}])
    prepared = await prepare_business(raw, authority)
    context = apb.ResolveRequestNotificationResponse(
        group_id=7,
        student_id=2,
        detail=apb.StudentRequestDetail(
            summary=apb.StudentRequestSummary(
                id="request-1",
                kind=apb.STUDENT_REQUEST_KIND_EXCUSE,
                status=apb.STUDENT_REQUEST_STATUS_PENDING,
            )
        ),
    )
    attendance = SimpleNamespace(resolve_request_notification=AsyncMock(return_value=context))
    bot, reminders, state = resources()
    queue = TelegramSendQueue(SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    queue.start()
    try:
        async with queue.staged() as batch:
            await handle_business(
                prepared,
                bot=bot,
                academic=authority,
                queue=queue,
                reminders=reminders,
                config=None,
                state=state,
                attendance=attendance,
            )
        await asyncio.wait_for(batch.wait(), 2)
        kwargs = bot.send_message.await_args.kwargs
        assert "privateReason" not in kwargs["text"]
        buttons = kwargs["reply_markup"].inline_keyboard[-1]
        assert [button.callback_data for button in buttons] == ["ex:approve:request-1", "ex:reject:request-1"]
    finally:
        await queue.shutdown()


@pytest.mark.parametrize("actor,sends", [(1, 0), (None, 1), (True, 1)])
async def test_external_own_sync_suppression_requires_positive_verified_actor(actor, sends):
    queue, batch, bot, _, _, _ = await deliver(
        event("lesson.cancelled", actor_user_id=actor),
        academic([{1: ["TEACHER"]}]),
    )
    try:
        await asyncio.wait_for(batch.wait(), 2)
        assert bot.send_message.await_count == sends
    finally:
        await queue.shutdown()


async def test_personal_rights_change_is_not_suppressed_as_own_sync_success():
    raw = event("headman.access.changed", user_id=1, actor_user_id=1, before_active=True, after_active=False)
    queue, batch, bot, _, _, _ = await deliver(raw, academic([{1: ["STUDENT"]}]))
    try:
        await asyncio.wait_for(batch.wait(), 2)
        bot.send_message.assert_awaited_once()
    finally:
        await queue.shutdown()


def test_complete_transfer_groups_keep_required_primary_and_only_matching_scopes():
    raw = event()
    raw["payload"]["target"]["group_id"] = 17
    requests = audience_requests(raw)
    assert [request.group_id for request in requests] == [7, 17]
    assert [[scope.group_id for scope in request.lesson_scopes] for request in requests] == [[7], [17]]


@pytest.mark.parametrize("actor", [None, 0, True, "1"])
def test_semester_terminal_unknown_actor_defers(actor):
    with pytest.raises(NotificationPreferencesUnavailable):
        audience_request(event("semester.operation.completed", actor_user_id=actor))


def test_semester_terminal_actor_is_affected_user_and_non_teacher_events_have_no_scopes():
    request = audience_request(event("semester.operation.completed", actor_user_id=17))
    assert request.affected_user_id == 17
    assert not request.lesson_scopes
    for kind in ("lesson.started", "lesson.closed", "lesson.reminder", "excuse.requested"):
        assert not audience_request(event(kind, user_id=1)).lesson_scopes


@pytest.mark.parametrize("kind", ["lesson.transferred", "lesson.updated", "semester.operation.completed"])
async def test_async_completed_outcome_keeps_initiator(kind):
    queue, batch, bot, _, _, _ = await deliver(event(kind, actor_user_id=1), academic([{1: ["ADMIN"]}]))
    try:
        await asyncio.wait_for(batch.wait(), 2)
        bot.send_message.assert_awaited_once()
    finally:
        await queue.shutdown()


@pytest.mark.parametrize("outcome", ["approved", "rejected"])
@pytest.mark.parametrize("gate", ["allowed", "revoked", "off", "muted", "binding"])
async def test_terminal_owner_fact_separately_retires_only_authorized_existing_cards(outcome, gate):
    from bot.grpc_client import attendance_pb2 as apb

    raw = event("excuse.decided", user_id=2, ticket_id="request-1", status=outcome, comment="privateDecision")
    source = academic([])

    async def audience(request):
        recipients = {2: ["STUDENT"]} if request.event_type == "excuse.decided" else {1: ["HEADMAN"]}
        if request.event_type == "excuse.requested" and gate == "revoked":
            recipients = {}
        return pb.NotificationAudienceResponse(
            request_id=request.request_id,
            event_type=request.event_type,
            recipients=[
                pb.NotificationAudienceRecipient(user_id=uid, roles=roles) for uid, roles in recipients.items()
            ],
        )

    source.resolve_notification_audience.side_effect = audience
    prepared = await prepare_business(raw, source)
    context = apb.ResolveRequestNotificationResponse(
        group_id=7,
        student_id=2,
        detail=apb.StudentRequestDetail(
            summary=apb.StudentRequestSummary(
                id="request-1",
                kind=apb.STUDENT_REQUEST_KIND_EXCUSE,
                status=getattr(apb, f"STUDENT_REQUEST_STATUS_{outcome.upper()}"),
            )
        ),
    )
    attendance = SimpleNamespace(resolve_request_notification=AsyncMock(return_value=context))
    entry = {"user_id": 1, "chat_id": 101, "message_id": 55}
    tracker = SimpleNamespace(get_all=AsyncMock(return_value=[entry]), delete_entry=AsyncMock())
    bot, reminders, state = resources()
    bot.edit_message_reply_markup = AsyncMock()

    async def enabled(chat_id, category, *, user_id, notification_type):
        assert notification_type == f"EXCUSE_{outcome.upper()}"
        # Existing preference gate combines OFF, mute and current exact binding.
        return user_id != 1 or gate not in {"off", "muted", "binding"}

    queue = TelegramSendQueue(SimpleNamespace(is_enabled=AsyncMock(side_effect=enabled)))
    queue.start()
    try:
        async with queue.staged() as batch:
            await handle_business(
                prepared,
                bot=bot,
                academic=source,
                queue=queue,
                reminders=reminders,
                config=None,
                state=state,
                attendance=attendance,
                tracker=tracker,
            )
        await asyncio.wait_for(batch.wait(), 2)
        bot.send_message.assert_awaited_once()
        assert bot.send_message.await_args.kwargs["chat_id"] == 102
        assert "privateDecision" not in bot.send_message.await_args.kwargs["text"]
        if gate == "allowed":
            bot.edit_message_reply_markup.assert_awaited_once_with(chat_id=101, message_id=55, reply_markup=None)
            tracker.delete_entry.assert_awaited_once_with("excuse", "request-1", entry)
        else:
            bot.edit_message_reply_markup.assert_not_awaited()
            tracker.delete_entry.assert_not_awaited()
    finally:
        await queue.shutdown()


@pytest.mark.parametrize("failure", ["authority", "identity", "edit"])
async def test_cleanup_unknown_or_uncertain_edit_keeps_original_card_for_retry(failure):
    from bot.grpc_client import attendance_pb2 as apb
    from bot.services.send_queue import TelegramDeliveryFailed

    raw = event("excuse.decided", user_id=2, ticket_id="request-1", status="approved")
    source = academic([{1: ["HEADMAN"]}])
    context = apb.ResolveRequestNotificationResponse(
        group_id=7,
        student_id=2,
        detail=apb.StudentRequestDetail(
            summary=apb.StudentRequestSummary(
                id="request-1",
                kind=apb.STUDENT_REQUEST_KIND_EXCUSE,
                status=apb.STUDENT_REQUEST_STATUS_APPROVED,
            )
        ),
    )
    if failure == "identity":
        context.student_id = 3
    if failure == "authority":
        source.resolve_notification_audience.side_effect = RuntimeError("synthetic unknown")
    attendance = SimpleNamespace(resolve_request_notification=AsyncMock(return_value=context))
    tracker = SimpleNamespace(
        get_all=AsyncMock(return_value=[{"user_id": 1, "chat_id": 101, "message_id": 55}]),
        delete_entry=AsyncMock(),
    )
    bot = SimpleNamespace(edit_message_reply_markup=AsyncMock(side_effect=RuntimeError("synthetic edit unknown")))
    queue = TelegramSendQueue(SimpleNamespace(is_enabled=AsyncMock(return_value=True)))
    queue._RETRY_DELAYS = []
    queue.start()
    try:
        if failure == "edit":
            async with queue.staged() as batch:
                await queue_request_cleanup(
                    raw, bot=bot, academic=source, queue=queue, attendance=attendance, tracker=tracker
                )
            with pytest.raises(TelegramDeliveryFailed):
                await asyncio.wait_for(batch.wait(), 2)
            bot.edit_message_reply_markup.assert_awaited_once()
        else:
            with pytest.raises(NotificationPreferencesUnavailable):
                async with queue.staged():
                    await queue_request_cleanup(
                        raw, bot=bot, academic=source, queue=queue, attendance=attendance, tracker=tracker
                    )
            bot.edit_message_reply_markup.assert_not_awaited()
        tracker.delete_entry.assert_not_awaited()
    finally:
        await queue.shutdown()


@pytest.mark.parametrize(
    "app_url, has_button",
    [
        ("https://localhost:18514/mini-app/", False),
        ("https://LOCALHOST./mini-app/", False),
        ("https://127.0.0.1:18514/mini-app/", False),
        ("https://[::1]/mini-app/", False),
        ("https://10.0.0.1/mini-app/", False),
        ("https://notification-web/mini-app/", False),
        ("https://[malformed/", False),
        ("https://bad host.site/mini-app/", False),
        ("https://ruttrack..site/mini-app/", False),
        ("https://ruttrack.site../mini-app/", False),
        ("https://ruttrack.site.../mini-app/", False),
        ("https://.ruttrack.site/mini-app/", False),
        ("https://-ruttrack.site/mini-app/", False),
        ("https://ruttrack-.site/mini-app/", False),
        ("https://rut_track.site/mini-app/", False),
        ("https://" + "a" * 64 + ".site/mini-app/", False),
        ("https://" + ".".join(["a" * 63] * 4) + "/mini-app/", False),
        ("https://" + "я" * 64 + ".рф/mini-app/", False),
        ("https://app.localhost/mini-app/", False),
        ("https://app.local/mini-app/", False),
        ("https://app.internal/mini-app/", False),
        ("https://192.168.1.1/mini-app/", False),
        ("https://[fc00::1]/mini-app/", False),
        ("https://[::ffff:127.0.0.1]/mini-app/", False),
        ("https://127.1/mini-app/", False),
        ("https://ruttrack.site./mini-app/", True),
        ("https://пример.рф/mini-app/", True),
        ("https://" + "a" * 63 + ".site/mini-app/", True),
        ("https://" + ".".join(["a" * 63] * 3 + ["a" * 61]) + "/mini-app/", True),
        ("https://ruttrack.site/mini-app/", True),
        ("https://t.me/ruttrack_bot/ruttrack", True),
    ],
)
async def test_personal_delivery_keeps_text_and_receipt_without_unavailable_app_button(app_url, has_button):
    raw = event("headman.access.changed", user_id=17, actor_user_id=1, before_active=True, after_active=False)
    authority = academic([{17: ["STUDENT"]}])
    prepared = await prepare_business(raw, authority)
    bot, reminders, state = resources()
    prefs = SimpleNamespace(is_enabled=AsyncMock(return_value=True))
    queue = TelegramSendQueue(prefs)
    queue.start()
    try:
        async with queue.staged() as batch:
            await handle_business(
                prepared,
                bot=bot,
                academic=authority,
                queue=queue,
                reminders=reminders,
                config=SimpleNamespace(mini_app_web_url=app_url),
                state=state,
            )
        await batch.wait()
        bot.send_message.assert_awaited_once()
        options = bot.send_message.call_args.kwargs
        assert "Права старосты изменены" in options["text"]
        assert "Доступ отозван" in options["text"]
        assert ("reply_markup" in options) is has_button
        if has_button:
            button = options["reply_markup"].inline_keyboard[0][0]
            if "t.me" in app_url:
                assert button.url == app_url and button.web_app is None
            else:
                assert button.web_app.url == app_url and button.url is None
        state.complete.assert_awaited_once_with(EVENT_ID, 17)
        assert prefs.is_enabled.await_count > 0
        assert authority.resolve_notification_audience.await_count > 1
    finally:
        await queue.shutdown()
