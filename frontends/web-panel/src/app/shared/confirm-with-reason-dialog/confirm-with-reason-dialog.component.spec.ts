import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import {
  ConfirmWithReasonDialogComponent,
  type ConfirmWithReasonDialogData,
} from './confirm-with-reason-dialog.component';

describe('ConfirmWithReasonDialogComponent (M07 G7, QC4)', () => {
  let dialogClose: ReturnType<typeof vi.fn>;

  async function open(data: ConfirmWithReasonDialogData) {
    dialogClose = vi.fn();
    return render(ConfirmWithReasonDialogComponent, {
      providers: [
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: { close: dialogClose } },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    });
  }

  beforeEach(() => {
    dialogClose = vi.fn();
  });

  it('рендерит title и message', async () => {
    await open({ title: 'Отмена пары', message: 'Причина отмены?' });
    expect(screen.getByText('Отмена пары')).toBeTruthy();
    expect(screen.getByText('Причина отмены?')).toBeTruthy();
  });

  it('кнопка "Подтвердить" disabled при пустой причине', async () => {
    await open({ title: 'x' });
    const confirmBtn = screen.getByRole('button', { name: /подтвердить/i });
    expect(confirmBtn).toBeDisabled();
  });

  it('вводит причину → enable → close(trimmed)', async () => {
    await open({ title: 'x' });
    const user = userEvent.setup();
    const textarea = screen.getByRole('textbox');
    await user.type(textarea, '  Болен  ');
    const confirmBtn = screen.getByRole('button', { name: /подтвердить/i });
    expect(confirmBtn).toBeEnabled();
    await user.click(confirmBtn);
    expect(dialogClose).toHaveBeenCalledWith('Болен');
  });

  it('только whitespace → confirm остаётся disabled', async () => {
    await open({ title: 'x' });
    const user = userEvent.setup();
    await user.type(screen.getByRole('textbox'), '   ');
    expect(screen.getByRole('button', { name: /подтвердить/i })).toBeDisabled();
  });

  it('отмена → close(null)', async () => {
    await open({ title: 'x' });
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /отмена/i }));
    expect(dialogClose).toHaveBeenCalledWith(null);
  });

  it('предзаполняет поле initialReason', async () => {
    await open({ title: 'x', initialReason: 'Было так' });
    const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
    expect(textarea.value).toBe('Было так');
    expect(screen.getByRole('button', { name: /подтвердить/i })).toBeEnabled();
  });

  it('показывает destructive кнопку warn-color при destructive=true', async () => {
    await open({ title: 'x', destructive: true, confirmLabel: 'Отменить пару' });
    const btn = screen.getByRole('button', { name: 'Отменить пару' });
    expect(btn).toBeTruthy();
  });

  it('использует custom maxLength', async () => {
    await open({ title: 'x', maxLength: 10 });
    const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
    expect(textarea.getAttribute('maxlength')).toBe('10');
  });
});
