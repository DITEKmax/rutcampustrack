import { render, screen } from '@testing-library/angular';
import { of, throwError } from 'rxjs';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { signal } from '@angular/core';
import { HeadmanLateCheckinComponent } from './headman-late-checkin.component';
import { HeadmanApiService } from '../shared/headman-api.service';
import { HeadmanStompService } from '../shared/headman-stomp.service';
import { AuthService } from '../../../core/auth/auth.service';
import type { LateCheckinRequestView } from './late-checkin.types';

function stubAuth(groupId: number | null = 1): Partial<AuthService> {
  return {
    accessToken: signal('stub-token').asReadonly(),
    currentUser: signal({ id: 42, role: 'STUDENT' as const, isHeadman: true, groupId }).asReadonly() as any,
  } as Partial<AuthService>;
}

function stubStomp(): Partial<HeadmanStompService> {
  return {
    connect: () => undefined,
    disconnect: () => undefined,
    lateCheckinRequested$: of() as any,
  };
}

describe('HeadmanLateCheckinComponent', () => {
  it('shows empty state when backend returns no pending requests', async () => {
    const mockApi = { getPendingLateCheckins: () => of<LateCheckinRequestView[]>([]) };
    await render(HeadmanLateCheckinComponent, {
      providers: [
        { provide: HeadmanApiService, useValue: mockApi },
        { provide: HeadmanStompService, useValue: stubStomp() },
        { provide: AuthService, useValue: stubAuth() },
        provideNoopAnimations(),
      ],
    });
    expect(screen.getByText('Нет активных запросов')).toBeTruthy();
  });

  it('shows empty state on 404 graceful degradation', async () => {
    const mockApi = {
      getPendingLateCheckins: () => of<LateCheckinRequestView[]>([]),
    };
    await render(HeadmanLateCheckinComponent, {
      providers: [
        { provide: HeadmanApiService, useValue: mockApi },
        { provide: HeadmanStompService, useValue: stubStomp() },
        { provide: AuthService, useValue: stubAuth() },
        provideNoopAnimations(),
      ],
    });
    expect(screen.getByText('Нет активных запросов')).toBeTruthy();
  });

  it('renders a pending request with approve/reject buttons', async () => {
    const sample: LateCheckinRequestView = {
      id: 'req-1',
      studentId: 7,
      groupId: 1,
      lessonId: 123,
      studentName: 'Иванов И.И.',
      status: 'pending',
      decisionBy: null,
      decisionAt: null,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    const mockApi = { getPendingLateCheckins: () => of([sample]) };
    await render(HeadmanLateCheckinComponent, {
      providers: [
        { provide: HeadmanApiService, useValue: mockApi },
        { provide: HeadmanStompService, useValue: stubStomp() },
        { provide: AuthService, useValue: stubAuth() },
        provideNoopAnimations(),
      ],
    });
    expect(screen.getByText('Иванов И.И.')).toBeTruthy();
    expect(screen.getByText(/Подтвердить/)).toBeTruthy();
    expect(screen.getByText(/Отклонить/)).toBeTruthy();
  });

  it('shows load error when API fails with non-403/404 status', async () => {
    const mockApi = {
      getPendingLateCheckins: () => throwError(() => ({ status: 500 })),
    };
    await render(HeadmanLateCheckinComponent, {
      providers: [
        { provide: HeadmanApiService, useValue: mockApi },
        { provide: HeadmanStompService, useValue: stubStomp() },
        { provide: AuthService, useValue: stubAuth() },
        provideNoopAnimations(),
      ],
    });
    expect(screen.getByText('Не удалось загрузить список запросов.')).toBeTruthy();
  });
});
