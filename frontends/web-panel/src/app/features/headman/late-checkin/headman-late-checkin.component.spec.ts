import { render, screen } from '@testing-library/angular';
import { throwError } from 'rxjs';
import { HeadmanLateCheckinComponent } from './headman-late-checkin.component';
import { HeadmanApiService } from '../shared/headman-api.service';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

describe('HeadmanLateCheckinComponent', () => {
  it('shows degradation empty state when API returns error', async () => {
    const mockService = { getPendingLateCheckins: () => throwError(() => new Error('404')) };
    await render(HeadmanLateCheckinComponent, {
      providers: [
        { provide: HeadmanApiService, useValue: mockService },
        provideNoopAnimations(),
      ],
    });
    expect(screen.getByText(/Функция находится в разработке/)).toBeTruthy();
  });

  it('renders .page-empty element', async () => {
    const mockService = { getPendingLateCheckins: () => throwError(() => new Error('404')) };
    const { container } = await render(HeadmanLateCheckinComponent, {
      providers: [
        { provide: HeadmanApiService, useValue: mockService },
        provideNoopAnimations(),
      ],
    });
    expect(container.querySelector('.page-empty')).toBeTruthy();
  });

  it('shows exact D-07 text', async () => {
    const mockService = { getPendingLateCheckins: () => throwError(() => new Error('404')) };
    await render(HeadmanLateCheckinComponent, {
      providers: [
        { provide: HeadmanApiService, useValue: mockService },
        provideNoopAnimations(),
      ],
    });
    expect(screen.getByText('Функция находится в разработке. Заявки появятся здесь автоматически.')).toBeTruthy();
  });
});
