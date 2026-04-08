// Создаём пользователя приложения в MongoDB при первом запуске
db = db.getSiblingDB('admin');
db.createUser({
  user: process.env.MONGO_USER || 'rct_user',
  pwd: process.env.MONGO_PASSWORD,
  roles: [
    { role: 'readWrite', db: 'notification_db' },
    { role: 'readWrite', db: 'attendance_db' },
    { role: 'dbAdmin', db: 'notification_db' },
    { role: 'dbAdmin', db: 'attendance_db' }
  ]
});
