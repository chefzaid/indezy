import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`dictates a note on an opportunity's journal (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    // The browser's speech recognition, replaced: it hears one sentence when asked to start.
    await page.addInitScript(() => {
      class FakeRecognition {
        lang = '';
        continuous = false;
        interimResults = false;
        onresult: ((event: unknown) => void) | null = null;
        onerror: ((event: unknown) => void) | null = null;
        onend: (() => void) | null = null;
        start(): void {
          (window as unknown as { heard: string }).heard = this.lang;
          setTimeout(() => this.onresult?.({ resultIndex: 0, results: [{ isFinal: true, 0: { transcript: 'Rappeler Léa lundi' } }] }), 50);
        }
        stop(): void {
          this.onend?.();
        }
      }
      const scope = window as unknown as { SpeechRecognition: unknown; webkitSpeechRecognition: unknown };
      scope.SpeechRecognition = FakeRecognition;
      scope.webkitSpeechRecognition = FakeRecognition;
    });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);

    await page.goto('/projects/42');
    const dictate = page.locator('[data-dictate]');
    await dictate.click();
    await expect(page.locator('app-project-journal .journal-input textarea')).toHaveValue('Rappeler Léa lundi');
    expect(await page.evaluate(() => (window as unknown as { heard: string }).heard)).toBe(en ? 'en-US' : 'fr-FR');
    await expect(dictate).toHaveAttribute('aria-pressed', 'true');
    const audit = await new AxeBuilder({ page }).include('app-project-journal').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await dictate.click();
    await expect(dictate).toHaveAttribute('aria-pressed', 'false');
  });
}
