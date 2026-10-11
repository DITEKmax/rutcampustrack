# RutCampusTrack mobile UX package

Дата: 28.08.2026. Поверхности: PWA и Telegram Mini App. Figma не запускалась, исходный kit не изменялся.

## С чего начать

1. Откройте `RutCampusTrack_PWA_TMA_UX_Guide.pdf` для чтения и согласования.
2. Передайте `RutCampusTrack_PWA_TMA_UX_Guide.docx` вместе с папкой `assets` агенту/дизайнеру, если нужны комментарии и редактирование.
3. Используйте `Figma-build-checklist.md` как порядок сборки и контроль готовности.
4. `research-notes` содержит полную доказательную базу: официальную Telegram, PWA, живые Mini Apps, mobile UI и Behance/Dribbble benchmark.

## Состав

- `RutCampusTrack_PWA_TMA_UX_Guide.pdf` — основная 40-страничная инструкция;
- `RutCampusTrack_PWA_TMA_UX_Guide.docx` — редактируемая версия;
- `RutCampusTrack_PWA_TMA_UX_Guide.md` — исходник содержания и прямые URL;
- `Figma-build-checklist.md` — короткий handoff;
- `assets/svg` — 10 редактируемых схем/набросков;
- `assets/png` — те же материалы для вставки/просмотра;
- `reference-audit` — contact sheets предоставленных 1920 dark кадров студента и старосты;
- `research-notes` — пять подробных исследовательских записок;
- `fonts` — Onest Regular/Medium/SemiBold/Bold и лицензия OFL.

## Важная граница

Отдельные актуальные 1920 dark exports преподавателя и администратора не были приложены. Их опыт спроектирован по текущему kit/spec и reference frames из design archive. Перед финальным pixel pass запросите свежие кадры и проверьте labels, права, высоты блоков и последние owner-corrections.

## Принятые коррекции прежних решений

- «Один дизайн и три platform difference» расширен до общего product core и минимум восьми surface adapters.
- Пять slots оставлены baseline только для root routes; detail/editor/task скрывают custom nav.
- Один canonical owner экрана не запрещает contextual deep links из Today, push и сообщения бота.
- Mobile visual extension разрешает управляемые gradients, fluid shapes, pattern и glass-only chrome в фиолетовой системе; это новое решение, которое надо занести в журнал кита.

