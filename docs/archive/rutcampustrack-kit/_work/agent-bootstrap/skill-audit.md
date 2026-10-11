# Отбор проектных skills

✏️ 05.09.2026. Единица здесь — файл SKILL.md, не узел Figma A.
В `.agents/skills` основного repo найдены 18 файлов. В kit — один действующий
design skill и его Claude-копия. Глобальные и plugin skills не удаляются:
они используются другими задачами пользователя.

| Старые skills | Решение для нового этапа |
|---|---|
| code-review, find-bugs | заменить одной ролью reviewer и risk-based workflow; убрать Sentry/React примеры и полный security-checklist для каждого файла |
| design-system, frontend-design | исключить генерацию новой эстетики/дизайн-системы; использовать принятый RCT design |
| git-workflow | основные правила в AGENTS/workflow; Claude-specific процедура не нужна |
| gsap-core, gsap-frameworks, gsap-performance, gsap-plugins, gsap-react, gsap-scrolltrigger, gsap-timeline, gsap-utils | исключить из активного набора; GSAP не выбран, отдельная библиотека анимаций не требуется для переноса |
| landing-page | исключить: шаблон предлагает Tailwind CDN |
| react-patterns, vercel-react-best-practices | исключить: новый frontend на Vue |
| responsiveness-check, ux-audit | заменить rct-verification: локальная тестовая среда, ширины по спекам, без обязательного согласия на каждый flow и без production по умолчанию |
| kit rutcampustrack-design | короткая адаптация для кода; Figma-правила сборки и исторические счётчики остаются в kit, не становятся командами Vue-разработчику |

Новый набор: `rutcampustrack-design`, `rct-source-resolution`, `rct-verification`.
Vue/backend conventions размещены в AGENTS и не дублируются отдельными skills.
Новые тексты написаны для проекта, сторонние skills не скопированы.

[di-sukharev/vibe](https://github.com/di-sukharev/vibe) проверен по полному Git tree
ветки master, commit `820ca32865c27063add8885139d2b1687cd8030f`,
`truncated=false`: файлов SKILL.md — 0. Его React/Bun/Hono-шаблон не устанавливался.
Снимок проверки: `../vibe-tree-2026-09-05.json`.

Изменение старых проектных skills в основном repo произойдёт только при
последующей установке. Пока подготовлены замены и установщик с архивированием.
