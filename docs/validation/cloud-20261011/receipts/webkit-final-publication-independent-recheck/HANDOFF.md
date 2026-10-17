APPROVE_SOURCE — surgical final-publication recheck

T005-GRANT-USE-TIME [P2] закрыта на уровне SOURCE. Blocking findings:0 в исходном coherent package5de с последующими исправлениями7dfe и окончательным source5de233. Это узкая recheck только последнего native PASS publication interval; broad audit и native/runtime проверки не повторялись. Старые CHANGES_REQUESTED24fec/734f, source5de/7dfe и их evidence сохраняются immutable.

Frozen accepted execution source:

- /workspace/scratch/rct-validation/browser_webkit_native_publication_correction/manifest.json SHA256 5de233d48c60770701384c5464df6a58d4bb5210632ac3146ec7afdffff46e9f.
- publication-correction.source.safe.zip SHA256 fab03bc73dd779ecb883a7915d43e9e719ea1726a0aa2777139b6f3be673dd9f;31listed files+manifest/32members. Independent byte/hash/size/mode/member/CRC checks PASS, extraction ZIP не выполнялась.

Actual corrected fragment validate-native.py120–122:

```python
final_proof = {..., 'resources': driver.resources(), ...}
gates.require_fresh(stop)
out.save('native-validation.safe.json', final_proof)
```

Все потенциально задерживающие final payload/resource expressions вычисляются до последней fresh gate. После её current actor/resource/deadline check save получает prebuilt dict; post-clock driver.resources calls отсутствуют. Исходный stop_utc не изменяется/не продлевается. Whole native caller AST совпадает с7dfe после обратного преобразования только этих трёх statements; final_proof payload AST exact прежнему save argument. Остальные Python helpers/first-mkdir callbacks/exec gate byte exact7dfe, поэтому три ранее SOURCE-closed points сохраняются.

Независимо выполнены четыре meaningful selected FULL expression controls, включая actual require_fresh FunctionDef, без target module import и с полностью mocked actor/resource/clock/output:

1. Old gate+FULL save-expression: gate проходит при stop−1; post-gate resources задерживает mock clock2s; old mock PASS записан при stop+1. Исходный контрпример воспроизведён.
2. New FULL assignment+gate+save: resources задерживает clock2s до gate; gate отклоняет expiry; mock SAVE отсутствует.
3. New fresh positive: snapshot и gate завершаются до stop; единственный mock SAVE до stop.
4. New already-expired: gate отклоняет; mock SAVE отсутствует.

43 synthetic success-case metadata нужны только для evaluation ordering; это не actual39loader+4dlopen native evidence. Реальных proof files/aliases/clock changes/children/exec/extraction/loader/readelf/dlopen/browser не было. Author fullpublication4controls прочитаны; author134total112+18+4 claims сохранены как pure-source controls, не native PASS. Reviewer не запускал авторские target modules/control suites.

Независимо проверено сохранение original5de25 source files,7dfe28 source files и734f5reviewfiles с исходными hashes/sizes/modes. Source/helper/phase-seal/grant/actor/context/pinnedFD/selection/resources/probe boundaries не расширены. Partial/error cleanup receipts после stop и обычный неатомарный scheduler interval finalcomparison→kernelcall остаются явно квалифицированными; correction устраняет дополнительный задерживающий resource preflight после finalclock, а не обещает OS-level atomic fencing/malicioussameUID isolation/hardcaps.

Root получает одно консолидированное SOURCE approval цепочки5de→7dfe→5de233. Фактические entrypoints должны быть только NEW publication-correction sources иNEW manifest, exact independent review и отдельные новые extraction-only/native-only/browser-only Root grants. Старые grant/path/seals не используются как разрешение запуска. Before actual phase Root повторно проверяет ресурсы/leases/freeze/seals и условия ранее принятого whole-package review; browser всё ещё отдельная граница. Никакой grant данным review не выдан; actual extraction/native ABI/dlopen/browser/GUI/resources/capacity/acceptance NOT_RUN/UNKNOWN.

FULL current TASK-CONTRACT/CURRENT/decisions/questions/resources восстановлены. Root4c/currentprepared5ced и allstand/browserleasesreleased — сохранённые task facts, не новый runtime receipt. Root sole shared-doc writer, host sole source author; selectedSol6.1/high actualUNKNOWN. Angular/futureweb/product/server/cache/keys/data/volumes и private grant/ENV/body не читались/не менялись. No source fixes, broad provenance/native repeats, network/API/Docker/Gradle/JVM/Node/browser operations.

Created только NEW native_publication_recheck: check-publication.py, publication-recheck.safe.json, HANDOFF.md, CURRENT.json, manifest.json, safe-evidence.zip. Modified/deleted старые/чужие/Root files:0. Собственный helper terminal; active process/heavy lease NONE. Следующий шаг принадлежит Root: fresh bounded actual extraction grant, затем отдельный actual native validation grant; не объявлять WebKit availability/client PASS до этих evidence.
