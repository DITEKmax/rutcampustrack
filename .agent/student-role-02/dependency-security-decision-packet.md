# Dependency remediation decision — staged, waiting consultant slot

07.09.2026. S3. Fresh Sol xhigh readonly source/architecture consultation authorized by owner. Attempted spawn dependency_security_decision failed thread limit; no consultant running. Root works alongside two independent domain writers; don't disturb them.

## Goal
Choose smallest coherent dependency repair for the scanned student backend, closing reproducible HIGH/CRITICAL gate without unrelated major-platform migration.
## Context/evidence
security/trivy-homework-backend-rootfs.json:256package records82instances51unique(8critical43high), exit1; backend-findings-unique.json. Stable repair Academic/BFFJAR hashes in homework-repair/evidence.md and security/backend-artifacts. Initial fs scan detected0Java and is not PASS. Root reopened rootbuild/catalog/sharedBOM/BFF/Academic build files; currentBoot3.4.1 repeatedBOMs/grpc1.63/protobuf3.25.1runtime.
## Relevant scope
Read build.gradle.kts,gradle/libs.versions.toml,services/shared/* build files,app/contract build files and security relevant runtime configs. Future sole config writer needs independent worktree, frozenbaseline; current Homework/request writers do not own configs. No files/children from consultant.
## Required decision
Compare narrowBoot3.5.16+coherent patch overrides versus broaderBoot4 migration. Prefer smallest sufficient repair and evidence-based reachability dispositions, never ignore merely to green. Exact released versions must resolve Maven and rescan; compatibility net.devh3.1.0/Springdoc2.8.6/protoc/runtime must be considered. No exploitability assertion merely from version. Define bounded writer paths and genuine regression checks, noteEOL.
## Constraints
No major API/auth/business rewrite under dependency fix. No masks/ignore-unfixed/broadignore; any disposition evidence based separately reviewed. Entire sharedBOM/build declarations coherent; avoid hiddenversiondrift. No securitysource changes to silence scanners.
## Existing patterns / authoritative sources opened by root
Boot3.5.16 lastOSSreleaseJune25 https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/ . BOM https://raw.githubusercontent.com/spring-projects/spring-boot/v3.5.16/spring-boot-project/spring-boot-dependencies/build.gradle hasNetty4.1.135 (need4.1.136 perreport),AMQP5.25 (need5.33.1),PG42.7.11(need42.7.12),HATEOAS2.5.3,DataBOM2025.0.13. Someversionsprojectproperties. Tomcat https://tomcat.apache.org/security-10 states10.1.58vote failed, realrelease10.1.59 includesfixes; do not assume scannerFixedVersion ispublished. gRPC https://github.com/grpc/grpc-java/releases/tag/v1.75.0 confirmsMadeYouResetfix. Boot4 would broadenJackson/servletbaseline. Verify primary sources/currentpublishedversions.
## Acceptance criteria
Decisive minimal contract with versioncandidates/references, affected modules/configscope, required tests/scan and exclusions; no unbounded redesign/no fakePASS. Acknowledge supported-lifecycle constraint separately from presentCVEgate.
## Verification
Readonly consultation runtimeN/A; later build/compile/authHTTP/gRPC/proto/OpenAPI/DBtests plus rootfsscan coverageactualpackagecount and independentreview. No fullsuite repeat withoutchange rationale.
## Do not
No writes/spawn/deploy/secrets/otherrolefeature/UI. Do not stop runningleafs or folddependencyupdates intoHomework/date orrequestscope.

Additional root original: services/api-gateway/build.gradle.kts:55 pins Spring Cloud2024.0.0. Any Boot3.5 choice must align compatible SpringCloud BOM (verify official matrix) and actualgatewaystarter; gateway compilation/runtime/authrouting is affected, cannot validate onlyBFFJARs.

## Additional primary-source evidence, 07.09.2026

Root grouped scanner findings into 19 package coordinates in `security/backend-package-groups.json`; original scanner JSON remains authoritative. Root opened gRPC v1.82.4 release and its exact version catalog: https://github.com/grpc/grpc-java/releases/tag/v1.82.4 and https://raw.githubusercontent.com/grpc/grpc-java/v1.82.4/gradle/libs.versions.toml . Catalog still declares Netty 4.1.133.Final, tcnative 2.0.75 and protobuf 3.25.8; therefore upgrading shaded gRPC alone may retain vulnerable embedded Netty. This is a candidate concern to verify on resolved artifacts, not an exploitability assertion or selected fix. Compare shaded coverage with coherent unshaded transport only if compatibility is established.

Netty official release list https://netty.io/news lists 4.1.137.Final (6 August) after 4.1.136.Final (9 July); exact release page https://netty.io/news/2026/08/06/4-1-137-Final.html . Jackson 2.21.4 release notes https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21.4 include additional validator/stream fixes; verify Boot BOM and actual findings before selecting overrides. No version decision or dependency writer has been authorized by this evidence note. Prior consultation was paused before opening critical originals; fresh consultation must independently open those originals.

Root additionally opened exact Boot v3.5.16 gradle.properties: https://raw.githubusercontent.com/spring-projects/spring-boot/v3.5.16/gradle.properties . It already declares Jackson 2.21.4, Framework 6.2.19, Tomcat 10.1.55. Thus do not assume a separate Jackson override is required. Official Cloud compatibility https://spring.io/projects/spring-cloud/ maps Boot 3.5.x to Cloud 2025.0.x. Release notes https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2025.0-Release-Notes list 2025.0.3 (11 June 2026) and Gateway 4.3.5; they document new starter names and property prefixes plus changed trusted-proxy handling. Inspect actual gateway configuration and route/auth tests before adopting. These are consultation inputs, not selected versions.

Gateway original read confirms deprecated starter in services/api-gateway/build.gradle.kts and old spring.cloud.gateway.globalcors/routes prefixes in application.yml plus application-prod.yml. A Cloud upgrade needs bounded property migration with route-count/target, CORS and rate-limit runtime checks; changing BOM only risks lost routes. Existing test scopes: gateway/security, gateway/ratelimit, JwtAuthenticationFilterTest and PwaVersionPolicyFilterTest. Preserve all route values and auth policies; no blanket trusted-proxy rule.

Resume dispatch attempt `dependency_security_decision_resume` (Sol xhigh readonly) was rejected with `agent thread limit reached` while Homework recheck and request repair were running. No consultant started, no model substituted. Queue after a slot is actually freed; do not interrupt useful work.

07.09.2026 ROOT DECISION after completed consultation: `dependency-security-decision-result.md` preserves the final report unchanged. Accept bounded Boot3.5.16/Cloud2025.0.3 coherent repair direction and listed overrides/BOMs/unshaded transport as a candidate implementation contract; Maven resolution, net.devh compatibility and actual artifact scans are mandatory acceptance gates, not assumed PASS. Root verified affected main build/config paths have no owner diff. Future writer uses an isolated checkout with explicit manifest after source baseline freeze; no writer launched yet. Boot4 lifecycle migration is not added to current task. Newly identified HIGH XFF rate-limit trust defect is separate in `gateway-client-ip-finding.md`, with ingress topology/trust contract still pending. Do not conflate closing dependency CVEs with closing that authorization/rate-limit defect or full student role.
