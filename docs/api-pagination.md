# API Pagination (M13 G3)

**Статус:** глобальный cap реализован в M13 (tag будет `v0.0.0-alpha.14`).

Документирует размер страниц для всех RESTful endpoint'ов
RutTrack, поддерживающих `Pageable` (Spring Data).

## Глобальный потолок

Все сервисы, подключающие `shared-web`, получают дефолт
`spring.data.web.pageable.max-page-size=100` через
`PageableDefaultsPostProcessor` (`EnvironmentPostProcessor`
с lowest-priority property source, регистрируется в
`META-INF/spring.factories`).

**Семантика:** клиент шлёт `?size=N`. Spring Data обрезает `N` до
`max-page-size=100` **молча**, без 400 — по convention Spring Data
это не ошибка валидации, а предельное значение.

```
GET /academic/users?size=1000000
→ 200 OK
  page: { size: 100, number: 0, totalElements: 42, totalPages: 1 }
```

## Per-service override

Сервис может override'нуть глобальный cap в своём `application.yml`.
Это честная эскалация limit'а при наличии bound'а на domain-level.

| Сервис       | `max-page-size` | `default-page-size` | Обоснование                         |
|--------------|-----------------|---------------------|-------------------------------------|
| academic     | 100 (default)   | 20 (Spring default) |                                     |
| schedule     | **200**         | 50                  | Week-range queries ~640 lessons/sem |
| attendance   | 100 (default)   | 20                  |                                     |
| notification | 100 (default)   | **20**              | Для history feed UX                 |
| auth         | 100 (default)   | 20                  | Пагинации практически нет           |

Override идёт в сторону **increase** (понижать ниже 100 обычно не
нужно — если endpoint отдаёт мало данных, клиент сам отправит
`size=N < default`).

## Семантика `default-page-size`

Если клиент не указал `?size=`, используется `default-page-size`.
Spring Boot default = 20. Если сервис override'нул — см. таблицу выше.

## Правила для клиентов

### 1. Всегда указывайте `page` и `size` явно

```typescript
const pageSize = 50;
const url = `/api/academic/users?page=${pageNum}&size=${pageSize}`;
```

### 2. Не полагайтесь на unlimited response

```typescript
// ❌ BAD — клиент считает, что получит всех пользователей
const users = await fetch('/api/academic/users?size=100000').then(r => r.json());

// ✅ GOOD — итерация по страницам
async function* allUsers() {
  let page = 0;
  while (true) {
    const response = await fetch(`/api/academic/users?page=${page}&size=100`);
    const data = await response.json();
    yield* data._embedded?.userResponseList ?? [];
    if (page >= data.page.totalPages - 1) break;
    page++;
  }
}
```

### 3. HATEOAS `_links.next` — каноничный источник «есть ли ещё»

`PagedModel` отдаёт `_links.next` и `_links.prev`. Клиент может
просто следовать ссылкам вместо инкремента `page` вручную.

```typescript
let nextUrl: string | null = '/api/academic/users?size=50';
while (nextUrl) {
  const resp = await fetch(nextUrl);
  const data = await resp.json();
  processPage(data._embedded?.userResponseList ?? []);
  nextUrl = data._links?.next?.href ?? null;
}
```

### 4. UI подсказки

- **PWA/web-panel:** default page size = `50` (dense tables)
  или `20` (mobile).
- **Infinite scroll:** `size=20-30` с pre-fetch следующей страницы
  при scroll > 80% viewport.
- **Export-all:** не делайте через `size=N` с большим `N`. Нужен
  отдельный endpoint (roadmap v0.1 — `/export/*.csv` c streaming).

## Мониторинг

- **Prometheus metric (roadmap v0.1):** `http_server_requests_seconds{uri=~"/api/.+",status="200"}` с histogram позволит увидеть, какие endpoint'ы отвечают медленно → candidate на server-side streaming вместо пагинации.
- **Alert (roadmap v0.1):** запросы с `size ≥ 100` на `/api/academic/users` логируются WARN (потенциальный signal об N+1 у клиента).

## Источники

- Spring Data Web reference: https://docs.spring.io/spring-data/commons/reference/repositories/core-extensions.html#web.pageable
- M13 G3 implementation: `services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/autoconfigure/PageableDefaultsPostProcessor.java`
- Per-service overrides: `services/{service}/*/src/main/resources/application.yml`
- Integration tests (cap validation):
  - `academic-app/.../integration/PaginationCapIT.java`
  - `schedule-app/.../integration/PaginationCapIT.java`
  - `attendance-app/.../integration/PaginationCapIT.java`
