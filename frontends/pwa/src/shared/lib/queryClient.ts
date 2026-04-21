import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'
import { getProblemDetails } from '@/api/problemDetails'
import { errorToastBus } from './errorToastBus'

/**
 * Глобальный обработчик ошибок TanStack Query.
 *
 * M07 G4 (QC3): каждый ошибочный query/mutation прогоняется через
 * `getProblemDetails` (парсер RFC 9457 + rename fieldErrors→invalidParams),
 * затем emit'ится в `errorToastBus`. `ErrorToastHost` слушает и показывает
 * toast.
 *
 * Feature-level `onError`-хэндлеры продолжают вызываться — TanStack не
 * блокирует их из-за глобального обработчика. Если feature обработал
 * ошибку контекстно (например, 403 → «Нет прав»), его toast появляется
 * через свой локальный UI; глобальный toast даёт fallback для необрабо-
 * танных ошибок (500, network, 502, etc).
 *
 * Исключения (чтобы не спамить):
 * - `meta.skipGlobalErrorToast: true` — feature явно опресает toast
 *   (например, ожидаемые 404/403 в graceful-degradation хуках).
 */
export const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error, query) => {
      if (query.meta?.['skipGlobalErrorToast']) return
      errorToastBus.emit(getProblemDetails(error))
    },
  }),
  mutationCache: new MutationCache({
    onError: (error, _vars, _ctx, mutation) => {
      if (mutation.meta?.['skipGlobalErrorToast']) return
      errorToastBus.emit(getProblemDetails(error))
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000,
      gcTime: 10 * 60 * 1000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})
