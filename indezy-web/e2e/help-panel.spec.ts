import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';
import { settleDialog } from './helpers/dialog';

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`opens the help of the page in use beside it (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);

    await page.goto('/offers');
    await page.locator('[data-help-open]').click();
    const panel = page.getByRole('dialog');
    await expect(panel.locator('[data-help-panel] [data-markdown]')).toContainText(en ? 'Read the feed' : 'Lire le fil');
    await settleDialog(page);
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await panel.locator('[data-help-panel-full]').click();
    await expect(page).toHaveURL(/\/help\/offers$/);
    await expect(panel).toHaveCount(0);
  });
}
