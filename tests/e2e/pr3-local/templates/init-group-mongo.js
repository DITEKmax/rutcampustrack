// Fresh owned Mongo attendance_db only. No account/foreign database changes.
const target=db.getSiblingDB('attendance_db');
const expected=["attendances", "late_checkin_requests", "excuse_tickets", "request_attachments", "lesson_actions", "student_checkin_pairs", "student_checkin_receipts", "student_request_receipts", "lesson_cancellation_markers", "group_deletion_participants", "group_deletion_fences", "group_deletion_lesson_scopes", "group_deletion_lessons"];
for(const name of expected){if(!target.getCollectionNames().includes(name))target.createCollection(name);}

// Fresh same-RS database, distinct app principal; no attendance permissions.
const notification=db.getSiblingDB('notification_db');
if(notification.getUser('rct_group_notification'))throw Error('Unexpected existing notification principal');
const password=process.env.GROUP_MONGO_NOTIFICATION_PASSWORD;
if(!/^[A-Za-z0-9_-]{43}$/.test(password||''))throw Error('Missing scoped notification password');
notification.createUser({user:'rct_group_notification',pwd:password,roles:[{role:'readWrite',db:'notification_db'}]});
for(const name of ['event_consumer_processed','notification_history','notification_event_audiences','notification_webpush_intents','user_notification_preferences','telegram_notification_preferences','notification_preference_legacy_bindings','notification_preference_mutation_receipts','push_subscriptions','reminder_attendance_state','shedLock']){
 if(!notification.getCollectionNames().includes(name))notification.createCollection(name);
}
