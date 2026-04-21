/**
 * Глобальная шина для RFC 9457 ProblemDetails → toast.
 *
 * M07 G4: TanStack Query QueryCache/MutationCache вызывают `emit()` на
 * каждом query/mutation error. `ErrorToastProvider` подписывается через
 * `subscribe()` и показывает toast. Без DI — прямой EventTarget, т.к.
 * провайдер один на всё приложение.
 *
 * Feature-level `onError`-хэндлеры продолжают работать — TanStack вызывает
 * и их, и глобальный onError. Это by design: глобальный даёт fallback
 * (если feature не показал свою ошибку), а feature — контекстные
 * сообщения типа «Нет прав на действие».
 */

import type { ProblemDetails } from '@/api/problemDetails'

type Listener = (problem: ProblemDetails) => void

const listeners = new Set<Listener>()

export const errorToastBus = {
  emit(problem: ProblemDetails): void {
    listeners.forEach((listener) => {
      try {
        listener(problem)
      } catch {
        /* swallow — не ломаем остальных подписчиков из-за одного бага */
      }
    })
  },

  subscribe(listener: Listener): () => void {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },
}
