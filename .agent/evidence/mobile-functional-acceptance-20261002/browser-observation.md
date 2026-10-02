# Первое открытие интегрированной PWA

Source d17455153cd26af8a30b969cf85cad210eb8543d; manifest 67ccdaf75fe9629ce853e3eac30a2ca160f065642bb422b0692b9faa8663eb47.
Run 20261002-203457516-sgjgvbyb, https://127.0.0.1:18514.

CUA IAB tab1 открыл PWA без TLS warning. Использован публичный synthetic seed account локальной disposable БД; реальные credentials не использовались. После отправки формы входа UI вернул «Academic Service временно недоступен». Повтор не выполнялся. Literal принадлежит MobileAcademicClient.call/MapAcademicClient, потому отказ всей цепочки не доказывает отказ Auth login. Owner стенда диагностирует сервисный ответ.

Три пользовательских результата (ONE_OFF create, DATE homework/edit, roster download) пока НЕ приняты. Сборки и source review не заменяют этот результат. Дизайн не изменялся; настоящий Telegram не подключался.
