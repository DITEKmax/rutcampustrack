/** Display labels do not change the original server lesson-type selector. */
export function teacherLessonTypeLabel(value: string): string { return ({lecture:'Лекция',practice:'Практика',lab:'Лабораторная',laboratory:'Лабораторная',seminar:'Семинар'} as Record<string,string>)[value.toLowerCase()] ?? value }

export function teacherExcuseTypeLabel(value: string | null): string { if (!value) return 'Причина не указана'; const label = ({illness:'Болезнь',disease:'Болезнь',family:'Семейные обстоятельства',family_circumstances:'Семейные обстоятельства',other:'Другая причина',free_attendance:'Свободное посещение'} as Record<string,string>)[value.toLowerCase()]; return label ?? (/^[A-Z_]+$/.test(value) ? 'Другая причина' : value) }
