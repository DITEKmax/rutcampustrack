# RutCampusTrack — Дизайн-решения

Зафиксированные решения по дизайну фронтендов. Соблюдаются во всех фазах.

---

## 1. Иконки: Phosphor Icons

### Пакеты по платформам

| Платформа | Пакет |
|-----------|-------|
| React (Mini App + PWA) | `@phosphor-icons/react` |
| Angular (веб-панель) | `@phosphor-icons/web` (web-components, универсальный) |

### Weight convention

| Контекст | Weight | Обоснование |
|----------|--------|-------------|
| Mobile (Mini App, PWA) | `bold`, `fill` | Крупные, чёткие на маленьких экранах |
| Desktop (веб-панель) | `regular`, `light` | Элегантные, не перегружают интерфейс |
| Dashboard карточки и статистика | `duotone` | Двухцветные для визуального акцента |
| Активные/выбранные элементы | `fill` | — |
| Неактивные элементы | `regular` | — |

### Размеры

| Контекст | Размер |
|----------|--------|
| Inline (таблицы, списки) | 20px |
| Навигация | 24px |
| Hero / dashboard | 32px |

### Цвет

- По умолчанию: `currentColor` (наследуется от текста)
- Цветные только для статусов:
  - `present` / success — зелёный
  - `absent` / error — красный
  - `excused` / warning — жёлтый/оранжевый
  - `pending` — серый

---

## 2. Анимации

### По платформам

| Платформа | Библиотека | Применение |
|-----------|-----------|------------|
| React (Mini App + PWA) | Motion (framer-motion) | Декларативные анимации, gestures, AnimatePresence для смены экранов, layout для списков |
| Angular (веб-панель) | `@angular/animations` | Route transitions, subtle UI feedback. Тяжёлые библиотеки НЕ использовать |
| Лендинг | GSAP + ScrollTrigger | Scroll-driven анимации, timeline секвенции, параллакс |

### Готовые animated компоненты (лендинг)

- Aceternity UI и/или Magic UI (copy-paste, React + Tailwind)

### Принцип

Анимации должны быть **функциональными** (guide user attention, show state changes, provide feedback), не декоративными.

---

## 3. PWA Mobile Client «RutTrack»

- Отдельный фронтенд-проект в `frontends/pwa/`, НЕ часть Angular веб-панели
- Стек: React (общий с Mini App для переиспользования компонентов и API-клиента)
- Аудитория: все роли (студенты, старосты, преподаватели). Админы остаются на desktop веб-панели
- PWA сосуществует с Telegram Mini App — оба канала активны
- Офлайн: кэшировать расписание, статистику, ДЗ для чтения. Check-in строго онлайн
- Web Push уведомления дублируют Telegram push (в будущем — настройка канала в профиле)
- iOS onboarding: инструкция «Safari → Поделиться → На экран Домой»
- `manifest.json`: display: standalone, название «RutTrack», иконки 192x192 и 512x512

---

## 4. Брендинг

- **Название приложения**: RutTrack (короткое, для иконки на рабочем столе)
- **Полное название**: RutCampusTrack (для документации и лендинга)
- Логотип и цветовая схема: определить перед Фазой 8, привязать к Angular Material theme
- Единый визуальный стиль через Phosphor Icons + общую цветовую палитру
