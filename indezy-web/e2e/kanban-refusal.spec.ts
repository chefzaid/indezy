import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession } from './helpers/workspace';

for (const width of [1280, 390]) {
  test(`refuses an opportunity, saying who refused it, and badges it (${width}px)`, async ({ page }) => {
    test.setTimeout(60_000);
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, 'en');
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language: 'en' } }));
    await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, skills: [] } }));
    await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/sources/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: emptyDashboardStats }));
    const card = (projectId: number, role: string, status: string, extra: Record<string, unknown> = {}) => ({
      projectId, role, status, clientName: 'Acme', totalSteps: 0, completedSteps: 0, failedSteps: 0, ...extra
    });
    let cards = [card(41, 'Java developer', 'CONTACT'),
      card(7, 'Go developer', 'LOST', { refusedBy: 'RECRUITER', lostReason: 'NO_RESPONSE' }),
      card(9, 'Data engineer', 'WON')];
    const columnOrder = ['CONTACT', 'INTERVIEW', 'OFFER', 'LOST', 'WON'];
    await page.route('**/api/projects/kanban/1*', route => route.fulfill({ json: {
      inactivityDays: 14, columnOrder,
      columns: Object.fromEntries(columnOrder.map(status => [status, cards.filter(c => c.status === status)]))
    } }));
    const changes: Record<string, unknown>[] = [];
    await page.route('**/api/projects/bulk', async route => {
      const change = route.request().postDataJSON();
      changes.push(change);
      cards = cards.map(c => change.projectIds.includes(c.projectId) ?
        { ...c, status: change.status, refusedBy: change.refusedBy, lostReason: change.lostReason } : c);
      await route.fulfill({ json: { updated: change.projectIds.length } });
    });

    await page.goto('/dashboard');
    await page.getByRole('radio', { name: /Kanban/ }).click();
    // The refused column comes before the accepted one.
    await expect(page.locator('.column-header h3')).toHaveText(['Contact', 'Tests & Interviews', 'Offer', 'Declined', 'Accepted']);
    const recruiterBadge = page.locator('[data-project-id="7"] [data-refused-by]');
    await expect(recruiterBadge).toHaveText('Declined by the recruiter');
    await expect(recruiterBadge).toHaveCSS('background-color', 'rgb(198, 40, 40)');
    await expect(page.locator('[data-project-id="7"]')).toContainText('No response');

    await page.getByRole('checkbox', { name: 'Select "Java developer"' }).check();
    await page.getByRole('toolbar').getByRole('button', { name: 'Move to' }).click();
    await page.getByRole('menuitem', { name: 'Declined' }).click();
    const dialog = page.getByRole('dialog', { name: 'Who declined this opportunity?' });
    await expect(dialog).toBeVisible();
    const confirm = dialog.getByRole('button', { name: 'Confirm' });
    await expect(confirm).toBeDisabled();
    await dialog.getByRole('radio', { name: 'Me' }).check();
    await expect(confirm).toBeEnabled();
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations).toEqual([]);
    expect(await page.evaluate(() => document.querySelector('mat-dialog-container')!.scrollWidth <= window.innerWidth)).toBe(true);
    await dialog.screenshot({ path: test.info().outputPath(`refusal-dialog-${width}.png`) });
    // The reason is optional: confirmed without one.
    await confirm.click();

    await expect.poll(() => changes).toEqual([{ projectIds: [41], action: 'STATUS', status: 'LOST', refusedBy: 'FREELANCE' }]);
    const freelanceBadge = page.locator('[data-project-id="41"] [data-refused-by]');
    await expect(freelanceBadge).toHaveText('Declined by me');
    await expect(freelanceBadge).toHaveCSS('background-color', 'rgb(245, 124, 0)');
    await page.locator('.kanban-board').screenshot({ path: test.info().outputPath(`kanban-refusal-${width}.png`) });
  });
}
