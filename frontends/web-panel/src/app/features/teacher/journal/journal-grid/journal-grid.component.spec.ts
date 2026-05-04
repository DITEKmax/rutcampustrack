import { TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { render, screen } from '@testing-library/angular';
import { describe, it, expect, beforeEach } from 'vitest';
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

  it('columns() signal produces correct column IDs from 2 dates × 2 lessons = 4 columns', async () => {
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

  it('getCellFor() returns correct cell for matching date and lessonNumber', async () => {
    const { fixture } = await render(JournalGridComponent, {
      componentInputs: { journalData: mockJournal },
    });
    const row = mockJournal.students[0];
    const cell = fixture.componentInstance.getCellFor(row, '2026-03-15', 1);
    expect(cell).not.toBeNull();
    expect(cell?.status).toBe('present');
    expect(cell?.symbol).toBe('+');
  });

  it('getCellFor() returns null for no matching record', async () => {
    const { fixture } = await render(JournalGridComponent, {
      componentInputs: { journalData: mockJournal },
    });
    const row = mockJournal.students[1]; // empty records
    const cell = fixture.componentInstance.getCellFor(row, '2026-03-15', 1);
    expect(cell).toBeNull();
  });

  it('displayedColumns() starts with "student" followed by column IDs', async () => {
    const { fixture } = await render(JournalGridComponent, {
      componentInputs: { journalData: mockJournal },
    });
    const displayedColumns = fixture.componentInstance.displayedColumns();
    expect(displayedColumns[0]).toBe('student');
    expect(displayedColumns.length).toBe(5); // 1 student + 4 date columns
  });
});
