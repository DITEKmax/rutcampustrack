import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/angular';
import { userEvent } from '@testing-library/user-event';
import { StudentPwaBannerComponent } from './student-pwa-banner.component';
import { StudentBannerService } from '../../../features/student/shared/student-banner.service';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../../core/auth/auth.service';

const makeMockBannerService = (show: boolean) => ({
  shouldShow: signal(show),
  init: vi.fn(),
  dismiss: vi.fn(),
});

describe('StudentPwaBannerComponent', () => {
  beforeEach(() => {
    localStorage.removeItem('pwa-banner-dismissed');
  });

  it('не рендерит баннер когда shouldShow() = false', async () => {
    const mockService = makeMockBannerService(false);
    await render(StudentPwaBannerComponent, {
      providers: [
        provideNoopAnimations(),
        { provide: StudentBannerService, useValue: mockService },
      ],
    });
    expect(screen.queryByRole('banner')).toBeNull();
  });

  it('рендерит баннер когда shouldShow() = true', async () => {
    const mockService = makeMockBannerService(true);
    await render(StudentPwaBannerComponent, {
      providers: [
        provideNoopAnimations(),
        { provide: StudentBannerService, useValue: mockService },
      ],
    });
    expect(screen.getByRole('banner')).toBeTruthy();
    expect(screen.getByText('Установите RutTrack на главный экран')).toBeTruthy();
  });

  it('клик × вызывает bannerService.dismiss()', async () => {
    const mockService = makeMockBannerService(true);
    const user = userEvent.setup();
    await render(StudentPwaBannerComponent, {
      providers: [
        provideNoopAnimations(),
        { provide: StudentBannerService, useValue: mockService },
      ],
    });
    await user.click(screen.getByLabelText('Закрыть баннер'));
    expect(mockService.dismiss).toHaveBeenCalledOnce();
  });
});

describe('StudentBannerService', () => {
  beforeEach(() => {
    localStorage.removeItem('pwa-banner-dismissed');
    TestBed.resetTestingModule();
  });

  it('shouldShow() = false если dismissed = true', () => {
    localStorage.setItem('pwa-banner-dismissed', 'true');
    TestBed.configureTestingModule({
      providers: [
        StudentBannerService,
        {
          provide: AuthService,
          useValue: { currentUser: signal({ role: 'STUDENT', isHeadman: false, id: 1, username: 'student', groupId: 1 }) },
        },
      ],
    });
    const service = TestBed.inject(StudentBannerService);
    service.init();
    expect(service.shouldShow()).toBe(false);
  });

  it('shouldShow() = false для TEACHER', () => {
    TestBed.configureTestingModule({
      providers: [
        StudentBannerService,
        {
          provide: AuthService,
          useValue: { currentUser: signal({ role: 'TEACHER', isHeadman: false, id: 2, username: 'teacher', groupId: null }) },
        },
      ],
    });
    const service = TestBed.inject(StudentBannerService);
    service.init();
    expect(service.shouldShow()).toBe(false);
  });

  it('shouldShow() = true для STUDENT без dismissed flag', () => {
    TestBed.configureTestingModule({
      providers: [
        StudentBannerService,
        {
          provide: AuthService,
          useValue: { currentUser: signal({ role: 'STUDENT', isHeadman: false, id: 1, username: 'student', groupId: 1 }) },
        },
      ],
    });
    const service = TestBed.inject(StudentBannerService);
    service.init();
    expect(service.shouldShow()).toBe(true);
  });

  it('dismiss() устанавливает localStorage flag и shouldShow = false', () => {
    TestBed.configureTestingModule({
      providers: [
        StudentBannerService,
        {
          provide: AuthService,
          useValue: { currentUser: signal({ role: 'STUDENT', isHeadman: false, id: 1, username: 'student', groupId: 1 }) },
        },
      ],
    });
    const service = TestBed.inject(StudentBannerService);
    service.init();
    service.dismiss();
    expect(localStorage.getItem('pwa-banner-dismissed')).toBe('true');
    expect(service.shouldShow()).toBe(false);
  });
});
