import { Component, computed, input } from '@angular/core';
import type { UserRole } from '../types';

const ROLE_LABELS: Record<UserRole, string> = {
  admin: 'Админ',
  teacher: 'Преподаватель',
  student: 'Студент',
};

@Component({
  selector: 'app-role-chip',
  standalone: true,
  template: `<span class="role-chip role-chip--{{role()}}">{{ roleLabel() }}</span>`,
})
export class RoleChipComponent {
  role = input.required<UserRole>();
  roleLabel = computed(() => ROLE_LABELS[this.role()] ?? this.role());
}
