/** Existing synthetic seed credentials. The server session/grants remain authoritative. */
export interface TestUser {
  login: string;
  password: string;
  /** Requested active role, selected only through an actually granted UI choice. */
  role: 'ADMIN' | 'TEACHER' | 'STUDENT' | 'HEADMAN';
  /** Academic fixture metadata only; never an admitted role or authorization decision. */
  isHeadman?: boolean;
  expectedLandingPath: string;
}

// HEADMAN scenarios reuse this student's identity with explicit role HEADMAN and
// canonical /headman intent. They must prove the server offers that grant; the
// academic seed's is_headman value cannot bypass role selection or route guards.
export const TEST_USERS: Record<string, TestUser> = {
  student: {
    login: 'student',
    password: 'password',
    role: 'STUDENT',
    expectedLandingPath: '/student/home',
  },
  teacher: {
    login: 'teacher',
    password: 'password',
    role: 'TEACHER',
    expectedLandingPath: '/teacher/home',
  },
  // These ADMIN scenarios require the fixture's real current dashboard/registry
  // permissions. Missing grants fail; this fixture never assigns them.
  admin: {
    login: 'admin',
    password: 'password',
    role: 'ADMIN',
    expectedLandingPath: '/admin/dashboard',
  },
};
