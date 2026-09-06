import { computed, type Ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { applyCheckinAck } from '../../domain/checkin'
import type { StudentApi } from '../../api/student-client'
import type { StudentCheckinAck, StudentCheckinCommand, StudentToday } from '../../api/types'

export function useToday(api: StudentApi, enabled: Ref<boolean>, offline: Ref<boolean>) {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: ['student', 'today'],
    queryFn: () => api.getToday(),
    enabled: computed(() => enabled.value && !offline.value),
    retry: 1,
  })
  const mutation = useMutation({
    mutationFn: ({ lessonId, command, key }: { lessonId: string; command: StudentCheckinCommand; key: string }) => api.checkin(lessonId, command, key),
    onSuccess: (ack: StudentCheckinAck) => {
      queryClient.setQueryData<StudentToday>(['student', 'today'], (current) => current ? applyCheckinAck(current, ack) : current)
      void queryClient.invalidateQueries({ queryKey: ['student', 'today'] })
    },
  })
  return { query, mutation }
}
