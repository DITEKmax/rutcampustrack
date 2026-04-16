import { render, screen, fireEvent } from '@testing-library/angular';
import { describe, it, expect, vi } from 'vitest';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { LessonRowComponent } from './lesson-row.component';
import type { LessonResponse } from '../../shared/student-schedule.types';

const baseLesson: LessonResponse = {
  id: 42,
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

describe('LessonRowComponent', () => {
  it('renders subject name, formatted start/end times, room and status chip', async () => {
    await render(LessonRowComponent, {
      providers: [provideNoopAnimations()],
      componentInputs: { lesson: baseLesson, subjectName: 'Математика' },
    });

    expect(screen.getByText('Математика')).toBeTruthy();
    expect(screen.getByText('10:00')).toBeTruthy();
    expect(screen.getByText('11:30')).toBeTruthy();
    expect(screen.getByText(/Ауд\.\s*404/)).toBeTruthy();
    // planned status chip shows the literal 'Пара'
    expect(screen.getByText('Пара')).toBeTruthy();
  });

  it('emits toggle with lesson id on row click', async () => {
    const toggle = vi.fn();
    const { fixture } = await render(LessonRowComponent, {
      providers: [provideNoopAnimations()],
      componentInputs: { lesson: baseLesson, subjectName: 'Математика' },
    });
    fixture.componentInstance.toggle.subscribe(toggle);

    fireEvent.click(screen.getByRole('button'));
    expect(toggle).toHaveBeenCalledWith(42);
  });

  it('renders inline detail panel with lesson number when expanded', async () => {
    await render(LessonRowComponent, {
      providers: [provideNoopAnimations()],
      componentInputs: {
        lesson: baseLesson,
        subjectName: 'Математика',
        expanded: true,
      },
    });

    expect(screen.getByText('Номер пары')).toBeTruthy();
    expect(screen.getByText('Тип')).toBeTruthy();
    expect(screen.getByText(/Пара\s*№\s*2/)).toBeTruthy();
    expect(screen.getByText('Длительность')).toBeTruthy();
    // row button's aria-expanded reflects the input
    const rowButton = document.querySelector('button.lesson-row') as HTMLButtonElement;
    expect(rowButton.getAttribute('aria-expanded')).toBe('true');
    // expanded panel also exposes the two action buttons
    expect(screen.getByText('Заявить об уважительной причине')).toBeTruthy();
    expect(screen.getByText('Заявить о посещении')).toBeTruthy();
  });

  it('hides the detail panel when expanded = false', async () => {
    await render(LessonRowComponent, {
      providers: [provideNoopAnimations()],
      componentInputs: {
        lesson: baseLesson,
        subjectName: 'Математика',
        expanded: false,
      },
    });

    expect(screen.queryByText('Номер пары')).toBeNull();
    expect(screen.getByRole('button').getAttribute('aria-expanded')).toBe('false');
  });

  it('applies lesson-row--active class for ACTIVE lessons', async () => {
    const { container } = await render(LessonRowComponent, {
      providers: [provideNoopAnimations()],
      componentInputs: {
        lesson: { ...baseLesson, status: 'ACTIVE' },
        subjectName: 'Математика',
      },
    });

    expect(container.querySelector('.lesson-row--active')).toBeTruthy();
    // station dot still rendered
    expect(container.querySelector('.lesson-row__dot')).toBeTruthy();
  });

  it('applies lesson-row--cancelled class and does NOT emit toggle for CANCELLED lessons', async () => {
    const toggle = vi.fn();
    const { fixture, container } = await render(LessonRowComponent, {
      providers: [provideNoopAnimations()],
      componentInputs: {
        lesson: { ...baseLesson, status: 'CANCELLED' },
        subjectName: 'Математика',
      },
    });
    fixture.componentInstance.toggle.subscribe(toggle);

    expect(container.querySelector('.lesson-row--cancelled')).toBeTruthy();

    fireEvent.click(screen.getByRole('button'));
    expect(toggle).not.toHaveBeenCalled();
  });

  it('shows "+" for a present lesson regardless of source', async () => {
    await render(LessonRowComponent, {
      providers: [provideNoopAnimations()],
      componentInputs: {
        lesson: baseLesson,
        subjectName: 'Математика',
        personalStatus: 'present',
        personalSource: 'late_checkin',
      },
    });
    const chip = screen.getByText('+');
    expect(chip.className).toContain('status-chip--present');
  });

  it('uses a personal attendance status chip when personalStatus is provided', async () => {
    await render(LessonRowComponent, {
      providers: [provideNoopAnimations()],
      componentInputs: {
        lesson: baseLesson,
        subjectName: 'Математика',
        personalStatus: 'present',
      },
    });

    const chip = screen.getByText('+');
    expect(chip.className).toContain('status-chip--present');
  });

  it('falls back to "Предмет" when subjectName is not provided', async () => {
    await render(LessonRowComponent, {
      providers: [provideNoopAnimations()],
      componentInputs: { lesson: baseLesson },
    });
    expect(screen.getByText('Предмет')).toBeTruthy();
  });
});
