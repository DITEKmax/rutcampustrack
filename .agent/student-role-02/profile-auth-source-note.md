# Profile/auth — root source evidence before contract

07.09.2026. Final7profileframes captured:4611:142,4611:848928,4611:849055,4614:276,4615:326,4618:535,4618:849228. Security has current/newpasswordform; sessions show current/otherdevice and logoutall; account history has eventcards/more. Theme3choices; role switch alwayspresent evenone role.

Root reopened AuthApi,AuthService/JwtService,AcademicUser/proto,BFFsession and backend-conflictsR6:145. Existing auth has login/refresh/logout/OTP/TMA/change-password; no sessions/history/logoutall/profile public endpoints. refresh tokens Redis refresh:user:jti stringvalid; rotatesdelete+set, sessionmetadata absent. Password change updatesDBhash then RedisKEYS deletesrefresh; activeaccess/internalJWTrevoke notdemonstrated. Auth/access tokens currently single role,AcademicUser singleenum,StudentBFFsession hardcodesSTUDENT and threecapabilities.

AcceptedR6 requires rolearray+perrolestatus,serveractiverole durable acrossreload/tabs; defaultstudentelseteacher,neveradmin; terminalroles read-only. Finalroleframes contain examples student/headman/teacher but do notgrant them to actualuser. Need boundedarchitecturedecision beforeprofilewriter; no localfake role switching or claimalltokensrevoked afterrefresh-onlydeletion. Role/read-only infrastructure shared dependency, otherroleUIimplementation not scope.

R7 cancelledlesson invalidation/archiveHomework also cross-cutting source tocheck duringToday/attendance integration; olddelete-event behavior is not acceptance. No code changed here. This note is evidence, not frozencontract or implementationPASS.

Additional originals: Auth JwtService.generateAccessToken:126 has noSID/jti/token-type; refresh hasjti but sameaudience. Gateway JwtAuthenticationFilter:95 verifies signature/issuer/audience then forwards claims, no session/revoke lookup. InternalJwtIssuerClient cache4min. Active-role/sessionrevocation contract must address gateway and cachedinternalclaims, not onlyAuthRedis records. Do not claim immediate global revocation from current refresh deletion.

ChangePasswordRequest currently @Size8..72 characters and Latinlower/upper/digit regex. If BCryptdependencyupgrade starts rejecting inputs over72UTF8bytes, explicitly test multibytepasswordboundary and typed400 rather than500; do not silently normalize/truncate. Sessions/history UI location labels need authoritative source or omit unavailable location, not hardcode mock Moscow/device.
