import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { OverallStatCardComponent } from './overall-stat-card.component';

describe('OverallStatCardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OverallStatCardComponent],
    }).compileComponents();
  });

  it('creates the component', () => {
    const fixture = TestBed.createComponent(OverallStatCardComponent);
    const comp = fixture.componentInstance;
    comp.totalLessons = 10;
    comp.attendanceRate = 85;
    fixture.detectChanges();
    expect(comp).toBeTruthy();
  });

  it('renders the total lessons number', () => {
    const fixture = TestBed.createComponent(OverallStatCardComponent);
    const comp = fixture.componentInstance;
    comp.totalLessons = 24;
    comp.attendanceRate = 75;
    fixture.detectChanges();
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('24');
  });

  it('renders the attendance rate with % suffix', () => {
    const fixture = TestBed.createComponent(OverallStatCardComponent);
    const comp = fixture.componentInstance;
    comp.totalLessons = 10;
    comp.attendanceRate = 90;
    fixture.detectChanges();
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('90%');
  });

  it('renders "Всего занятий" label', () => {
    const fixture = TestBed.createComponent(OverallStatCardComponent);
    const comp = fixture.componentInstance;
    comp.totalLessons = 5;
    comp.attendanceRate = 100;
    fixture.detectChanges();
    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('Всего занятий');
  });
});
