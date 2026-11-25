import AxeBuilder from '@axe-core/playwright';
import { Page } from '@playwright/test';

/**
 * The WCAG 2.1 A and AA rules the page breaks, one line each with where; empty when it breaks none. A notification
 * showing is left out: Material keeps a snackbar's content hidden from screen readers a moment before it announces it.
 */
export async function wcagViolations(page: Page): Promise<string[]> {
  const results = await new AxeBuilder({ page }).exclude('mat-snack-bar-container').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  return results.violations.flatMap(violation => violation.nodes.map(node => `${violation.id} (${violation.impact}): ${node.target.join(' ')}`));
}
