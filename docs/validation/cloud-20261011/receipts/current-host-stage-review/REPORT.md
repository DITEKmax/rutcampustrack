# Независимый current host stage review

**APPROVE_CURRENT8_STAGE_AND_SOURCE**, manifest `6fa4a78f3a3b3a5593456487306da86964a1f8ec078f75ef37ab6e68b80b7cd2`. Blocking JAR payload/loader/Renderer-overlay finding нет. Отдельный browser-serving prerequisite **STAGE-PRE01 PENDING**: отсутствуют новые public Vue22 copies для planned nginx binds. Это не defect завершённого JAR stage. App/browser/TLS/native UID/mount/tools/conversion runtime **NOT_RUN**; report не даёт launch grant.

READ_ONLY `export_ebsr_recheck`, requested Sol6.1/xhigh, actual UNKNOWN; NEW output только `/workspace/scratch/rct-validation/current_host_stage_review`. Different stage author. Product/root/author/previous seals не изменены. Нет JVM/Gradle/Docker/network/keys/private ENV/apps/browser/process children; только local source/metadata/hash/ZIP/pure parsers/schema validation. Sensitive bodies/logs/private keys не читались. Root state/T002/T003/CURRENT/resources/new packet восстановлены; TASK8cbd27d4 unchanged, full-file identity прочитана против ранее полностью прочитанных originals. NP heavy/formatter ресурсы не затронуты.

## Receipts и actual tree

Независимо проверены26 original files+manifest/27 ZIP names, bytes/SHA/CRC. Author archive `30ed29a619cc90f34031ec0ec4b4a1b1fb45ad961b21f66d739f3f5298590268`,1324465B. Root reconstructed parts ZIP `c6aa13d50ed6ef3b63937e46f787fd1768d868a75380ef130bb32abff44f1cea`, same1324465B: outer bytes отличаются, **все27 member names/bytes exact** author. Root all27 readable views/wrappers lossless по original-byte-index. Сохраняются оба digests; это member-byte equivalence, не outer-source ZIP byteidentity. Source corruption не найден.

Author native stdlib stage exit0/helper218459 absent; reviewer также увидел отсутствие /proc/218459. stage.py прочитан целиком: accepted seals/current JAR hashes, safe canonical paths, unique regular ZIP entries, full EOF CRC/SHA/length, unambiguous layer mapping. Первый distinct content — NEW O_EXCL/O_NOFOLLOW nlink1 temp, fchmod0644 до links, fsync/atomic rename; aliases только внутри нового stage. Нет old600/source/JAR hardlink или chmod. Failures honest partial, cleanup лишь точного own temp; success после final checks. Source/helper guards не заменяют actual filesystem readback ниже.

| Independently checked actual data | Result |
| --- | ---: |
| Original regular member mappings |10834|
| Complete layer+flat app paths |21668|
| Unique content inodes, каждый SHA заново проверен |2937|
| Exact expected directories /mode |813 /0755|
| All regular file modes |0644|
| Missing/extra/symlink/collision/temp |0|
| Unique payload allocation |172953600B|
| Directory allocation |4784128B|

Каждый path/member/service/layer/size/hash и actual captured device/inode/nlink/UIDGID/mtime/ctime/blocks/alias membership сверены. All nlink равны own alias counts; original JAR inode pairs disjoint. Directory set независимо выведен из member parents+четырёх layer roots, включая empty snapshot-dependencies; actual813 exact. Staged21,668 nodes frozen: before future write/build/formatter/chmod required atomic COW+identity journal; old12153-path fence сохраняется.

Original8 JAR metadata device/inode/nlink/size/mode600/UIDGID/mtime/ctime/blocks exact preflight. Whole637MB originals hashes не повторены лишь ради истории: accepted independent full-member SHA/CRC и author native streamed source proof reused, подкреплены unchanged actual original metadata и critical ZIP readbacks. Все2937 newly staged content hashes действительно рассчитаны reviewer.

## Complete standard loader и provenance

Для8 original ZIP заново открыты regular pathsets, size/CRC metadata, unfolded Manifest, layers.idx/classpath.idx; original critical bytes manifest/index/JarLauncher/start-class и7 git.properties exact actual stage. All4 ordered layers canonical dependencies→spring-boot-loader→snapshot-dependencies→application. Каждая Start-Class .class присутствует; Main-Class JarLauncher, BOOT-INF/classes/lib/index headers стандартные. Это полный app CONTENT union, не partial JarLauncher classpath.

Все original indexes не перечисляют один auto-added `spring-boot-jarmode-tools-3.5.16.jar`; он тоже полностью staged. Initial reviewer predicate «index должен перечислять каждый lib» оказался неверным и исправлен по original8. Original index и все libs retained exact, missing dependency не установлена. Loader фактически не запускался.

Artifact producer остаётся `908fc8c27be82e4a0d7db7bf4d7b2c4a62225916`:7 embedded908, Renderer canonical no git.properties. Roote58 minimal2 test corrections не дают fake rebuild stamp. Fixture absence reused independently accepted unchanged current8 JAR proof: все staged members tied к source; fixture injection нет. Future changed executable/formatter inputs требуют отдельного source/artifact equivalence/rebuild решения.

## Renderer overlay/readability

Filled Renderer manifest1260 rows independently exact actual names/hash/bytes/mode; local Draft202012Validator PASS, refs/network нет. Source908/status runtime-not-run/path/image/header/future fields соблюдают schema, boolean claims подкреплены actual full tree proof.

Pinned retained image `sha256:cdb912dd9171d42e2af4c677e2adfbb2a923621be23556f731fd7193d3cfa22f`, retained safe metadata SHA `726f0aef988ac6816423deb4635afc21855c620097a063987348df629c90bff6` unchanged. Live Docker inspect не выполнен. Dockerfile/source сохраняют Temurin21-jre-jammy, LibreOffice/Poppler/fonts/wget outside/app, USERapp/WORKDIRapp/normal java JarLauncher.

Current overlay mounts complete new `current_host_stage/payload/renderer/app`→`/app` read_only=true/create_host_path=false, без USER/cwd/entrypoint/clock/engine rewrite. Base sealed9 service не имеет conflicting nested/app mounts; current root/app target заменяет прежний root/app overlay и одновременно masks stale classes/loader/libs. Future resolved Compose/mount inspection обязательна.

All mounted root/descendant dirs755/files644 статически readable/traversable для любого numeric app UID. Host OUT ancestors могут быть700: container user traverses mounted/app root, а host source lookup выполняет Docker daemon; прямой другой hostUID access этим не доказан. Actual UID, writable/tmp, mount RW=false, native tools/fonts/JRE, liveness и DOCX/PDF/PNG/ZIP conversions NOT_RUN. T003 sharedUID/hostJDK/no hard perprocess cgroup qualifications не закрыты.

## STAGE-PRE01 required next-phase prerequisite

Accepted sealed9 template `data-renderer-edge.compose.template.yml`:178/:200 bind-ит `host_runtime_prepare/runtime/frontend/{pwa,tma}-vue/dist` readonly→`/usr/share/nginx/html`, create_host_path=false. **Оба actual source dirs отсутствуют.** JAR stage intentionally не создаёт Vue copies и не заявляет их serving PASS.

Original genuine PWA13/TMA9 independently SHA/bytes unchanged; all22 files0600/owner1000 и оба dist roots0700. Сохранность не доказывает readability intended nonowner nginx worker. HTTP/native UID probe не было; actual403/UID failure не заявляется.

До complete planned9/browser phase нужны **NEW own exact22 hash/byte public copies files0644/dirs0755** и accepted bind sources, либо уже согласованное owner-serving правило. Neverchmod originals или hardlink600 thenchmod. Это narrow existing acceptance prerequisite, не новое product/transport rule. Independent backend/Renderer payload admission можно использовать отдельно после fresh floor/NP cleanup/exact grant; full9/browser ready до prerequisite не подтверждена.

## Capacity и границы

Author whole-stage final bound197558272≤201326592B; transient0/max temp12093295/buffer262144/peakRSS49770496, helper terminal. Stage already charged; retained future runtime8079735569B **не требует второй stage192MiB надбавки**. Free8395509760 — completion sample с concurrent allocations, не current floor/grant. Fresh measured resources/NP cleanup/coherent current specs и отдельные key/data/app/browser grants обязательны; Vue copy cost учитывается отдельно.

Reviewer actual checks — receipt/Root-view identity, all actual paths/modes/links/2937 hashes, critical original ZIP metadata/bytes/pure parsers, local schema, Vue22 hash/stat и source PID absence. No native app/UID/Docker/JVM/runtime proof. Prior NP/audit/export/host reviews сохранены, источники/foreign resources не изменены. Source/current-stage PASS не является whole product/client/security isolation PASS.

Created only NEW CURRENT/attempts, identity/full payload/Renderer+Vue/prerequisite/source-control/preservation metadata, REPORT и manifest/archive/seal; full inventory в manifest. Modified product/author/root/old reviews NONE, Deleted NONE. Next Root: record stage admission/STAGE-PRE01, narrow public Vue staging, then current runtime/source/resource/TLS review and explicit grants after NP cleanup/fresh floor.
