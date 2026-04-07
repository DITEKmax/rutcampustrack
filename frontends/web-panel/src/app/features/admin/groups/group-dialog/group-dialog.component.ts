import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';

import { AdminApiService } from '../../shared/admin-api.service';
import type { GroupResponse } from '../../shared/types';

export interface GroupDialogData {
  mode: 'create' | 'edit';
  group?: GroupResponse;
}

@Component({
  selector: 'app-group-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSlideToggleModule,
  ],
  templateUrl: './group-dialog.component.html',
})
export class GroupDialogComponent implements OnInit {
  data = inject<GroupDialogData>(MAT_DIALOG_DATA);
  private dialogRef = inject(MatDialogRef<GroupDialogComponent>);
  private adminApi = inject(AdminApiService);
  private fb = inject(FormBuilder);

  saving = false;

  form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(50)]],
    code: ['', [Validators.required, Validators.maxLength(20)]],
    active: [true],
  });

  get isEdit(): boolean {
    return this.data.mode === 'edit';
  }

  ngOnInit(): void {
    if (this.isEdit && this.data.group) {
      this.form.patchValue({
        name: this.data.group.name,
        code: this.data.group.code,
        active: this.data.group.active,
      });
    }
  }

  save(): void {
    if (this.form.invalid || this.saving) return;
    this.saving = true;

    const { name, code, active } = this.form.getRawValue();

    if (this.isEdit && this.data.group) {
      this.adminApi.updateGroup(this.data.group.id, { name, code, active }).subscribe({
        next: () => this.dialogRef.close(true),
        error: () => (this.saving = false),
      });
    } else {
      this.adminApi.createGroup({ name, code }).subscribe({
        next: () => this.dialogRef.close(true),
        error: () => (this.saving = false),
      });
    }
  }
}
