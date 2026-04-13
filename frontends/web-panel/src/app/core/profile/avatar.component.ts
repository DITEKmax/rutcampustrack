import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { findPreset } from './preset-avatars';

/**
 * Renders a circular avatar tile.
 * - If avatarId resolves to a preset → show its gradient + Phosphor icon.
 * - Otherwise → show initials on a neutral gradient (BUG-004 default).
 */
@Component({
  selector: 'app-avatar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (preset(); as p) {
      <span class="avatar" [style.background]="p.gradient" [style.width.px]="size()" [style.height.px]="size()">
        <i [class]="p.icon" aria-hidden="true"></i>
      </span>
    } @else {
      <span class="avatar avatar--initials" [style.width.px]="size()" [style.height.px]="size()">
        {{ initials() }}
      </span>
    }
  `,
  styles: [`
    :host { display: inline-block; line-height: 0; }
    .avatar {
      display: grid; place-items: center;
      border-radius: 50%;
      color: #fff;
      font-family: var(--font-display, 'Inter', sans-serif);
      font-weight: 700;
      letter-spacing: -0.01em;
      box-shadow: 0 0 0 1px color-mix(in oklab, var(--text-primary, #000) 8%, transparent) inset;
      overflow: hidden;
      flex-shrink: 0;
    }
    .avatar i { font-size: calc(var(--avatar-size, 40px) * 0.55); }
    .avatar--initials {
      background: linear-gradient(135deg, #00E5A0, #3B82F6);
      font-size: calc(var(--avatar-size, 40px) * 0.40);
      text-transform: uppercase;
    }
  `],
})
export class AvatarComponent {
  avatarId = input<string | null | undefined>(null);
  initials = input<string>('?');
  size = input<number>(40);

  readonly preset = computed(() => findPreset(this.avatarId() ?? null));
}
