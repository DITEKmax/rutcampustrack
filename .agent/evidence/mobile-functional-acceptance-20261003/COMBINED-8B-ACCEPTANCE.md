# Общая ограниченная приёмка — PASS, 2026-10-03

Run 20261003-105228536-k4fsbqpr, source 8b993e0a6cd358ca7b7ac3e20d279a0c26ec92ff; manifest SHA256 c59b0d79e90a1397942bb91881a62133129b9cf7a64657be55c4ad1271f72617. Root — владелец этой приёмки. Все сборки до manual timer; неизменённые артефакты переиспользованы. PASS относится только к перечисленным новым сценариям, не ко всему продукту.

## Преподаватель: статистика через настоящий API и PWA
Выбранная группа2/семестр2/предметA1 открывается без обязательного расчёта посторонней группы с ошибкой покрытия истории. Учтено3 пары; Альфа имеет1/3=33.3% присутствия, Бета0/3=0%, совпадает с API. Текущий предметB2 без проведённых пар сохраняется в выборе после B→A→B, показывает0 и «Пар ещё не было».
Числовой фильтр присутствие>=25 оставляет только Альфу. После обновления страницы остаются фильтр25, семестр, группа, предмет и одна правильная строка. Сброс возвращает обе строки, сохраняя выбранный контекст. Два ключа сортировки -present/displayName сохраняются после обновления; видны приоритеты1/2. Некорректный minPercent101 сбрасывается с понятным уведомлением, валидные semester/group/subject сохраняются. После выхода Teacher и входа Student старые Teacher данные не отображаются.
Proof: teacher-zero-lessons-8b.jpg, teacher-filter-reload-8b.jpg, teacher-sort-reload-8b.jpg в primary .agent/evidence/mobile-functional-acceptance-20261003.

## Студент: физический тип конкретного занятия
После обновления PWA показывает «Контрпример типа 8b»,20:00–21:30,«Практика». Реальный предмет4 в справочнике имеет LECTURE, но physicalLesson20 и Today schedule.id20 имеют PRACTICE. Поэтому сценарий различает исправленное поведение и прежний дефект. Дополнительный первоначальный предмет3 целиком PRACTICE сам по себе не считался достаточным контрпримером.
Proof: student-practice-counterexample-8b.jpg; API directory/lesson/Today statuses201/201/200 и IDs в combined-mobile-20261003/student-counterexample-8b.json. Student-only fixture amendment frozen11:14:02.649UTC; Teacher group2/lifecycle данные не менялись.

## Шаблон расписания: реальные сервисы и история
API owner evidence combined-mobile-20261003/8B-ACCEPTANCE.md и fixtures-8b-20261003-105228536-k4fsbqpr.json. Item1: preview room updated5; PUT200 и повтор200 возвращают стабильный результат. Пять реальных операций PENDING→COMPLETED; десять APPLIED receipts Attendance+Academic и реальная связь ДЗ подтверждены только чтением точных own DB строк. DELETE preview removed5→204; возврат того же item1→201 создаёт будущие planned15–19/gen3; старые10–14 cancelled сохраняются. Прошлые3/4 CLOSED/gen1/current unchanged. Homework1/binding1 остаются ARCHIVED/terminal marker1 после возврата. Новые physical generations только будущих дат. SQL writes0. Промежуток многодневного отсутствия здесь не симулировали: достаточная отдельная PostgreSQL проверка уже принята и не повторялась.

## Границы
Новый Headman lifecycle editor6993d68 ещё не интегрирован и в этот PASS не включён; автор исправляет один P2403 после независимого review. Не выполнялись Figma redesign, actual Telegram/WebPush, офлайн/установка, password UI mutation, физическое сохранение выгрузки, deploy/push или production migration. Cleanup подтверждается отдельно после sentinel. Исторический stand73540 FAIL сохранён; этим PASS его результаты не переписываются.