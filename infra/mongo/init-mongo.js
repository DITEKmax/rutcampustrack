// Создаём пользователя приложения в MongoDB при первом запуске.
// Root-пользователь уже создан через MONGO_INITDB_ROOT_USERNAME / _PASSWORD
// в docker-compose.prod.yml; здесь создаём отдельного app user.
db = db.getSiblingDB('admin');
db.createUser({
  user: process.env.MONGO_USER,
  pwd: process.env.MONGO_PASSWORD,
  roles: [
    { role: 'readWrite', db: 'notification_db' },
    { role: 'dbAdmin', db: 'notification_db' },
    { role: 'readWrite', db: 'attendance_db' },
    { role: 'dbAdmin', db: 'attendance_db' }
  ]
});
