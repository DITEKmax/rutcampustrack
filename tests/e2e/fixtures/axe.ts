import { Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * M08 Группа 5 (P2-8/5) + M07 G10 baseline — axe-core a11y audit
 * per test spec.
 *
 * Threshold: **zero CRITICAL + SERIOUS** violations (NEW-27 / QC2 /
 * NEW-110 baseline установлена в M07). MODERATE/MINOR не блокируют
 * CI — фиксируются в `docs/product/a11y-checklist.md` для следующего pass'а.
 *
 * Scope per call site: page.locator('main') — избегаем axe-scan
 * на Angular router-outlet'ы, которые ведут себя странно (false
 * positives на aria-hidden элементах).
 */
export async function assertNoA11yCriticalOrSerious(page: Page, scope = 'main'): Promise<void> {
  const results = await new AxeBuilder({ page })
    .include(scope)
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();

  const blocking = results.violations.filter(
    (v) => v.impact === 'critical' || v.impact === 'serious'
  );

  if (blocking.length > 0) {
    // M13 G25.24 — расширенный report: target HTML, failureSummary, html snippet.
    // Раньше было только {rule,impact,nodes:count,helpUrl} — невозможно понять
    // какой именно элемент нарушает контраст. Теперь видно exact selector +
    // computed colors из axe-core deep info.
    const report = blocking.map((v) => ({
      rule: v.id,
      impact: v.impact,
      description: v.description,
      helpUrl: v.help,
      affectedNodes: v.nodes.map((n) => ({
        target: n.target,
        html: n.html?.slice(0, 200),
        failureSummary: n.failureSummary,
      })),
    }));
    throw new Error(
      `axe-core found ${blocking.length} CRITICAL/SERIOUS violations:\n` +
        JSON.stringify(report, null, 2)
    );
  }
}
