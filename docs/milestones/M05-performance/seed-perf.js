// M05 Performance baseline seed — MongoDB (attendance_db).
//
// Назначение: наполнить collection late_checkin_requests реалистичным
// объёмом для EXPLAIN на hot query
//   findByGroupIdAndStatusOrderByCreatedAtAsc(groupId, PENDING)
// (closes 04 P2-9).
//
// Запуск:
//   mongosh "mongodb://rct_user:rct_dev_pass@localhost:27017/attendance_db?authSource=admin" \
//     --file docs/milestones/M05-performance/seed-perf.js
//
// Idempotent: удаляет ранее seed'нутые docs (student_id >= 900500)
// перед reinsert'ом.
//
// Объём: 20 групп × 300 requests = 6000 docs.
// Статусы: 40% PENDING, 40% APPROVED, 20% REJECTED.

print('=== M05 Mongo seed started ===');

const db_ = db.getSiblingDB('attendance_db');
const coll = db_.getCollection('late_checkin_requests');

// 1. Idempotent cleanup.
const delRes = coll.deleteMany({ student_id: { $gte: 900500 } });
print(`Cleaned ${delRes.deletedCount} prior seed docs`);

// 2. Seed: 20 групп (id 900001..900020) × 300 requests.
// student_id ∈ [900500 + group_id * 25 ... +24].
// lesson_id произвольный (группировка по group_id, lesson_id нам тут не важен).
// created_at равномерно распределён в последние 30 дней.

const bulk = coll.initializeUnorderedBulkOp();
const now = new Date();
const dayMs = 24 * 60 * 60 * 1000;

const statuses = [
    'PENDING', 'PENDING', 'PENDING', 'PENDING',                 // 40%
    'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED',             // 40%
    'REJECTED', 'REJECTED'                                      // 20%
];

let counter = 0;
for (let g = 1; g <= 20; g++) {
    const groupId = 900000 + g;
    for (let r = 1; r <= 300; r++) {
        const studentOffset = ((r - 1) % 25);
        const studentId = 900500 + (g - 1) * 25 + studentOffset;
        const lessonId = 800000 + ((g * 300 + r) % 10000);
        const status = statuses[r % statuses.length];
        const createdOffset = ((r * 137) % 30); // 0..29 days ago
        const createdAt = new Date(now.getTime() - createdOffset * dayMs);

        const doc = {
            student_id: studentId,
            group_id: groupId,
            lesson_id: lessonId,
            student_name: 'M05 Student g' + g + ' r' + r,
            status: status,
            created_at: createdAt,
            updated_at: createdAt,
            _class: 'ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest'
        };
        if (status !== 'PENDING') {
            doc.decision_by = 900100 + (r % 20);
            doc.decision_at = new Date(createdAt.getTime() + 3600 * 1000);
        }
        bulk.insert(doc);
        counter++;
    }
}

const res = bulk.execute();
print(`Inserted ${res.nInserted} late_checkin_requests (target: ${counter})`);

// 3. Статистика.
const total = coll.countDocuments({ student_id: { $gte: 900500 } });
const pending = coll.countDocuments({
    student_id: { $gte: 900500 },
    status: 'PENDING'
});
print(`Seed docs total=${total}, PENDING=${pending}`);

print('=== M05 Mongo seed complete ===');
