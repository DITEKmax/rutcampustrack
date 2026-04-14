import { Component, computed, input } from '@angular/core';
import type { UserRole } from '../types';

const ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: 'Админ',
  TEACHER: 'Преподаватель',
  STUDENT: 'Студент',
};

@Component({
  selector: 'app-role-chip',
  standalone: true,
  template: `<span class="role-chip role-chip--{{cssSuffix()}}">{{ roleLabel() }}</span>`,
})
export class RoleChipComponent {
  role = input.required<UserRole>();
  roleLabel = computed(() => ROLE_LABELS[this.role()] ?? this.role());
  cssSuffix = computed(() => this.role().toLowerCase());
}
