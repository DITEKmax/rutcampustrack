# URL layout — текущие Vue-клиенты

Общий внешний Nginx обслуживает WEB, PWA, TMA, landing и Gateway. История решений
v7/v9 сохранена в Git и milestone-документах; активный Angular package удалён в PR3.

| Клиент | Source | Dev по умолчанию | Production path |
|---|---|---:|---|
| WEB |`frontends/web-vue`|14210|`/login`, role routes от`/`|
| PWA |`frontends/pwa-vue`|5175|`/app/`|
| TMA |`frontends/tma-vue`|5176|`/mini-app/`|
| Landing |`frontends/landing`|согласно dev Compose|`/presentation/`|

В production внутренние static-контейнеры слушают80 в private network, наружу
выходит общий Nginx. Идентичность `web-panel-nginx`/`rct-web-panel-nginx` сохранена
для совместимого DNS/image wiring; её Dockerfile теперь `web-vue/Dockerfile`,
это не Angular. Остальные frontend Dockerfile и nginx.conf находятся в соответствующих
Vue/landing каталогах.

`/api/*` проксируется в Gateway. Notifications используют SockJS/STOMP: source base
`/api/ws`, реальные transport URLs включают`/api/ws/info` и session suffix, которые
обслуживает существующий`/api/ws/` proxy. Test createSocket seam не является
доказательством недоступности production SockJS.

После входа route выбирают [role-home](../../frontends/web-vue/src/app/role-home.ts)
и [router guards](../../frontends/web-vue/src/app/router.ts) по текущему selected grant,
readOnly/assignment/admin permissions. Старый `is_headman` shortcut и исторические
`*/dashboard` адреса не описывают новый контракт. Deep links и refresh должны проходить
через общий Nginx; отсутствие hashed asset должно давать404, не HTML fallback.

PWA/TMA production build задаёт `VITE_PUBLIC_BASE=/app/` или`/mini-app/`, fixture mode
выключен. Default root-base dev artifact не подходит для этих production prefixes.
Version policy, service worker scope и существующий WebPush остаются частью PWA.
Dev origin/CORS зависит от явно выбранного порта и backend-конфигурации; не подменять
Origin и не выключать проверки ради runtime PASS.

PR3 локальные standalone namespaces/ports задаются переносимыми launchers и не
заменяют старый проект владельца. Все фактические gate статусы — в
[CURRENT-PRIORITIES](../plans/web-vue-pr3/CURRENT-PRIORITIES.md) и PR; source/build
не означают deploy или owner-local group PASS.
