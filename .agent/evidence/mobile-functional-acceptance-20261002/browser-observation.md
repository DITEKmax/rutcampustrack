# Первое открытие интегрированной PWA

Source d17455153cd26af8a30b969cf85cad210eb8543d; manifest 67ccdaf75fe9629ce853e3eac30a2ca160f065642bb422b0692b9faa8663eb47.
Run 20261002-203457516-sgjgvbyb, https://127.0.0.1:18514.

CUA IAB tab1 открыл PWA без TLS warning. Использован публичный synthetic seed account локальной disposable БД; реальные credentials не использовались. После отправки формы входа UI вернул «Academic Service временно недоступен». Повтор не выполнялся. Literal принадлежит MobileAcademicClient.call/MapAcademicClient, потому отказ всей цепочки не доказывает отказ Auth login. Owner стенда диагностирует сервисный ответ.

Три пользовательских результата (ONE_OFF create, DATE homework/edit, roster download) пока НЕ приняты. Сборки и source review не заменяют этот результат. Дизайн не изменялся; настоящий Telegram не подключался.

## R2 — наблюдаемый результат на a93f4265

Тот же origin, run20261002-205620261-kmyrcsnj; manifest43d733ce63b444ef9674e643e081928adf563a31d8ed0514367ff235f2d4013d. Один вход student прошёл; новая onefile diagnostics не меняла поведение, поэтому устранение R1 rootcause не утверждается. Safe MobileAcademicClient error logs отсутствуют. Переключение STUDENT→HEADMAN→STUDENT и восстановление HEADMAN после full reload прошли.

- ONE_OFF: через форму HEADMAN создана пара2026-10-05,09:00–10:30,number1,Локальная-101,assignment1. UI показал создание и canonical list. После fullreload TodaydateOct05 вернул сохранённую пару со временем и аудиторией. Bounded creation/persistence PASS.
- DATE homework: HEADMAN создал на2026-10-06/subject1 без пары, изменил описание. Серверный список подтвердил обновление, история показалаВерсия2/actor3. Послеfullreload→STUDENT→Задания→Раскрыть описание получен точный обновлённый текст «Обновлено: подготовить конспект и два примера к 6 октября.». Bounded creation/edit/persistence/role-read PASS. Nonpublisher/concurrent replay здесь не повторялись.
- Roster: UI загрузил пять форматов и состав. ЕдинственныйDOCXclick закончился «Файл передан браузеру для скачивания». CUA waitForEvent(download) превысил30s и resetkernel; локальный файл не получен. Сохранение на диск UNCONFIRMED, не утверждается поломка сервера. Повтора не было.
- Отдельный reproducible blocker: после studentToday при первомвходе и сменероли toast «Attendance Service временно недоступен». Он не мешал перечисленным ONE_OFF/DATEоперациям; причина ещё не установлена. Общее приложение НЕ принято полностью.

CUA screenshots состояния созданной пары и истории показаны в чате; отдельного сохранённого screenshotfile tool не вернул. Figma/redesign не выполнялись. TMA реального Telegram и external delivery не проверялись.
