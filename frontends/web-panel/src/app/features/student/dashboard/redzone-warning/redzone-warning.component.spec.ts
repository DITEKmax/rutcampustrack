import { render, screen } from '@testing-library/angular';
import { describe, it, expect } from 'vitest';
import { RedzoneWarningComponent } from './redzone-warning.component';

describe('RedzoneWarningComponent', () => {
  it('renders subject name, rounded percentage, and the threshold copy', async () => {
    const { container } = await render(RedzoneWarningComponent, {
      componentInputs: { subjectName: 'Математика', percentage: 42 },
    });
    expect(screen.getByText('Математика')).toBeTruthy();
    expect(screen.getByText(/42%/)).toBeTruthy();
    expect(screen.getByText(/посещаемость ниже порога/)).toBeTruthy();
    expect(container.querySelector('.redzone-warning')).toBeTruthy();
    expect(container.querySelector('.ph-warning, .ph-duotone.ph-warning')).toBeTruthy();
  });

  it('rounds percentage to the nearest integer', async () => {
    await render(RedzoneWarningComponent, {
      componentInputs: { subjectName: 'Физика', percentage: 42.7 },
    });
    expect(screen.getByText(/43%/)).toBeTruthy();
  });

  it('rounds percentage down when fractional part < 0.5', async () => {
    await render(RedzoneWarningComponent, {
      componentInputs: { subjectName: 'Химия', percentage: 42.3 },
    });
    expect(screen.getByText(/42%/)).toBeTruthy();
  });
});
