import { render } from '@testing-library/angular';
import { describe, it, expect } from 'vitest';
import { JournalGridComponent } from './journal-grid.component';
import type { JournalResponse } from '../types';

const mockJournal: JournalResponse = {
  groupId: 1,
  subjectId: 2,
  dates: ['2026-03-15', '2026-03-16'],
  students: [
    {
      userId: 1,
      displayName: 'Иванов Иван Иванович',
      records: [
        { date: '2026-03-15', lessonNumber: 1, status: 'present', symbol: '+' },
        { date: '2026-03-15', lessonNumber: 2, status: 'absent', symbol: 'н' },
        { date: '2026-03-16', lessonNumber: 1, status: 'excused', symbol: 'у' },
        { date: '2026-03-16', lessonNumber: 2, status: 'present', symbol: '+' },
      ],
    },
    {
      userId: 2,
      displayName: 'Петров Пётр Петрович',
      records: [],
    },
  ],
};

describe('JournalGridComponent', () => {
  it('creates the component', async () => {
    const { fixture } = await render(JournalGridComponent, {
      componentInputs: { journalData: mockJournal },
    });
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('columns() signal produces correct column IDs from 2 dates x 2 lessons = 4 columns', async () => {
    const { fixture } = await render(JournalGridComponent, {
      componentInputs: { journalData: mockJournal },
    });
    const columns = fixture.componentInstance.columns();
    expect(columns.length).toBe(4);
    expect(columns.map(c => c.id)).toContain('2026-03-15_lesson1');
    expect(columns.map(c => c.id)).toContain('2026-03-15_lesson2');
    expect(columns.map(c => c.id)).toContain('2026-03-16_lesson1');
    expect(columns.map(c => c.id)).toContain('2026-03-16_lesson2');
  });

  it('getCellFor() returns correct cell for matching column', async () => {
    const { fixture } = await render(JournalGridComponent, {
      componentInputs: { journalData: mockJournal },
    });
    const row = mockJournal.students[0];
    const cell = fixture.componentInstance.getCellFor(row, '2026-03-15_lesson1');
    expect(cell).not.toBeNull();
    expect(cell?.status).toBe('present');
    expect(cell?.symbol).toBe('+');
  });

  it('getCellFor() returns null for no matching record', async () => {
    const { fixture } = await render(JournalGridComponent, {
      componentInputs: { journalData: mockJournal },
    });
    const row = mockJournal.students[1];
    const cell = fixture.componentInstance.getCellFor(row, '2026-03-15_lesson1');
    expect(cell).toBeNull();
  });

  it('getStats() returns per-student attendance statistics', async () => {
    const { fixture } = await render(JournalGridComponent, {
      componentInputs: { journalData: mockJournal },
    });
    const stats = fixture.componentInstance.getStats(1);
    expect(stats.total).toBe(4);
    expect(stats.attended).toBe(3);
    expect(stats.absent).toBe(1);
    expect(stats.excused).toBe(1);
    expect(stats.percent).toBe(75);
  });

  it('renders the familiar matrix headers and sticky statistics column', async () => {
    const { fixture } = await render(JournalGridComponent, {
      componentInputs: { journalData: mockJournal },
    });
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('.student-column--head')?.textContent?.trim()).toBe('Студент');
    expect(element.querySelector('.stat-cell--head')?.textContent?.trim()).toBe('Статистика');
    expect(element.querySelectorAll('.date-column').length).toBe(4);
  });
});
