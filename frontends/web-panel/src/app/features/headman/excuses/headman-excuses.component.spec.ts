import { render, screen } from '@testing-library/angular';
import { throwError } from 'rxjs';
import { HeadmanExcusesComponent } from './headman-excuses.component';
import { HeadmanApiService } from '../shared/headman-api.service';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

describe('HeadmanExcusesComponent', () => {
  it('shows degradation empty state when API returns error', async () => {
    const mockService = { getPendingExcuses: () => throwError(() => new Error('404')) };
    await render(HeadmanExcusesComponent, {
      providers: [
        { provide: HeadmanApiService, useValue: mockService },
        provideNoopAnimations(),
      ],
    });
    expect(screen.getByText(/Функция находится в разработке/)).toBeTruthy();
  });

  it('renders .page-empty element', async () => {
    const mockService = { getPendingExcuses: () => throwError(() => new Error('404')) };
    const { container } = await render(HeadmanExcusesComponent, {
      providers: [
        { provide: HeadmanApiService, useValue: mockService },
        provideNoopAnimations(),
      ],
    });
    expect(container.querySelector('.page-empty')).toBeTruthy();
  });

  it('shows exact D-07 text', async () => {
    const mockService = { getPendingExcuses: () => throwError(() => new Error('404')) };
    await render(HeadmanExcusesComponent, {
      providers: [
        { provide: HeadmanApiService, useValue: mockService },
        provideNoopAnimations(),
      ],
    });
    expect(screen.getByText('Функция находится в разработке. Заявки появятся здесь автоматически.')).toBeTruthy();
  });
});
