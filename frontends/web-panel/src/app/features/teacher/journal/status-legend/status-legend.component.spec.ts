import { render, screen } from '@testing-library/angular';
import { describe, it, expect } from 'vitest';
import { StatusLegendComponent } from './status-legend.component';

describe('StatusLegendComponent', () => {
  it('renders 5 legend entries with correct symbols and labels', async () => {
    await render(StatusLegendComponent);

    expect(screen.getByText('б')).toBeTruthy();
    expect(screen.getByText('н')).toBeTruthy();
    expect(screen.getByText('у')).toBeTruthy();
    expect(screen.getByText('сп')).toBeTruthy();
    // em dash for cancelled
    expect(screen.getByText('—')).toBeTruthy();

    expect(screen.getByText('присутствовал')).toBeTruthy();
    expect(screen.getByText('отсутствовал')).toBeTruthy();
    expect(screen.getByText('уважительная причина')).toBeTruthy();
    expect(screen.getByText('свободное посещение')).toBeTruthy();
    expect(screen.getByText('отменена')).toBeTruthy();
  });
});
