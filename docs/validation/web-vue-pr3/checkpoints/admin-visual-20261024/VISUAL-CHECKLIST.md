# Независимый visual-readiness checkpoint

Source `10d8b908ae9801b22018881389f21ef5c0188583`; product baseline0421. Изначальный инвентарь основан только на source и существующих evidence. Позднее выполнен bounded packet:см. runtime qualification ниже. Runtime модель/effort независимо не измерялись. Продукт, общие документы, Git refs, fixture data и сервисы не менялись.

83 screen rows в `visual-matrix.csv`:33 canonical WEB logical variants из logical-screens.csv +50 фактических production Screen.vue (48 shared mobile-core +2 PWA auth). Shared48 переиспользуются PWA/TMA; темы и ширины не создают страницы. Канонические33 не исчерпывают дополнительные WEB schedule/notifications/admin-attendance/detail overlays: они обозначены отдельными case IDs ниже. Наличие всех83 source paths установлено; это не visual-ready83 и не functional actions83. Whole-row83 пока notchecked, поскольку нет доказательства всех требуемых состояний/ширин. Это не отменяет принятые functional117 или доказанные baseline slices.

## Конечный checklist:18 критериев

| ID | Критерий и точная граница | Статус | Evidence / остаток |
|---|---|---|---|
| INV-01 |33 WEB logical views +50 production mobile screen components перечислены по одному | ready | visual-matrix.csv; inventory-summary.json |
| REUSE-01 | HEADMAN baseline11 families,300/1440,light/dark | ready | public headman44 manifest3f585; feature/common shell/app/theme bytes unchanged10d8. Только эти baseline states; map отдельно |
| REUSE-02 | AUTH empty password1440 +OTP300,обе темы | ready | public auth4 manifestabc8; auth/common bytes unchanged10d8. QR/recovery/new-password не покрыты этими4 |
| REUSE-03 | STUDENT80 baseline +GEO label43b7 квалифицированы после delta | notchecked | feature меняет только StudentTrend label; shell assistant fence изменился.43b7 label correction accepted separately; нельзя объявить все80 current по file presence |
| REUSE-04 | TEACHER6 correctionframes уже включены вpublicgallery | ready |14 =8source341(journal/lesson)+4source210(pagination/dates/populatedstats)+2source840(drill/return). Последний teacher feature840 byte-identical10d8; сохранить эти6 corrections. Общийheader позже изменился; HOME/loading/fullwidths не покрыты, whole rows notchecked |
| REUSE-05 | Mobile12 corrective samples: PWA login/OTP300 root16/20; semester300/1440 both themes | ready |1e7d public mobile12; independent09e7/1e7 recheck. Исправленные ранее P2 не воспроизводить без новой зависимости |
| REUSE-06 | Mobile retained140 выборочно привязаны к50 source screens | ready qualified baseline inventory | mobile-consumer-reconciliation.csv:36PWA actualretainedbaseline slices/14notvisuallychecked;138PWA+2controlledunsignedTMA,140hashesmatch. Corrective12supersedeauth/semesterP2. TMApostlogin0, whole-screenallstatesnotclaimed. |
| NOTIF-01 | Два события накапливаются; исчезают поTTL, сохраняются вbell/history | ready | web-foundation/notifications-live.json source43440; accepted original actual events+STOMP. Не доказывает4events |
| NOTIF-02 | Четыре события, каждому≈10s, нет преждевременного eviction при свободном viewport | ready bounded recheck | Source77f: four actualapp toasts retained1440light; individual lifetimes10.127/10.089/10.176/9.987s; history4/bell3. Browser-only syntheticGET+focus; postfix-77f-qualified.json. Originalcap3failure retained. |
| LOAD-01 | Initial data loading STUDENT/HEADMAN/TEACHER имеет skeleton/progress, не голый текст | ready initial representatives | Source77f actualheldGET: StudentRequests425light/HeadmanJournal300light/TeacherHome1440dark skeletons viewed; percase realroutehit. Otherwidths/themes belong WIDTH/THEME, no whole-screenPASS. |
| STATE-01 | Empty/error/retry/reconnect read states ready ролей | partial | loading-only-4 Headman300light abortedGET→error→restoredGET/UIretry→populatedjournal observed. Otherroles/empty/reconnect notchecked; syntheticfailure notbackendcorrectness. |
| STATE-02 | Focus/hover/press/disabled и validation/saving/conflict/unknown при командах | notchecked | Reuse existing homework selected-hover and accepted unknown-result receipts; missing visual cases: role-specific forms at425light, keyboard modal at768dark. Не выполнять новые domain writes ради картинки |
| WIDTH-01 | Все согласованные widths300/425/600/768/1024/1280/1440/1920 на meaningful baseline layouts | notchecked | STUDENT5families имеют8×2; HEADMAN11 только300/1440×2; teacher sparse; auth sparse; mobile mostly300,selected1440. Нужен bounded breakpoint packet по4 shell/layout representatives, не528state combinations |
| THEME-01 | Общая manual/system/device policy и прежние live adapter checks | ready | Theme/device accepted reviews; mobile host-theme.json PWAOS live/manual priority; TMA controlled unsigned adapter. Signed TMA admission не заявляется |
| TMA-01 | Реальные TMA post-login screens light | notchecked |140survey содержит только2 unsigned TMAgate; не заменять signed admission fake success. Shared source/light tokens есть, TMA native runtime пока unknown |
| OVERLAY-01 | Bell/history/recovery, profile role/access gates, notification reconnect | notchecked | Existing notifications recovery uncertain PNG +85799 JSON retained; reuse after source equality. Bell preview3 ≠toast cap3, правила разные |
| MOBILE-01 | PWA update/offline/install/recovery/new-proof gates и mobile nested drawers | notchecked | Existing source owners есть; не представлены полностью140. Отдельные состояния, не новые страницы на каждую тему |
| GALLERY-01 | Новые reproduced states имеютID/screen/state/theme/width/revision/cause | partial | observed-states.html stages8 viewedactualPNG withmanifest+narrow-results-qualified.json; publicpublication notdone. Failedattempt originals retained; functionalactions added0. |

## Кандидаты findings перед браузером

- **Candidate P2, toast lifetime:** `frontends/web-vue/src/features/notifications/notifications.ts:100` после добавления новых events выполняет `.slice(-3)`, хотя каждой записи назначено `expiresAt:Date.now()+10_000`. Ожидаемое причинное воспроизведение: пустой initialized inbox → четыре fresh allowed events менее чем за2s → viewport1440×1280 способен показатьчетыре → первое исчезает сразупри4, доTTL. До runtime это source candidate, не подтверждённый actual repro и не разрешение автору менять продукт.
- **Candidate P2, loading presentation:** StudentNotice21/HeadmanPage28/TeacherNotice19 рендерят только p loading text. Initial withheld GET должен показать фактический экран, прежде чем назначатьfix. HomeworkPage113 имеет существующий skeleton; не распространять вывод на весь WEB или mobile.
- **Известное qualified observation, mobile attendance overflow:** прежний79 survey adminattendance300 scrollWidth313 обе темы; это не новое наблюдение и не доказанный regression. Оставлено notchecked; исходный reviewer не включил в блокирующие themeP2.

## Первый browser packet — только после Root lease

`timeout --signal=TERM --kill-after=10s 180s node /workspace/rct-pr3-runtime/visual-readiness-20261024/capture-first.cjs`

Ожидаемо60–100s, максимум180s; один Chromium, два sequential contexts,8PNG +capture-first-receipt.json, безtrace/video/network bodies. Нормальная configured auth synthetic student→HEADMAN и teacher; ownlogout вfinally, закрытие contexts/browser. Main WEB14210/API18080; ни isolated7/adminQ8/user8, ни realm changes. Любой nonGET domain request вне/api/auth/ прерывается и регистрируется как unexpected, без fake server ACK.

1. **N4:**1440×1280/light; synthetic GET inbox+prefs only, frontend `load(true)` явно synthetic live-refresh trigger, неSTOMP. Послеevent3/event4 → PNG +arrival timestamps/visible toast IDs/bounds; через10.5s TTL frame; bell/history frame. Проверить раннее вытеснение и независимые expiry, удержание history. Никакого создания публикаций/уведомлений на сервере.
2. **H-LER:**300×900/light; HEADMAN journal lessonsGET удержан →loading frame; затем abortread →error frame; normalGET restored +UIretry →recovered frame. Ни mark/export/create, ни credential dialogs.
3. **T-L:**1440×1080/dark; TEACHER home semesterGET удержан →initial loading frame; GET отпустить. Отдельный safe currentHOME frame возможен в следующем packet, не добавлять автоматически.

Synthetic ответы допустимы только для visual state; API/backend correctness не заявляется. Source bytesguard pin10d8 разрешает docs-only HEADadvance и останавливает при frontend changes. Preparation syntaxcheck exit0. После RootGO выполнены3 attempts; ни toast/skeleton criterion не подтверждён:см.ATTEMPTS.md/attempt3-qualified.json. Устаревшие parent priorities/questions не используются: QPROD004/QENV003/007/008 уже owner-approved; админ/Q3 security работы остаются независимыми.


## Runtime qualification после RootGO

Третий nativeattempt exit1 за25.8s, source10d8 unchanged. ЧетыреPNG сохранены как diagnostics: UI показал normalhistory/bell, но все4 syntheticarrival наблюдения имели empty renderedToasts.3/4PNG identicalSHA, syntheticitems не видны. Поэтому NOTIF-02,LOAD-01,STATE-01 остаютсяnotchecked; **toastcap/skeleton fixes по этому запуску не подтверждены**. TTLнельзяоценивать безtoast. Никаких functionalactions/APIcorrectness PASS из этихкадров.

Подтверждённый harnessfailure: перед переключениемвHEADMAN helper сначала ставитwidth300, где web.pcss:489 скрываетweb-header-role, затем ждётневидимуюrolebutton. Productнеисправлялся. Store-owner/syntheticGET-rootcause остаётсяunknown; текущийинвентарь нельзярасширять доready.

Ownnormalstudentlogout HTTP204+localguestphase подтверждены; guest401 не запрашивался. Context/browserclosed; processclosure показываеттолько4PPID1zombies,0livebrowser. Leaseосвобождён. Исходныеattempt1/2/3scripts/receipts иPNG/hash сохранены; четвёртаяпопытка не запускалась.


## Narrow changed-hypothesis results after combined failures

Loading-only4: actualHeadman300light read loading/error/retry andTeacher1440dark initialloading rendered,4PNG personallyviewed. Bothbareloading defects confirmed within theseviews only. Headmanrestoredread recoverspopulatedjournal. NativeUIlogout204×2/loginvisible; context/browserclosed.

Toast5 faileduniqueemptytextlocator aftersuccessfulactualmockbootstrap; toast6 provednativefocusbringToFront doesnottriggerapprefresh inheadless. Bothoriginalsretained,0PNG,ownlogout204. Toast7 changedtosyntheticfocusdispatch (trustedfalse), actualappstore/parser/UI nowrendersfourarrivals; firsttoast removedatfourthafter≈2.2s becausecap3 despite10sTTL andampleviewport. Fouractualhistorycardsremain. Last10.548s framefromfirststillhas3/4toast duearrivaltiming, notallTTLexpired. All4PNG personallyviewed. Ownlogout7 UNCONFIRMED afteropenbellpopover/UIprofiletimeout; context/browserclosed/live0, exactsessionhandle unavailable, nofreshlogin/revokeall. See toast7-cleanup-qualified.json.

Eightnewframes areobservations, not8functionalactions orreadinesspercentage.83canonicalrows/18criteria unchanged; nofullrowreadyadded. No productsource orrepositorydocs changed. Gallery: observed-states.html, criterionqualification:narrow-results-qualified.json, solewriterhandoff:WRITER-PACKET.md. RequestedSol6.1/high; actualruntimetelemetryunavailable.

## Postfix77f changed-hypothesis closure

See POSTFIX-77f-REPORT.md/postfix-77f-manifest.json: sevenoriginalafterframes, checkedhashes, exactsourcefilehashes, syntheticcauses, cleanup. Notification4retain4/eachown≈10s/fullhistory4/bell3 andall3roleinitialskeletonrepresentatives confirmed. Firstpostfixrawexit1 remains: HeadmancaptureguardranbeforetargetlessonGEThit; successfulToast/Student slicesqualified separately. RemainingH/Trechecktarget-hitwait passes. All4ownpostfixsessions native204+guest401, context/browserclosed; oldtoast7cleanupFAILretained. No functionalactioncount oroverallready% added.

Mobileconsumerreconciliation isseparatefromwhole-row83status:36qualifiedretainedrealPWA baseline slices/14notvisuallychecked; actualTMApostloginunknown. NEXT-mobile-packet.md selects12missingTodaybreakpointframes+16notificationconsumerbaselineframes+max2actualTMAentry, notall50×allstates. No newbrowser untilRootresourceGO.
