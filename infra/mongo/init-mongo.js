// Создаём application users в MongoDB при первом запуске контейнера.
// Root-пользователь уже создан через MONGO_INITDB_ROOT_USERNAME / _PASSWORD
// в docker-compose*.yml; здесь создаём сервисные users с PoLP (M10 D2).
//
// - MONGO_USER — attendance-service (readWrite + dbAdmin на attendance_db)
// - MONGO_NOTIFICATION_USER — notification-web (readWrite + dbAdmin на
//   notification_db) — отдельный credential для изоляции blast-radius.

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
