import { render, screen } from '@testing-library/angular';
import { describe, it, expect } from 'vitest';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { NextLessonCardComponent } from './next-lesson-card.component';
import type { LessonResponse } from '../../shared/student-schedule.types';

const base: LessonResponse = {
  id: 1,
  scheduleItemId: 1,
  groupId: 5,
  subjectId: 7,
  teacherId: 11,
  date: '2026-04-09',
  status: 'PLANNED',
  dayOfWeek: 4,
  lessonNumber: 2,
  startTime: '10:00:00',
  endTime: '11:30:00',
  weekType: 'BOTH',
  room: '404',
  geoBlocked: false,
  cancelReason: null,
  createdAt: '2026-04-01T00:00:00Z',
};

describe('NextLessonCardComponent', () => {
  it('renders "Текущая пара" heading + active class for ACTIVE lesson', async () => {
    const { container } = await render(NextLessonCardComponent, {
      providers: [provideRouter([]), provideNoopAnimations()],
      componentInputs: {
        lesson: { ...base, status: 'ACTIVE' },
        subjectName: 'Математика',
      },
    });
    expect(screen.getByText('Текущая пара')).toBeTruthy();
    expect(screen.getByText('Математика')).toBeTruthy();
    expect(screen.getByText(/10:00.*11:30/)).toBeTruthy();
    expect(screen.getByText(/Ауд\..*404/)).toBeTruthy();
    expect(container.querySelector('.next-lesson-card--active')).toBeTruthy();
  });

  it('renders "Следующая пара" heading WITHOUT active class for PLANNED lesson', async () => {
    const { container } = await render(NextLessonCardComponent, {
      providers: [provideRouter([]), provideNoopAnimations()],
      componentInputs: {
        lesson: { ...base, status: 'PLANNED' },
        subjectName: 'Математика',
      },
    });
    expect(screen.getByText('Следующая пара')).toBeTruthy();
    expect(container.querySelector('.next-lesson-card--active')).toBeNull();
  });

  it('renders "Отметиться" CTA only when lesson is ACTIVE', async () => {
    await render(NextLessonCardComponent, {
      providers: [provideRouter([]), provideNoopAnimations()],
      componentInputs: {
        lesson: { ...base, status: 'ACTIVE' },
        subjectName: 'Математика',
      },
    });
    expect(screen.getByText('Отметиться')).toBeTruthy();
  });

  it('does NOT render "Отметиться" CTA for PLANNED lesson', async () => {
    await render(NextLessonCardComponent, {
      providers: [provideRouter([]), provideNoopAnimations()],
      componentInputs: {
        lesson: { ...base, status: 'PLANNED' },
        subjectName: 'Математика',
      },
    });
    expect(screen.queryByText('Отметиться')).toBeNull();
  });

  it('renders empty variant "Сегодня пар нет" when lesson is null', async () => {
    await render(NextLessonCardComponent, {
      providers: [provideRouter([]), provideNoopAnimations()],
      componentInputs: { lesson: null },
    });
    expect(screen.getByText('Сегодня пар нет')).toBeTruthy();
  });
});
