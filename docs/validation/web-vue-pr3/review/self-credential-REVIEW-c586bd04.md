# Независимое review self-credential handoff — PASS

Проверен замороженный source `c586bd04f4d15ac834a68eef6466cc47f07ee24b` относительно `85eb32bee4300c67231ebe8158553c9c0d0c212e` в `/workspace/rct-worktrees/web-foundation`. **Подтверждённых P1/P2 findings нет.** Scope: безопасная передача результата собственной генерации пароля из завершаемой административной страницы на гостевой экран, включая потерю ACK и смену владельца. Риск: раскрытие секрета другому actor/session/role, отмена нового входа запоздавшим ответом, потеря unknown и блокировка перехода к входу.

## Вывод по реализации

- `frontends/web-vue/src/auth/reauthentication-receipt.ts:18–70`: ticket создаётся только для текущего self target при ready и отсутствии session busy. Сохранённая строка owner содержит userId/sessionId/sessionVersion/rolesVersion/activeRole/readOnly из `webSession.identity`; target label передан строковым значением, без последующего чтения mutable props. Единственный активный intent проверяется по объектной идентичности. `publish` потребляет его до callback и публикации; старые cancel/unknown/confirmed не могут потребить новый intent.
- `reauthentication-receipt.ts:34–56,83–97`: подтверждённый secret публикуется только при исходной текущей identity, ready, !busy и непустом пароле. После retirement разрешён исключительно несекретный unknown. Синхронный observer очищает receipt и отменяет intent при начале authentication/role operation через busy, а также при новой ненулевой authority identity. Это закрывает интервал нового login, когда старый snapshot ещё ready. Переход snapshot→null→guest не теряет заранее зарегистрированный unknown intent; consumed known receipt не заменяется unknown.
- `frontends/web-vue/src/features/admin/UserCredentials.vue:33–48,100–114,151–155`: лишь callback принятого self intent снимает локальные unfinished/route locks и синхронно emits locked=false исходному parent до invalidate. Он очищает local credentials/recovery link. Общие pending-операции и другие targets эту ветку не получают. Неизменённый UsersPage объединяет credentialLocked с другими собственными блокировками; callback не снимает чужие locks.
- `UserCredentials.vue:59–89,107–129`: нет обхода `CredentialsClient`/transport/admin.assertCurrent для частных decoded данных. Ticket позволяет независимо передать только уже разрешённый несекретный unknown после retirement, когда transport обоснованно отвергает ответ старого владельца. Поздний ACK не попадает в другой owner; до invalidate требуется успешный confirmed/unknown. Unmount отменяет лишь свой ticket. Неизвестный исход сохраняет исходную version, блокирует следующую генерацию и разрешает readback без повторного POST; старый version сам по себе не доказывает завершение мутации.
- Receipt остаётся module-local shallowRef: добавленный путь не пишет secret в storage, URL, logs, notifications или durable recovery. Существующий backend остаётся владельцем live ADMIN authorization, CAS и отзыва сессий; frontend не меняет этот контракт. Для контекста прочитаны неизменённые `AdminGeneratedPasswordService`, controller и `GeneratedPasswordMutation`, backend не проверялся runtime.

## Проверки и причинность evidence

Прочитаны root/frontend/tests AGENTS, RULES, CURRENT и применимые разделы workflow, а также services AGENTS для backend-контекста. Review выполнялся read-only для source/runtime; продуктовые тесты и security writes не запускались.

Самостоятельно выполнены read-only `git diff`, `git diff --check` (PASS), `git status` (clean) и побайтовое сравнение base/head: ровно три заданных пути; `CredentialsClient`, `WebTransport`, `ReauthenticationReceipt.vue` и template `UserCredentials.vue` byte-identical. Продуктовые/test файлы, ветки и runtime не изменялись.

Прочитан весь `self-credentials.test.ts` на frozen source и существующие логи автора в `/workspace/rct-pr3-runtime/self-credentials-source-diagnosis/`:

- `isolation-after-login-cancellation.log`: один файл, 10 mounted tests PASS. Фикстура монтирует настоящий UserCredentials и receipt, настоящий session/transport/useAdmin и MemoryRouter; fetch подменён синтетическим сервером. Проверяются доступность login route, наличие/отсутствие фактического secret output, сохранность successor authority, unknown receipt и ровно один POST.
- Набор покрывает known self, lost ACK + held GET + refresh401, late self ACK после retirement, новый login того же/другого actor, login-start до ответа, смену роли и два non-self случая. Это проверки наблюдаемого результата и authority boundaries; число сетевых POST проверяет запрет повторения значимой мутации. Текст кнопок используется для выбора публичного действия; новые assertions не фиксируют точный wording, CSS или внутреннюю последовательность вызовов.
- `isolation-before-login-cancellation.log` сохраняет 9 PASS / 1 FAIL: поздний ACK во время начатого нового login терял successor 13. Текущий код явно отменяет intent по busy и запрещает публикацию при busy; итоговый лог подтверждает проход именно этого сценария. Исходные FAIL/UNKNOWN логи не заменены.
- `strict-web-typecheck.log` и итоговый `scoped-eslint-fixture-comments.log` пусты; успешные exit codes этих проверок сообщены автором/packet, сам reviewer их повторно не запускал. Самостоятельно подтверждается отсутствие diff whitespace errors.

## Границы PASS

Это независимое source/security review, а не новая native или backend приёмка. Mounted tests используют синтетические ответы, небольшой shell и parent boundary; не являются запуском полного UsersPage/App.vue в браузере либо реальным отзывом сессий Auth. Исходный backend/ownership proof и очистка ранее проверенной изолированной учётной записи не повторялись. Согласованная changed-behavior native verification и интеграция остаются у root/назначенного writer; CI/merge/deploy не выполнялись. Конкретного непокрытого source-риска, требующего ещё одного тестового пакета до интеграции, не найдено.

Создан только этот review-отчёт вне source worktree. Коррекции автору не требуются.
