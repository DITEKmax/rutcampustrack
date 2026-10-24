/**
 * BUG-004: каталог пресетов аватаров.
 * id ровно "avatar_NN" (две цифры), используется как ключ для backend.
 * Стиль — двутоновые иконки Phosphor на градиентном брендовом фоне.
 * Расширение каталога — только в конец, без дыр.
 */
export interface AvatarPreset {
  id: string;
  /** Phosphor icon class for the inner glyph. */
  icon: string;
  /** Two-stop linear-gradient string used as the tile background. */
  gradient: string;
  /** Russian label for accessibility / picker tooltip. */
  label: string;
}

export const AVATAR_PRESETS: readonly AvatarPreset[] = [
  { id: 'avatar_01', icon: 'ph-duotone ph-graduation-cap', gradient: 'linear-gradient(135deg,#00E5A0,#3B82F6)', label: 'Студент' },
  { id: 'avatar_02', icon: 'ph-duotone ph-chalkboard-teacher', gradient: 'linear-gradient(135deg,#3B82F6,#8B5CF6)', label: 'Преподаватель' },
  { id: 'avatar_03', icon: 'ph-duotone ph-rocket-launch', gradient: 'linear-gradient(135deg,#F43F5E,#F97316)', label: 'Ракета' },
  { id: 'avatar_04', icon: 'ph-duotone ph-cat', gradient: 'linear-gradient(135deg,#F97316,#FACC15)', label: 'Кот' },
  { id: 'avatar_05', icon: 'ph-duotone ph-dog', gradient: 'linear-gradient(135deg,#84CC16,#22C55E)', label: 'Пёс' },
  { id: 'avatar_06', icon: 'ph-duotone ph-smiley', gradient: 'linear-gradient(135deg,#FACC15,#EAB308)', label: 'Смайл' },
  { id: 'avatar_07', icon: 'ph-duotone ph-ghost', gradient: 'linear-gradient(135deg,#6366F1,#EC4899)', label: 'Призрак' },
  { id: 'avatar_08', icon: 'ph-duotone ph-game-controller', gradient: 'linear-gradient(135deg,#22D3EE,#06B6D4)', label: 'Геймпад' },
  { id: 'avatar_09', icon: 'ph-duotone ph-music-notes', gradient: 'linear-gradient(135deg,#A855F7,#D946EF)', label: 'Музыка' },
  { id: 'avatar_10', icon: 'ph-duotone ph-basketball', gradient: 'linear-gradient(135deg,#F97316,#DC2626)', label: 'Баскетбол' },
  { id: 'avatar_11', icon: 'ph-duotone ph-leaf', gradient: 'linear-gradient(135deg,#10B981,#34D399)', label: 'Лист' },
  { id: 'avatar_12', icon: 'ph-duotone ph-coffee', gradient: 'linear-gradient(135deg,#A16207,#78350F)', label: 'Кофе' },
];

export function findPreset(id: string | null | undefined): AvatarPreset | undefined {
  if (!id) return undefined;
  return AVATAR_PRESETS.find(p => p.id === id);
}
