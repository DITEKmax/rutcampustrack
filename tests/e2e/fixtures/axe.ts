import { Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * M08 Группа 5 (P2-8/5) + M07 G10 baseline — axe-core a11y audit
 * per test spec.
 *
 * Threshold: **zero CRITICAL + SERIOUS** violations (NEW-27 / QC2 /
 * NEW-110 baseline установлена в M07). MODERATE/MINOR не блокируют
 * CI — фиксируются в `docs/a11y-checklist.md` для следующего pass'а.
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
    const report = blocking.map((v) => ({
      rule: v.id,
      impact: v.impact,
      description: v.description,
      nodes: v.nodes.length,
      helpUrl: v.help,
    }));
    throw new Error(
      `axe-core found ${blocking.length} CRITICAL/SERIOUS violations:\n` +
        JSON.stringify(report, null, 2)
    );
  }
}
