# Временное Spring-исключение для alpha release

2026-11-01 владелец разрешил убрать Spring из проверки при деплое. Принятый
scope ограничен следующими четырьмя сочетаниями; это принятие остаточного
риска для alpha, а не исправление Spring или подтверждение отсутствия уязвимости.

| CVE | Maven package | Версия |
| --- | --- | --- |
| CVE-2026-47884 | org.springframework:spring-webmvc | 6.2.19 |
| CVE-2026-47890 | org.springframework:spring-webmvc | 6.2.19 |
| CVE-2026-47890 | org.springframework:spring-webflux | 6.2.19 |
| CVE-2026-47892 | org.springframework:spring-webflux | 6.2.19 |

[Политика](../../.github/security/release-spring-exceptions.rego) подключена
только к `Blocking release image scan` в Deploy. Trivy проверяет одновременно
тип finding, CVE, имя пакета, установленную версию и точный Maven PURL.
Отсутствующая идентичность, другая версия, пакет, новое CVE и неуказанные
сочетания остаются блокирующими. Политика берётся из immutable
`github.workflow_sha` через checkout `release-control`, а образ — из manifest
по точному build digest. HIGH/CRITICAL, exit-code 1, ignore-unfixed, 13 образов,
SBOM, подписи и apply gates сохраняются.

Основание решения — адресный source audit текущего кода и Boot 3.5.16:

- [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) требует XsltView
  с неявным именем view под wildcard route. В authored production source нет
  XsltView и XSLT views. Стандартные MVC resolvers и error StaticView Boot не
  создают XsltView.
- [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) требует SSE view
  fragments. В authored endpoints их нет;
  [WebSocketConfig](../../services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java)
  использует STOMP `/ws` и SockJS.
- [CVE-2026-47892](https://spring.io/security/cve-2026-47892/) требует WebFlux
  functional endpoints с DispatcherServlet. Gateway явно
  [reactive](../../services/api-gateway/src/main/resources/application.yml);
  [Boot DispatcherServletAutoConfiguration](https://github.com/spring-projects/spring-boot/blob/v3.5.16/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/web/servlet/DispatcherServletAutoConfiguration.java)
  ограничена SERVLET. Default reactive error handler создаёт RouterFunction,
  поэтому отсутствие всех functional endpoints не заявляется.

Полная перепись runtime beans не выполнялась. Spring 6.2.19 остаётся
уязвимой версией; исключение не защищает от появления условий эксплуатации.
При изменении версии Spring/Boot, routing, view resolvers, XSLT, SSE fragments
или servlet/reactive конфигурации пересмотри исключение до release. Для
закрытия исключения нужен совместимый переход на исправленную поддерживаемую
версию и повторный exact-digest scan; расширение списка требует нового решения.

Используется Rego, поскольку Trivy 0.70.0
[YAML matchPURL](https://github.com/aquasecurity/trivy/blob/v0.70.0/pkg/result/ignore.go)
считает отсутствующий PURL совпадением даже при заполненном `purls`.
[Pinned action](https://github.com/aquasecurity/trivy-action/blob/a9c7b0f06e461e9d4b4d1711f154ee024b8d7ab8/action.yaml)
передаёт `ignore-policy` через `TRIVY_IGNORE_POLICY` в native Trivy 0.70.0.

Конечная офлайн-проверка реального Trivy без загрузки БД и сканирования образов:

```sh
python3 scripts/test-release-spring-policy.py --trivy /path/to/trivy-0.70.0
```

Контроли проверяют четыре разрешённых сочетания и блокировку новых CVE,
версий, пакетов, неполных/противоречивых идентификаторов. Они проверяют фильтр;
готовность новых образов подтверждает отдельный обязательный release scan.
