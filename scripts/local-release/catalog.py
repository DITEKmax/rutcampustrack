"""Fixed complete local CI replay with explicit browser selections."""
from dataclasses import dataclass

@dataclass(frozen=True)
class Gate:
    name: str
    command: tuple
    requires: tuple=()
    seconds: int=1800
    resource: bool=False
    reports: tuple=()

MODULES=(('api-gateway',':services:api-gateway'),('auth-service',':services:auth-service:auth-app'),
         ('academic-service',':services:academic-service:academic-app'),('schedule-service',':services:schedule-service:schedule-app'),
         ('attendance-service',':services:attendance-service:attendance-app'),('notification-service',':services:notification-service:notification-app'),
         ('mobile-bff',':services:mobile-bff:mobile-bff-app'))

def catalog():
    gates=[Gate('setup',('bash','scripts/local-release/setup.sh'),seconds=1800),
           Gate('source-public-object-prepare',('python3','scripts/local-release/source-public-object-prepare.py'),('setup',),seconds=120),
           Gate('java-verification',('bash','scripts/local-release/java-verification.sh'),('setup','source-public-object-prepare'),reports=('build/ci-test-outcomes',)),
           Gate('bot',('bash','scripts/local-release/bot.sh'),('setup',),reports=('services/notification-bot/coverage.xml','services/notification-bot/junit.xml')),
           Gate('vue',('bash','scripts/local-release/vue.sh'),('setup',),reports=('frontends/coverage','frontends/test-results/local-release.xml')),
           Gate('scripts',('bash','scripts/local-release/scripts.sh'),('setup',)),
           Gate('security-public-object-prepare',('bash','scripts/local-release/security-public-object-prepare.sh'),('setup',),seconds=1800),
           Gate('gitleaks',('bash','scripts/local-release/gitleaks.sh'),('setup','security-public-object-prepare'),seconds=1800),
           Gate('trivy',('bash','scripts/local-release/trivy.sh'),('setup',),seconds=1800)]
    for service,module in MODULES:
        root=module[1:].replace(':','/')
        gates.append(Gate('java-'+service,('bash','scripts/local-release/java.sh',service,module),('setup','source-public-object-prepare'),seconds=3600,resource=True,
                          reports=(root+'/build/test-results',root+'/build/reports/jacoco',root+'/build/reports/tests',root+'/build/reports/integrationTest','build/ci-test-outcomes/'+service)))
    gates += [Gate('files-recovery',('bash','scripts/local-release/files-recovery.sh'),('setup',),resource=True,seconds=1200),
              Gate('monitoring',('bash','scripts/local-release/monitoring.sh'),('setup','bot'),resource=True,seconds=1500,reports=('build/monitoring',)),
              Gate('observability-apps',('bash','scripts/local-release/apps.sh'),('setup','vue','monitoring'),resource=True,seconds=1080,reports=('build/monitoring/apps.json',))]
    for mode in ('controls','full','client','webkit','lesson-lifecycle','ui-batch','special13','homework-sensitivity'):
        gates.append(Gate('browser-'+mode,('python3','scripts/local-release/browser-replay.py',mode),('setup','vue'),resource=True,seconds=7200))
    gates.append(Gate('native-group-and-nginx',('python3','scripts/local-release/native-acceptance.py'),('setup','vue')+tuple('java-'+s for s,_ in MODULES),resource=True,seconds=2400))
    return tuple(gates)

NOT_APPLICABLE={'diff-cover':'pull_request-only diagnostic comparison; this is LOCAL_CONTROL',
                'remote-image-scan':'schedule-only scan of remote GHCR images; no local dispatch',
                'external-provider':'no live Telegram/WebPush/provider accounts in the synthetic local realm'}
