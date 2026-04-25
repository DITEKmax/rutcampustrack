// Создаёт application users в admin DB при первом запуске Mongo
// контейнера (volume пустой). Bitnami legacy image запускает .js
// файлы из /docker-entrypoint-initdb.d/ через mongosh уже как root,
// после rs.initiate(), но до того, как контейнер становится healthy.
//
// Заменяет MONGODB_EXTRA_USERNAMES/PASSWORDS/DATABASES — declarative
// механизм Bitnami в digest sha256:16a57fa создавал users в `test` DB
// вместо `admin` (Spring подключается с ?authSource=admin →
// AuthenticationFailed). Init-script даёт явный контроль над auth DB.
//
// Идемпотентность: при повторном запуске на существующем volume
// /docker-entrypoint-initdb.d/ Bitnami НЕ выполняет (volume already
// initialised) — поэтому повторных createUser конфликтов нет.
//
// PoLP (M10 D2):
//   MONGO_USER              — readWrite + dbAdmin на attendance_db
//   MONGO_NOTIFICATION_USER — readWrite + dbAdmin на notification_db

db = db.getSiblingDB('admin');

db.createUser({
  user: process.env.MONGO_USER,
  pwd: process.env.MONGO_PASSWORD,
  roles: [
    { role: 'readWrite', db: 'attendance_db' },
    { role: 'dbAdmin', db: 'attendance_db' }
  ]
});

db.createUser({
  user: process.env.MONGO_NOTIFICATION_USER,
  pwd: process.env.MONGO_NOTIFICATION_PASSWORD,
  roles: [
    { role: 'readWrite', db: 'notification_db' },
    { role: 'dbAdmin', db: 'notification_db' }
  ]
});
