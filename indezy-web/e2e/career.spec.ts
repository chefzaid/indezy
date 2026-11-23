import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

for (const language of ['en', 'fr']) {
  for (const width of [1280, 390]) {
    test(`keeps the past positions and schools on the profile (${language}, ${width}px)`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await mockWorkspaceSession(page, language);
      await mockWorkspaceData(page, language);
      const experiences: Record<string, unknown>[] = [{ id: 1, employer: 'Banque Exemple', title: 'Développeuse Java',
        startsOn: '2015-09-01', endsOn: '2019-06-01', stack: 'Java 8', shownPublicly: true }];
      const posted: Record<string, unknown>[] = [];
      await page.route(/\/api\/users\/experiences$/, route => {
        if (route.request().method() === 'POST') {
          const created = { id: 2, ...route.request().postDataJSON() };
          posted.push(route.request().postDataJSON());
          experiences.unshift(created);
          return route.fulfill({ status: 201, json: created });
        }
        return route.fulfill({ json: experiences });
      });
      await page.route(/\/api\/users\/educations$/, route => route.fulfill({ json: [
        { id: 3, school: 'INSA Lyon', degree: 'Ingénieure', startYear: 2010, endYear: 2015, shownPublicly: false }] }));

      await page.goto('/profile');
      const section = page.locator('[data-career]');
      await expect(section.locator('[data-experience="1"]')).toContainText('Développeuse Java · Banque Exemple');
      await expect(section.locator('[data-education="3"]')).toContainText('Ingénieure · INSA Lyon');
      const audit = await new AxeBuilder({ page }).include('[data-career]').withTags(WCAG).analyze();
      expect(audit.violations.map(violation => violation.id)).toEqual([]);

      await section.locator('[data-add-experience]').click();
      const dialog = page.getByRole('dialog');
      // The dialog focuses its first field once its form is bound: filling before that would be overwritten.
      await expect(dialog.locator('[data-experience-title]')).toBeFocused();
      await dialog.locator('[data-experience-title]').fill('Lead Java');
      await dialog.locator('[data-experience-employer]').fill('Alice Conseil');
      await dialog.locator('[data-experience-start]').fill('2024-01');
      await expect(dialog.locator('[data-experience-start]')).toHaveValue('2024-01');
      await expect(dialog.locator('[data-experience-employer]')).toHaveValue('Alice Conseil');
      const dialogAudit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(WCAG).analyze();
      expect(dialogAudit.violations.map(violation => violation.id)).toEqual([]);
      await dialog.locator('[data-experience-save]').click();
      await expect(dialog).toBeHidden();
      await expect.poll(() => posted.length).toBe(1);
      expect(posted).toEqual([{ title: 'Lead Java', employer: 'Alice Conseil', startsOn: '2024-01-01', endsOn: null, description: null,
        stack: null, shownPublicly: false }]);
      await expect(section.locator('[data-experience="2"]')).toContainText(language === 'en' ? 'now' : "aujourd'hui");
    });
  }
}
