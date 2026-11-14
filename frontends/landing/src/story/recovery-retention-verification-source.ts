import type { StoryOperation } from './story-source'

export const recoveryRetentionVerificationOperations: StoryOperation[] = [
  {
    "id": "recovery-retention-verification-bundle",
    "title": "Манифест и пустая цель",
    "description": "Перед созданием комплекта оператор останавливает запись приложения, а скрипт сверяет состояние источника до и после копирования. Манифест формата 3 содержит режим файлов и SHA-256 каждого артефакта. Перед восстановлением скрипт проверяет состав комплекта и суммы. Писать можно только в указанный изолированный проект с нужной меткой и свежими PostgreSQL и MongoDB. Для режима с файлами каталог цели должен ещё отсутствовать.",
    "frames": [
      {
        "title": "Комплект сверяется с исходным состоянием",
        "path": "scripts/recovery.py",
        "language": "python",
        "lines": [
          [
            683,
            "def backup(args):"
          ],
          [
            684,
            "    mode = file_mode(args)"
          ],
          [
            685,
            "    require(args.quiesced, \"Stop application writers first; acknowledge using --quiesced\")"
          ],
          [
            686,
            "    require(not args.output.exists(), \"Backup output already exists; choose a new path\")"
          ],
          [
            730,
            "    with tarfile.open(args.output / \"files.tar\", \"x:\") as archive:"
          ],
          [
            731,
            "        for entry in before[\"files\"]:"
          ],
          [
            732,
            "            archive.add(args.files_dir / entry[\"path\"], arcname=entry[\"path\"], recursive=False)"
          ],
          [
            733,
            "    require(before == inventory(args, secret), \"Source changed during backup; incomplete bundle retained\")"
          ],
          [
            734,
            "    write_json(args.output / \"inventory.json\", before)"
          ],
          [
            735,
            "    write_json("
          ],
          [
            736,
            "        args.output / \"manifest.json\","
          ],
          [
            737,
            "        {\"format\": BUNDLE_FORMAT, \"files_mode\": mode, \"sha256\": {n: digest(args.output / n) for n in sorted(PAYLOADS)}},"
          ],
          [
            738,
            "    )"
          ],
          [
            739,
            "    verify_bundle(args.output, mode)"
          ],
          [
            740,
            "    print(f\"PASS backup: complete immutable format-3 PG/Mongo bundle with owner/ACL evidence; mode={mode}\")"
          ]
        ]
      },
      {
        "title": "Манифест и контрольные суммы проверяются до записи",
        "path": "scripts/recovery.py",
        "language": "python",
        "lines": [
          [
            620,
            "def verify_bundle(bundle, requested_mode=None):"
          ],
          [
            621,
            "    require(bundle.is_dir() and not bundle.is_symlink(), \"Bundle must be a real directory\")"
          ],
          [
            622,
            "    require("
          ],
          [
            623,
            "        {p.name for p in bundle.iterdir()} == PAYLOADS | {\"manifest.json\"},"
          ],
          [
            624,
            "        \"Bundle incomplete or contains unexpected artifacts\","
          ],
          [
            625,
            "    )"
          ],
          [
            626,
            "    for path in bundle.iterdir():"
          ],
          [
            627,
            "        require(path.is_file() and not path.is_symlink(), \"Bundle artifacts must be regular files\")"
          ],
          [
            628,
            "    manifest = json.loads((bundle / \"manifest.json\").read_text(encoding=\"utf-8\"))"
          ],
          [
            629,
            "    require("
          ],
          [
            630,
            "        manifest.get(\"format\") == BUNDLE_FORMAT and set(manifest.get(\"sha256\", {})) == PAYLOADS,"
          ],
          [
            631,
            "        \"Unsupported/incomplete manifest; format 3 with owner/ACL evidence is required\""
          ],
          [
            632,
            "    )"
          ],
          [
            633,
            "    mode = manifest.get(\"files_mode\")"
          ],
          [
            634,
            "    require(mode in (\"files\", \"database-only\"), \"Unsupported files mode in manifest\")"
          ],
          [
            635,
            "    require("
          ],
          [
            636,
            "        requested_mode is None or requested_mode == mode,"
          ],
          [
            637,
            "        \"Restore files mode does not match bundle; refused before target writes\","
          ],
          [
            638,
            "    )"
          ],
          [
            639,
            "    for name, expected in manifest[\"sha256\"].items():"
          ],
          [
            640,
            "        require(digest(bundle / name) == expected, f\"Integrity check failed: {name}\")"
          ]
        ]
      },
      {
        "title": "Манифест · условный фрагмент",
        "language": "json",
        "example": [
          "{",
          "  \"format\": 3,",
          "  \"files_mode\": \"database-only\",",
          "  \"sha256\": {",
          "    \"inventory.json\": \"0000000000000000000000000000000000000000000000000000000000000000\"",
          "  }",
          "}"
        ]
      },
      {
        "title": "Проверки изолированной и свежей цели",
        "path": "scripts/recovery.py",
        "language": "python",
        "lines": [
          [
            668,
            "def check_targets(args, restore=False):"
          ],
          [
            669,
            "    names = [args.academic_container, args.schedule_container, args.mongo_container]"
          ],
          [
            670,
            "    require(len(set(names)) == 3, \"Three distinct DB containers are required\")"
          ],
          [
            671,
            "    for name in names:"
          ],
          [
            672,
            "        info = json.loads(run([\"docker\", \"inspect\", name]))[0]"
          ],
          [
            673,
            "        require(info[\"State\"][\"Running\"], \"DB target is not running\")"
          ],
          [
            674,
            "        if restore:"
          ],
          [
            675,
            "            labels = info[\"Config\"].get(\"Labels\", {}) or {}"
          ],
          [
            676,
            "            require("
          ],
          [
            677,
            "                labels.get(\"io.rutcampustrack.recovery\") == \"disposable\""
          ],
          [
            678,
            "                and labels.get(\"com.docker.compose.project\") == args.target_project,"
          ],
          [
            679,
            "                \"Restore accepts only the explicit labeled disposable project\","
          ],
          [
            680,
            "            )"
          ],
          [
            743,
            "def restore(args):"
          ],
          [
            744,
            "    mode = file_mode(args)"
          ],
          [
            745,
            "    require(re.fullmatch(r\"rct-recovery-[a-z0-9-]+\", args.target_project), \"Target project must start rct-recovery-\")"
          ],
          [
            746,
            "    expected = verify_bundle(args.bundle, mode)"
          ],
          [
            747,
            "    if mode == \"files\":"
          ],
          [
            748,
            "        require(not args.files_dir.exists(), \"Restore files target must not exist\")"
          ],
          [
            764,
            "        require(count == \"0\", \"PostgreSQL target is not fresh; restore refused\")"
          ],
          [
            765,
            "    counts = mongo_query("
          ],
          [
            770,
            "    )"
          ],
          [
            771,
            "    require(counts == [0, 0], \"Mongo target is not fresh; restore refused\")"
          ]
        ]
      }
    ]
  },
  {
    "id": "recovery-retention-verification-readback",
    "title": "Проверка после восстановления",
    "description": "Тест хранилищ портит отдельную копию архива и проверяет отказы при несовпадении проекта и повторе в непустую цель. Затем сравниваются данные, схемы, права, индексы и файлы. Отдельная прикладная проверка ждёт завершения восстановленного переноса пары, сверяет исходную и целевую пару через Schedule и историю пользователя. Повтор исходной команды должен вернуть тот же результат и сохранить историю.",
    "frames": [
      {
        "title": "Синтетические проверки отказов",
        "path": "scripts/test-recovery.py",
        "language": "python",
        "lines": [
          [
            322,
            "    # Corrupt an expendable copy without touching the real bundle. Integrity rejection"
          ],
          [
            323,
            "    # happens before any SQL/files write; full restore on the same fresh target proves it."
          ],
          [
            324,
            "    import shutil"
          ],
          [
            325,
            ""
          ],
          [
            326,
            "    args.failure_stage = \"REFUSAL_CORRUPT\""
          ],
          [
            327,
            "    damaged = args.work_dir / \"damaged-copy\""
          ],
          [
            328,
            "    shutil.copytree(source.output, damaged)"
          ],
          [
            329,
            "    with (damaged / \"academic.dump\").open(\"ab\") as stream:"
          ],
          [
            330,
            "        stream.write(b\"corrupt\")"
          ],
          [
            331,
            "    dest.bundle = damaged"
          ],
          [
            332,
            "    expect_refusal(lambda: r.restore(dest), \"corrupt archive before any target write\")"
          ],
          [
            333,
            "    dest.bundle = source.output"
          ],
          [
            334,
            "    args.failure_stage = \"REFUSAL_FOREIGN\""
          ],
          [
            335,
            "    original_project = dest.target_project"
          ],
          [
            336,
            "    dest.target_project = args.project_base + \"-foreign\""
          ],
          [
            337,
            "    expect_refusal(lambda: r.restore(dest), \"project label mismatch\")"
          ],
          [
            338,
            "    dest.target_project = original_project"
          ],
          [
            339,
            "    args.failure_stage = \"RESTORE\""
          ],
          [
            340,
            "    r.restore(dest)"
          ],
          [
            368,
            "    args.failure_stage = \"REFUSAL_NONEMPTY\""
          ],
          [
            369,
            "    dest.files_dir = args.work_dir / \"never-created-repeat-files\""
          ],
          [
            370,
            "    expect_refusal(lambda: r.restore(dest), \"nonempty database target\")"
          ],
          [
            371,
            "    args.failure_stage = \"WRITE_PASS\""
          ],
          [
            372,
            "    r.write_json("
          ],
          [
            373,
            "        args.work_dir / \"PASS.json\","
          ],
          [
            374,
            "        {"
          ],
          [
            375,
            "            \"result\": \"PASS\","
          ],
          [
            376,
            "            \"projects\": projects,"
          ],
          [
            377,
            "            \"bundle_manifest_sha256\": r.digest(source.output / \"manifest.json\"),"
          ],
          [
            378,
            "            \"checks\": ["
          ],
          [
            379,
            "                \"all three PG databases: exact data/schema/sequences/owners/ACL/default privileges\","
          ],
          [
            380,
            "                \"production principals (no rct_user); all runtime column/function grants and protected DML/DDL/monitor/cross-DB denial\","
          ],
          [
            381,
            "                \"both Mongo documents/indexes/options\","
          ],
          [
            382,
            "                \"all file bytes/paths\","
          ],
          [
            383,
            "                \"overwrite/corruption/project/nonempty refusal\","
          ]
        ]
      },
      {
        "title": "Повторная сверка восстановленного комплекта",
        "path": "scripts/recovery.py",
        "language": "python",
        "lines": [
          [
            904,
            "    compare_restored_inventory(args, secret, expected)"
          ],
          [
            905,
            "    print(f\"PASS restore: PostgreSQL data/schema/sequences/owners/ACL, Mongo documents/indexes/options match source; mode={mode}\")"
          ],
          [
            908,
            "def verify_restored(args):"
          ],
          [
            909,
            "    mode = file_mode(args)"
          ],
          [
            910,
            "    require(args.quiesced, \"Keep target isolated and stop application writers; acknowledge using --quiesced\")"
          ],
          [
            911,
            "    require(re.fullmatch(r\"rct-recovery-[a-z0-9-]+\", args.target_project), \"Target project must start rct-recovery-\")"
          ],
          [
            912,
            "    expected = verify_bundle(args.bundle, mode)"
          ],
          [
            913,
            "    if args.dry_run:"
          ],
          [
            914,
            "        print("
          ],
          [
            915,
            "            \"PASS bundle integrity; PLAN verify isolated restored targets; schema-only scratch references; no archive replay\""
          ],
          [
            916,
            "        )"
          ],
          [
            917,
            "        return"
          ],
          [
            919,
            "    check_targets(args, restore=True)"
          ],
          [
            920,
            "    compare_restored_inventory(args, secret, expected)"
          ],
          [
            921,
            "    print("
          ],
          [
            922,
            "        f\"PASS verify-restored: exact data/sequences/Mongo/files and PostgreSQL-native schema references; mode={mode}\""
          ],
          [
            923,
            "    )"
          ]
        ]
      },
      {
        "title": "Приложение видит прежний и новый экземпляры пары",
        "path": "scripts/test-application-recovery.py",
        "language": "python",
        "lines": [
          [
            517,
            "    lessons = ["
          ],
          [
            518,
            "        lesson for value in response.get(\"_embedded\", {}).values() if isinstance(value, list) for lesson in value"
          ],
          [
            519,
            "    ]"
          ],
          [
            520,
            "    selected = {int(v[\"id\"]): v for v in lessons if int(v[\"id\"]) in (op[\"source_lesson_id\"], op[\"target_lesson_id\"])}"
          ],
          [
            521,
            "    r.require(len(selected) == 2, \"Application schedule must expose source history and current target\")"
          ],
          [
            522,
            "    source, target = selected[op[\"source_lesson_id\"]], selected[op[\"target_lesson_id\"]]"
          ],
          [
            523,
            "    r.require("
          ],
          [
            524,
            "        source[\"current\"] is False"
          ],
          [
            525,
            "        and source[\"status\"].upper() == \"TRANSFERRED\""
          ],
          [
            526,
            "        and target[\"current\"] is True"
          ],
          [
            527,
            "        and target[\"status\"].upper() == \"PLANNED\""
          ],
          [
            528,
            "        and int(target[\"generation\"]) == op[\"target_generation\"]"
          ],
          [
            529,
            "        and int(target[\"occurrenceRevision\"]) == op[\"result_occurrence_revision\"]"
          ],
          [
            530,
            "        and target[\"transferOperationId\"] == op[\"operation_id\"]"
          ],
          [
            531,
            "        and target[\"transferState\"] == \"COMPLETED\","
          ],
          [
            532,
            "        \"Application schedule has incorrect canonical flags/generation/transfer\","
          ],
          [
            533,
            "    )"
          ]
        ]
      },
      {
        "title": "Завершение, повтор команды и сверка истории",
        "path": "scripts/test-application-recovery.py",
        "language": "python",
        "lines": [
          [
            556,
            "    status_url = args.schedule_url.rstrip(\"/\") + \"/schedule/lesson-transfers/\" + op[\"operation_id\"]"
          ],
          [
            557,
            "    deadline = time.monotonic() + args.timeout_seconds"
          ],
          [
            558,
            "    while True:"
          ],
          [
            559,
            "        status = http(status_url, args.actor_token_file)"
          ],
          [
            560,
            "        r.require("
          ],
          [
            561,
            "            status[\"operationId\"] == op[\"operation_id\"] and status[\"state\"] != \"ERROR\","
          ],
          [
            562,
            "            \"Restored API operation identity/error mismatch\","
          ],
          [
            563,
            "        )"
          ],
          [
            564,
            "        if status[\"state\"] == \"COMPLETED\":"
          ],
          [
            565,
            "            break"
          ],
          [
            566,
            "        r.require(time.monotonic() < deadline, \"Timed out waiting for restored real participants\")"
          ],
          [
            567,
            "        time.sleep(2)"
          ],
          [
            568,
            "    actual = database_snapshot(args, op, secret)"
          ],
          [
            569,
            "    validate_terminal(actual, baseline)"
          ],
          [
            570,
            "    schedule = schedule_readback(args, op)"
          ],
          [
            571,
            "    history = pointer(http(args.history_url, args.history_token_file), baseline[\"history_pointer\"])"
          ],
          [
            572,
            "    r.require(history == baseline[\"history\"], \"User API historical projection changed after restore\")"
          ],
          [
            578,
            "    r.require(replay == status, \"Exact accepted HTTP replay changed the operation result\")"
          ],
          [
            579,
            "    after = database_snapshot(args, op, secret)"
          ],
          [
            580,
            "    validate_terminal(after, baseline)"
          ],
          [
            581,
            "    # Publisher bookkeeping is allowed to finish independently; business history is not."
          ],
          [
            582,
            "    stable = lambda value: {k: v for k, v in value.items() if k != \"outbox\"}"
          ],
          [
            583,
            "    r.require(stable(after) == stable(actual), \"Exact HTTP replay changed business state/history\")"
          ],
          [
            584,
            "    r.require(schedule_readback(args, op) == schedule, \"Application canonical schedule changed after exact replay\")"
          ],
          [
            585,
            "    r.require("
          ],
          [
            586,
            "        pointer(http(args.history_url, args.history_token_file), baseline[\"history_pointer\"]) == history,"
          ],
          [
            587,
            "        \"User history changed after exact replay\","
          ],
          [
            588,
            "    )"
          ]
        ]
      }
    ]
  },
  {
    "id": "recovery-retention-verification-short-lived",
    "title": "Сеанс, билет и файл отчёта",
    "description": "Список действующих сеансов исключает отозванные и просроченные по refresh_expires_at. Скачивание отчёта использует отдельный билет, действующий не дольше 60 секунд и ограниченный полномочиями и сеансом. Файл готового отчёта доступен до expires_at, не более 10 минут от создания задачи. Фоновая очистка позже удаляет просроченные строки и их файлы.",
    "frames": [
      {
        "title": "Сеанс уже исключён по сроку и отзыву",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcAuthSessionQueryAdapter.java",
        "language": "java",
        "lines": [
          [
            52,
            "    private static final String LIVE_SESSIONS_SQL ="
          ],
          [
            53,
            "            \"\"\""
          ],
          [
            54,
            "            SELECT sid, auth_method, client_label, location_label,"
          ],
          [
            55,
            "                   created_at, last_seen_at"
          ],
          [
            56,
            "            FROM auth_sessions"
          ],
          [
            57,
            "            WHERE user_id = ?"
          ],
          [
            58,
            "              AND revoked_at IS NULL"
          ],
          [
            59,
            "              AND refresh_expires_at > ?"
          ],
          [
            60,
            "            ORDER BY created_at DESC, sid DESC"
          ],
          [
            61,
            "            LIMIT ?"
          ],
          [
            62,
            "            \"\"\";"
          ]
        ]
      },
      {
        "title": "Срок билета ограничен допуском и сеансом",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/authority/AuthReportCapabilityService.java",
        "language": "java",
        "lines": [
          [
            39,
            "    private static final Duration TICKET_TTL = Duration.ofSeconds(60);"
          ],
          [
            174,
            "        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);"
          ],
          [
            175,
            "        Instant expires ="
          ],
          [
            176,
            "                minimum("
          ],
          [
            177,
            "                        minimum(now.plus(TICKET_TTL), Instant.parse(authority.getNotAfter())),"
          ],
          [
            178,
            "                        Instant.parse(admitted.getExpiresAt()));"
          ],
          [
            179,
            "        if (!now.isBefore(expires)) throw Status.PERMISSION_DENIED.asRuntimeException();"
          ]
        ]
      },
      {
        "title": "Граница чтения файла и пакетное удаление строк",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exportjobs/ExportJobRepository.java",
        "language": "java",
        "lines": [
          [
            65,
            "        UUID id = UUID.randomUUID();"
          ],
          [
            66,
            "        // Database timestamp is the retention origin, including time spent admitting this request."
          ],
          [
            67,
            "        jdbc.update("
          ],
          [
            68,
            "                \"INSERT INTO\""
          ],
          [
            69,
            "                    + \" academic_export_jobs(job_id,user_id,session_id,request_id,binding_hash,identity,requires_full,expires_at)\""
          ],
          [
            70,
            "                    + \" VALUES (?,?,?,?,?,?::jsonb,?,LEAST(?,NOW()+interval '10 minutes'))\","
          ],
          [
            248,
            "        var rows ="
          ],
          [
            249,
            "                jdbc.query("
          ],
          [
            250,
            "                        \"SELECT content FROM academic_export_jobs WHERE job_id=? AND state='ready'\""
          ],
          [
            251,
            "                                + \" AND expires_at>NOW()\""
          ],
          [
            252,
            "                                + readyGuard(job.identity()),"
          ],
          [
            253,
            "                        (rs, n) -> rs.getBytes(1),"
          ],
          [
            254,
            "                        arguments.toArray());"
          ],
          [
            383,
            "    private void expire(int limit) {"
          ],
          [
            384,
            "        jdbc.query("
          ],
          [
            385,
            "                \"DELETE FROM academic_export_jobs WHERE job_id IN (SELECT job_id FROM\""
          ],
          [
            386,
            "                        + \" academic_export_jobs WHERE expires_at<=NOW() ORDER BY expires_at,job_id\""
          ],
          [
            387,
            "                        + \" LIMIT ? FOR UPDATE SKIP LOCKED) RETURNING identity,state,created_at\","
          ]
        ]
      }
    ]
  },
  {
    "id": "recovery-retention-verification-history",
    "title": "Вложения и история действий",
    "description": "Для вложения к заявке сервер прибавляет к загрузке календарный год в UTC. В момент expiresAt читать байты уже нельзя. Фоновая очистка обнуляет байты, сохраняя запись о вложении. Аудит уроков хранится отдельно. У коллекции lesson_actions нет TTL. Сроки резервных копий и общая политика хранения определяются отдельно от этих ограничений приложения.",
    "frames": [
      {
        "title": "Календарный год от загрузки и отказ чтения по сроку",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java",
        "language": "java",
        "lines": [
          [
            648,
            "        Instant now = retentionClock.instant();"
          ],
          [
            649,
            "        if (document.getState() == AttachmentState.EXPIRED"
          ],
          [
            650,
            "                || document.getExpiresAt() == null"
          ],
          [
            651,
            "                || !now.isBefore(document.getExpiresAt())) {"
          ],
          [
            652,
            "            throw new ResponseStatusException(HttpStatus.GONE, \"Срок хранения вложения истёк\");"
          ],
          [
            653,
            "        }"
          ],
          [
            654,
            "        if (document.getData() == null) {"
          ],
          [
            655,
            "            throw new ResponseStatusException(HttpStatus.GONE, \"Вложение больше недоступно\");"
          ],
          [
            656,
            "        }"
          ],
          [
            657,
            "        return new AttachmentDownload("
          ],
          [
            658,
            "                document.getData().getData(), document.getContentType(), document.getName());"
          ],
          [
            1128,
            "        List<RequestAttachmentDescriptorDocument> descriptors = new ArrayList<>();"
          ],
          [
            1129,
            "        for (int position = 0; position < files.items().size(); position++) {"
          ],
          [
            1130,
            "            ValidatedAttachment file = files.items().get(position);"
          ],
          [
            1131,
            "            Instant uploadedAt = retentionClock.instant();"
          ],
          [
            1132,
            "            Instant expiresAt = uploadedAt.atZone(ZoneOffset.UTC).plusYears(1).toInstant();"
          ],
          [
            1133,
            "            RequestAttachmentDocument attachment ="
          ],
          [
            1134,
            "                    RequestAttachmentDocument.builder()"
          ]
        ]
      },
      {
        "title": "Очистка байтов сохраняет запись",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java",
        "language": "java",
        "lines": [
          [
            3379,
            "    private void expireOne(RequestAttachmentDocument document, Instant now) {"
          ],
          [
            3380,
            "        if (document.getState() == AttachmentState.EXPIRED && document.getData() == null) {"
          ],
          [
            3381,
            "            return;"
          ],
          [
            3382,
            "        }"
          ],
          [
            3383,
            "        document.setState(AttachmentState.EXPIRED);"
          ],
          [
            3384,
            "        document.setExpiredAt(now);"
          ],
          [
            3385,
            "        document.setData(null);"
          ],
          [
            3386,
            "        attachmentRepository.save(document);"
          ],
          [
            3387,
            "    }"
          ]
        ]
      },
      {
        "title": "Аудит уроков хранится в отдельной коллекции без TTL",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/audit/LessonActionDocument.java",
        "language": "java",
        "lines": [
          [
            11,
            "/** Permanent domain history. Deliberately has no TTL and is separate from outbox/inbox cleanup. */"
          ],
          [
            12,
            "@Data"
          ],
          [
            13,
            "@Document(\"lesson_actions\")"
          ],
          [
            14,
            "@CompoundIndex(name = \"lesson_action_page\", def = \"{'lesson_id':1,'occurred_at':-1,'_id':-1}\")"
          ],
          [
            15,
            "public class LessonActionDocument {"
          ],
          [
            16,
            "    @Id private String id;"
          ],
          [
            17,
            ""
          ],
          [
            18,
            "    @Field(\"lesson_id\")"
          ],
          [
            19,
            "    private Long lessonId;"
          ],
          [
            20,
            ""
          ],
          [
            21,
            "    @Field(\"group_id\")"
          ],
          [
            22,
            "    private Long groupId;"
          ],
          [
            23,
            ""
          ],
          [
            24,
            "    @Field(\"semester_id\")"
          ],
          [
            25,
            "    private Long semesterId;"
          ],
          [
            26,
            ""
          ],
          [
            27,
            "    @Field(\"occurred_at\")"
          ],
          [
            28,
            "    private Instant occurredAt;"
          ]
        ]
      }
    ]
  },
  {
    "id": "recovery-retention-verification-checks",
    "title": "Геоотметка на трёх уровнях",
    "description": "Unit-тест подставляет источник координат и проверяет, что второе чтение завершает поиск одной итоговой записью диагностики. Интеграционный тест Attendance сохраняет заявку и квитанцию в MongoDB, проверяет повтор результата и единственные события outbox. Сценарий Playwright на тестовом стенде нажимает «Отметиться», получает заявку от API, проверяет повтор и одобрение старостой. После одобрения API показывает PRESENT с источником LATE_CHECKIN.",
    "frames": [
      {
        "title": "Unit · второе чтение и один итог диагностики",
        "path": "frontends/mobile-core/src/device-access/geolocation.test.ts",
        "language": "typescript",
        "lines": [
          [
            105,
            "  it('automatically reaches an inside position on the second reading and emits one terminal report', async () => {"
          ],
          [
            106,
            "    vi.useFakeTimers()"
          ],
          [
            107,
            "    const f = fixture()"
          ],
          [
            108,
            "    const terminal = vi.fn()"
          ],
          [
            109,
            "    const run = acquireGeolocation(f.source, { geofence: policy, terminal })"
          ],
          [
            110,
            "    f.emit(600)"
          ],
          [
            111,
            "    await next()"
          ],
          [
            112,
            "    f.emit(300)"
          ],
          [
            113,
            "    await expect(run).resolves.toMatchObject({"
          ],
          [
            114,
            "      sampleCount: 2,"
          ],
          [
            115,
            "      reason: 'WITHIN_GEOFENCE',"
          ],
          [
            116,
            "      sample: { latitude: at(300) },"
          ],
          [
            117,
            "    })"
          ],
          [
            118,
            "    expect(f.watch).toHaveBeenCalledTimes(2)"
          ],
          [
            119,
            "    expect(terminal).toHaveBeenCalledOnce()"
          ],
          [
            120,
            "  })"
          ]
        ]
      },
      {
        "title": "Integration · повтор сохраняет одну заявку",
        "path": "services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/student/StudentCheckinTransactionIT.java",
        "language": "java",
        "lines": [
          [
            797,
            "    void realOutboxCommitsOnceAndReplayDoesNotDuplicateEvent() {"
          ],
          [
            798,
            "        MongoOutboxStorage outbox = new MongoOutboxStorage(mongoTemplate, \"attendance_outbox\");"
          ],
          [
            799,
            "        StudentCheckinService realService = serviceWith(outbox);"
          ],
          [
            800,
            ""
          ],
          [
            801,
            "        Ack first ="
          ],
          [
            802,
            "                realService.checkin("
          ],
          [
            803,
            "                        student, lesson(), \"key-000000000001\", new Unavailable(\"TIMEOUT\"));"
          ],
          [
            804,
            "        Ack replay ="
          ],
          [
            805,
            "                realService.checkin("
          ],
          [
            806,
            "                        student, lesson(), \"key-000000000001\", new Unavailable(\"TIMEOUT\"));"
          ],
          [
            807,
            ""
          ],
          [
            808,
            "        assertThat(replay).isEqualTo(first);"
          ],
          [
            809,
            "        clock.set(first.retryAt());"
          ],
          [
            813,
            "        assertThat(retryFailure.request().id()).isEqualTo(first.request().id());"
          ],
          [
            814,
            "        assertThat("
          ],
          [
            815,
            "                        realService.checkin("
          ],
          [
            816,
            "                                student, lesson(), \"key-000000000002\", new Unavailable(\"TIMEOUT\")))"
          ],
          [
            817,
            "                .isEqualTo(retryFailure);"
          ],
          [
            818,
            "        assertThat(outbox.findPending(10))"
          ],
          [
            819,
            "                .extracting(record -> record.eventType())"
          ],
          [
            820,
            "                .containsExactlyInAnyOrder(\"late_checkin.requested\", \"dashboard.invalidated\");"
          ],
          [
            821,
            "        assertThat(lateCheckinRepository.findAll()).hasSize(1);"
          ],
          [
            822,
            "        assertThat(receiptRepository.findAll()).hasSize(2);"
          ],
          [
            823,
            "    }"
          ]
        ]
      },
      {
        "title": "Playwright · действие, API-ответ и отметка после решения",
        "path": "tests/e2e/specs/mobile-attendance-requests.spec.ts",
        "language": "typescript",
        "lines": [
          [
            660,
            "  await context.grantPermissions(['geolocation']);"
          ],
          [
            661,
            "  await context.setGeolocation({ latitude: 0, longitude: 0, accuracy: 1 });"
          ],
          [
            662,
            "  await page.getByRole('button', { name: 'Сегодня', exact: true }).click();"
          ],
          [
            663,
            "  const checkinPath = `/api/v1/student/lessons/${lessonId}/checkin`;"
          ],
          [
            664,
            "  const failedGeo = page.waitForResponse("
          ],
          [
            665,
            "    (response) =>"
          ],
          [
            666,
            "      new URL(response.url()).pathname === checkinPath && response.request().method() === 'POST',"
          ],
          [
            667,
            "  );"
          ],
          [
            668,
            "  await page.getByRole('button', { name: 'Отметиться', exact: true }).click();"
          ],
          [
            669,
            "  const response = await failedGeo;"
          ],
          [
            670,
            "  expect(response.status()).toBe(200);"
          ],
          [
            671,
            "  const ack = await response.json();"
          ],
          [
            672,
            "  expect(ack.outcome).toBe('PENDING_CONFIRMATION');"
          ],
          [
            673,
            "  expect(ack.attendance).toBeNull();"
          ],
          [
            674,
            "  expect(ack.request.status).toBe('PENDING');"
          ],
          [
            675,
            "  expect(ack.request.origin).toBe('AUTO_GEO_FAILURE');"
          ],
          [
            686,
            "  expect(replay.status()).toBe(200);"
          ],
          [
            687,
            "  expect(await replay.json()).toEqual(ack);"
          ],
          [
            688,
            "  const open = await student.call('GET', '/api/v1/student/requests?bucket=OPEN&page=0&size=50');"
          ],
          [
            689,
            "  expect(open.content.filter((row: { id: string }) => row.id === requestId).length).toBe(1);"
          ],
          [
            690,
            "  expect("
          ],
          [
            691,
            "    open.content.filter((row: { origin: string }) => row.origin === 'AUTO_GEO_FAILURE').length,"
          ],
          [
            692,
            "  ).toBe(1);"
          ],
          [
            895,
            "  await page.getByRole('button', { name: 'Одобрить 1 пары', exact: true }).click();"
          ],
          [
            896,
            "  const decision = await decided;"
          ],
          [
            897,
            "  expect(decision.status()).toBe(200);"
          ],
          [
            898,
            "  expect((await decision.json()).summary.status).toBe('APPROVED');"
          ],
          [
            899,
            "  await expect(page.getByText('Одобрена', { exact: true })).toBeVisible();"
          ],
          [
            900,
            "  const approved = await student.call('GET', `/api/v1/student/requests/${requestId}`);"
          ],
          [
            901,
            "  expect(approved.summary.status).toBe('APPROVED');"
          ],
          [
            902,
            "  expect((await today()).attendance).toMatchObject({ status: 'PRESENT', source: 'LATE_CHECKIN' });"
          ],
          [
            903,
            "  expect((await options()).budget).toEqual(exhausted);"
          ]
        ]
      },
      {
        "title": "Поля ответа на неуспешную геопроверку",
        "language": "json",
        "example": [
          "{",
          "  \"outcome\": \"PENDING_CONFIRMATION\",",
          "  \"attendance\": null,",
          "  \"request\": {",
          "    \"status\": \"PENDING\",",
          "    \"origin\": \"AUTO_GEO_FAILURE\"",
          "  }",
          "}"
        ]
      }
    ]
  }
]
