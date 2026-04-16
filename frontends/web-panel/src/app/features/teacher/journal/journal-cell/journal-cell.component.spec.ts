import { render, screen } from '@testing-library/angular';
import { describe, it, expect } from 'vitest';
import { JournalCellComponent } from './journal-cell.component';
import type { JournalCell } from '../types';

describe('JournalCellComponent', () => {
  it('renders status-chip--present with text "+" for present status', async () => {
    const cell: JournalCell = {
      date: '2026-03-15',
      lessonNumber: 1,
      status: 'present',
      symbol: 'б', // backend symbol is ignored — client maps by status
    };

    await render(JournalCellComponent, { componentInputs: { cell } });

    const chip = screen.getByText('+');
    expect(chip.className).toContain('status-chip--present');
  });

  it('renders status-chip--absent with text "н" for absent status', async () => {
    const cell: JournalCell = {
      date: '2026-03-15',
      lessonNumber: 1,
      status: 'absent',
      symbol: 'н',
    };

    await render(JournalCellComponent, { componentInputs: { cell } });

    const chip = screen.getByText('н');
    expect(chip.className).toContain('status-chip--absent');
  });

  it('renders middle dot for null cell input', async () => {
    await render(JournalCellComponent, { componentInputs: { cell: null } });

    const dot = screen.getByText('·');
    expect(dot).toBeTruthy();
  });

  it('renders middle dot for undefined cell input', async () => {
    await render(JournalCellComponent, { componentInputs: { cell: undefined } });

    const dot = screen.getByText('·');
    expect(dot).toBeTruthy();
  });
});
