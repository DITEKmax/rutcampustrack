import type { RouteLocationRaw } from 'vue-router'
import {
  executeNotificationAction,
  notificationNumericId,
} from '../../../../mobile-core/src/features/notifications/notifications-actions'
import type { NotificationAction } from '../../../../mobile-core/src/features/notifications/notifications-v2-models'
import { TeacherApi } from '../../../../mobile-core/src/features/teacher/teacher-client'
import { HeadmanRequestsApi } from '../../../../mobile-core/src/features/headman-requests/headman-requests-client'
import { HeadmanJournalApi } from '../../../../mobile-core/src/features/headman-journal/headman-journal-client'
import { HeadmanGroupApi } from '../../../../mobile-core/src/features/headman-group/headman-group-client'
import { createStudentApi, createWebTransport } from '../../app/api'
import { router } from '../../app/router'
import { webSession } from '../../auth/session'
import { addHomeworkDays } from '../../../../mobile-core/src/domain/homework'
export async function openWebNotification(action: NotificationAction): Promise<boolean> {
  const transport = createWebTransport()
  return executeNotificationAction(
    action,
    () => ({
      generation: webSession.generation.value,
      userId: webSession.snapshot.value?.userId ?? '',
      role: webSession.snapshot.value?.activeRole ?? null,
      authorityKey: webSession.identity.value ?? '',
      online: navigator.onLine,
    }),
    {
      async open(value, isCurrent) {
        let destination: RouteLocationRaw | null = null
        if (value.role === 'STUDENT' && value.kind === 'homework') {
          const page = await createStudentApi().getHomework(value.lessonDate, value.lessonDate)
          const item = page.items.find((item) => item.id === value.homeworkId)
          if (!item) return false
          destination = {
            name: 'student-homework',
            query: { date: item.lessonDate, mode: 'day' },
            hash: '#homework-' + item.id,
          }
        } else if (value.role === 'STUDENT' && value.kind === 'homework-week') {
          await createStudentApi().getHomework(value.weekStart, addHomeworkDays(value.weekStart, 6))
          destination = { name: 'student-homework', query: { date: value.weekStart, mode: 'week' } }
        } else if (value.role === 'TEACHER' && value.kind === 'lesson') {
          const id = notificationNumericId(value.lessonId)
          if (id === null) return false
          const lesson = await new TeacherApi(transport).lesson(id)
          if (lesson.lesson.id !== id) return false
          destination = { name: 'teacher-lesson', params: { lessonId: String(id) } }
        } else if (value.role === 'HEADMAN' && value.kind === 'request') {
          await new HeadmanRequestsApi(transport).get(value.requestId)
          destination = { name: 'headman-requests', query: { request: value.requestId } }
        } else if (value.role === 'HEADMAN' && value.kind === 'lesson') {
          const id = notificationNumericId(value.lessonId)
          if (id === null) return false
          const lesson = await new HeadmanJournalApi(transport).getLessonAttendance(id)
          if (!lesson.lessonDate) return false
          destination = {
            name: 'headman-attendance',
            query: { lesson: String(id), date: lesson.lessonDate },
          }
        } else if (value.role === 'HEADMAN' && value.kind === 'group') {
          const id = notificationNumericId(value.groupId)
          if (
            id === null ||
            !webSession.snapshot.value?.roles.some(
              (grant) => grant.role === 'HEADMAN' && grant.groupId === value.groupId,
            )
          )
            return false
          await new HeadmanGroupApi(transport).listMembers(id)
          destination = { name: 'headman-group' }
        }
        transport.assertCurrent()
        if (
          !isCurrent() ||
          !destination ||
          typeof destination !== 'object' ||
          !('name' in destination) ||
          !destination.name ||
          !router.hasRoute(destination.name)
        )
          return false
        await router.push(destination)
        return isCurrent()
      },
    },
  )
}
