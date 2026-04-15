import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { describe, it, expect, beforeEach } from 'vitest';

import { SegmentedControlComponent } from './segmented-control.component';

/**
 * Tests for SegmentedControlComponent (Phase 61-06 Task 1).
 *
 * Covers:
 * - Renders all option labels
 * - Click on a non-active option emits valueChange
 * - Active option has aria-checked="true"
 */

type Mode = 'day' | 'week' | 'month';

const OPTIONS: { value: Mode; label: string }[] = [
  { value: 'day', label: 'День' },
  { value: 'week', label: 'Неделя' },
  { value: 'month', label: 'Месяц' },
];

function setup(value: Mode = 'day'): {
  fixture: ComponentFixture<SegmentedControlComponent<Mode>>;
  component: SegmentedControlComponent<Mode>;
} {
  TestBed.configureTestingModule({
    imports: [SegmentedControlComponent],
    providers: [provideNoopAnimations()],
  });
  const fixture = TestBed.createComponent(SegmentedControlComponent<Mode>);
  fixture.componentInstance.options = OPTIONS;
  fixture.componentInstance.value = value;
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance };
}

describe('SegmentedControlComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('renders all option labels', () => {
    const { fixture } = setup();
    const buttons: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('button[role="radio"]');
    expect(buttons.length).toBe(3);
    expect(buttons[0].textContent?.trim()).toContain('День');
    expect(buttons[1].textContent?.trim()).toContain('Неделя');
    expect(buttons[2].textContent?.trim()).toContain('Месяц');
  });

  it('click on non-active option emits valueChange with the new value', () => {
    const { fixture, component } = setup('day');
    const emitted: Mode[] = [];
    component.valueChange.subscribe((v: Mode) => emitted.push(v));

    const buttons: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('button[role="radio"]');
    buttons[1].click(); // click «Неделя»
    fixture.detectChanges();

    expect(emitted).toEqual(['week']);
  });

  it('active button has aria-checked="true", others have aria-checked="false"', () => {
    const { fixture } = setup('week');
    const buttons: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('button[role="radio"]');
    expect(buttons[0].getAttribute('aria-checked')).toBe('false');
    expect(buttons[1].getAttribute('aria-checked')).toBe('true');
    expect(buttons[2].getAttribute('aria-checked')).toBe('false');
  });
});
