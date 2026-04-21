import { Component, type ErrorInfo, type ReactNode } from 'react'

/**
 * Верхнеуровневый React error boundary. Ловит runtime-ошибки в render/
 * effects дочерних компонентов — TanStack Query errors через сюда НЕ
 * проходят (они доступны через useQuery().error + errorToastBus).
 *
 * M07 G4: вместо белого экрана показываем offline-friendly fallback с
 * кнопкой «Перезагрузить».
 */
interface Props {
  children: ReactNode
}

interface State {
  error: Error | null
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // Консоль — единственный канал до наличия Sentry. Logger TBD в v0.1.
    console.error('[ErrorBoundary]', error, info)
  }

  private handleReload = () => {
    this.setState({ error: null })
    window.location.reload()
  }

  render(): ReactNode {
    if (!this.state.error) return this.props.children

    return (
      <div
        role="alert"
        className="fixed inset-0 flex flex-col items-center justify-center gap-4 p-6 text-center"
        style={{ background: 'var(--bg-primary)', color: 'var(--text-primary)' }}
      >
        <h1
          className="text-xl font-semibold"
          style={{ fontFamily: 'var(--font-heading)' }}
        >
          Что-то сломалось
        </h1>
        <p className="max-w-sm text-sm" style={{ color: 'var(--text-secondary)' }}>
          Мы уже получили уведомление. Попробуй перезагрузить страницу —
          если не поможет, напиши в поддержку с кодом ниже.
        </p>
        <code
          className="max-w-full overflow-hidden text-ellipsis rounded-lg px-3 py-1.5 text-xs"
          style={{
            background: 'var(--bg-surface)',
            color: 'var(--text-muted)',
            border: '1px solid var(--border-subtle)',
          }}
        >
          {this.state.error.message || 'неизвестная ошибка'}
        </code>
        <button
          type="button"
          onClick={this.handleReload}
          className="rounded-full px-5 py-2 text-sm font-semibold"
          style={{
            background: 'var(--accent-primary)',
            color: 'var(--accent-primary-contrast)',
          }}
        >
          Перезагрузить
        </button>
      </div>
    )
  }
}
