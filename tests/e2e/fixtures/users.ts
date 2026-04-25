/**
 * Тестовые пользователи для Playwright e2e (M08 Группа 5).
 *
 * Логины и пароли соответствуют seed'у из
 * `services/academic-service/academic-app/src/main/resources/db/migration/V2__seed_test_data.sql`:
 * - admin   / password — ADMIN role
 * - teacher / password — TEACHER role
 * - student / password — STUDENT + is_headman=true (старостa в группе ИВТ-211)
 *
 * Все BCrypt хеши в seed — для пароля "password" (cost 10).
 * Если seed обновляется — синхронизировать эти константы.
 *
 * Note: seed создаёт student-старосту (is_headman=true) под логином `student`,
 * отдельного `headman` пользователя нет. Headman-flow тесты используют
 * того же `student` пользователя.
 */

export interface TestUser {
  login: string;
  password: string;
  role: 'ADMIN' | 'TEACHER' | 'STUDENT';
  isHeadman?: boolean;
  expectedLandingPath: string;
}

// Web-panel post-login navigation (login.component.ts:55):
//   ADMIN  → /admin/dashboard
//   TEACHER → /teacher/dashboard
//   STUDENT + isHeadman=true → /headman/dashboard
//   STUDENT + isHeadman=false → /student/dashboard
//
// Seed-юзер `student` имеет is_headman=true → попадает на /headman/dashboard.
// Отдельного non-headman student'а в seed нет.
export const TEST_USERS: Record<string, TestUser> = {
  student: {
    login: 'student',
    password: 'password',
    role: 'STUDENT',
    isHeadman: true,
    expectedLandingPath: '/headman/dashboard',
  },
  teacher: {
    login: 'teacher',
    password: 'password',
    role: 'TEACHER',
    expectedLandingPath: '/teacher/dashboard',
  },
  admin: {
    login: 'admin',
    password: 'password',
    role: 'ADMIN',
    expectedLandingPath: '/admin/dashboard',
  },
  headman: {
    login: 'student',
    password: 'password',
    role: 'STUDENT',
    isHeadman: true,
    expectedLandingPath: '/headman/dashboard',
  },
};
