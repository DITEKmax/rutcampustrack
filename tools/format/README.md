# Reproducible author-source formatting

From the repository root, `node scripts/format-authored.mjs inventory` emits
the tracked inventory with a formatter or an explicit exclusion reason per file.
The runner accepts `prettier`, `java`, `python`, `sql`, or `all`, followed by
`--check` (default) or `--write`. A check never rewrites files. Formatting is a
separate operation from semantic changes and requires a stable assigned checkout.

Install frontend tools with `npm ci --prefix frontends`. Prettier is pinned in
the mobile workspace lockfile; `npm run format:check --prefix frontends` and
`npm run format --prefix frontends` use the same curated inventory. Existing
ESLint rules remain enabled. PCSS uses the CSS parser, preserving PostCSS mixins,
units and tokens. Vue template whitespace is treated strictly; Angular keeps
its semicolon convention through an override.

Java uses `./gradlew formatJavaCheck` / `formatJava`, with Spotless 7.0.4 and
google-java-format 1.25.2 AOSP (four-space indentation), on the existing JDK 21.
These opt-in tasks do not silently add a failing gate before the initial
mechanical formatting pass has been integrated.

Install Python tooling with `python3 -m pip install -r tools/format/requirements.txt`
in the assigned environment. Ruff 0.11.13 discovers the existing bot pyproject
or root formatting defaults (Python 3.12, line length 120). The runner adds no
new global lint rules. The existing bot `ruff check .` gate remains separate.

Eligible SQL uses pgFormatter 5.6 (`pg_format --spaces 4`), installed explicitly
from its pinned release. Versioned Flyway SQL is excluded and its bytes are
checked before/after formatter execution. This tooling never calls Flyway
repair/clean. Fixtures, snapshots, source archives, generated contracts and
machine outputs are excluded; revise an exclusion only with its byte/syntax
owner. `frontends/landing/dist` is a deliberate canonical-source exception to
the usual generated-dist exclusion.

After a mechanical pass, run the formatter check, existing lint and relevant
parser/type/build checks. A formatter's successful parse is not a proof of
unchanged runtime behavior, API contracts or historical migration compatibility.
