/**
 * Тестовые пользователи для Playwright e2e (M08 Группа 5).
 *
 * Логины соответствуют seed'у из `academic-service/.../V2__seed_test_data.sql`:
 * - student / student_test_pass — STUDENT role
 * - teacher / teacher_test_pass — TEACHER role
 * - admin   / admin_test_pass   — ADMIN role
 * - headman / headman_test_pass — STUDENT + is_headman=true
 *
 * Если seed обновляется — синхронизировать эти константы.
 */

export interface TestUser {
  login: string;
  password: string;
  role: 'ADMIN' | 'TEACHER' | 'STUDENT';
  isHeadman?: boolean;
  expectedLandingPath: string;
}

export const TEST_USERS: Record<string, TestUser> = {
  student: {
    login: 'student',
    password: 'student_test_pass',
    role: 'STUDENT',
    expectedLandingPath: '/student/schedule',
  },
  teacher: {
    login: 'teacher',
    password: 'teacher_test_pass',
    role: 'TEACHER',
    expectedLandingPath: '/teacher/schedule',
  },
  admin: {
    login: 'admin',
    password: 'admin_test_pass',
    role: 'ADMIN',
    expectedLandingPath: '/admin/dashboard',
  },
  headman: {
    login: 'headman',
    password: 'headman_test_pass',
    role: 'STUDENT',
    isHeadman: true,
    expectedLandingPath: '/headman/dashboard',
  },
};
