# Profile port signatures

The feature boundary uses server-owned decimal string IDs and versions. The UI never persists or interprets the access token returned by role selection.

```ts
interface ProfilePort {
  getSnapshot(): Promise<ProfileSnapshot>
  selectRole(input: { role: ProfileRole; expectedSessionVersion: string }): Promise<ProfileRoleSelection>
  listSessions(input?: { cursor?: string; limit?: number }): Promise<ProfileSessionsPage>
  listHistory(input?: { cursor?: string; limit?: number }): Promise<ProfileHistoryPage>
  changePassword(input: { currentPassword: string; newPassword: string }): Promise<void>
  logoutAll(): Promise<void>
  recoverPassword?(): void | Promise<void>
  navigate?(route: ProfileRoute): void | Promise<void>
  setTheme?(theme: ProfileTheme): void | Promise<void>
  onInvalidated?(reason: 'logout-all' | 'password-changed' | 'account-invalidated'): void | Promise<void>
  onRefreshAlreadyRotated?(error: ProfileRequestError): void | Promise<void>
  isOnline(): boolean
}
```

The frozen transport response for `selectRole` still includes `accessToken` and `expiresIn`; the state module deliberately does not store them. `ProfilePort` is an adapter boundary, not an HTTP implementation.
