import { expect, test } from '@playwright/test';
import { wcagViolations } from './helpers/axe';
import { settleDialog } from './helpers/dialog';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  const en = language === 'en';
  test(`turns an interview's debrief into what comes next (${language}, ${width}px)`, async ({ page }) => {
    test.setTimeout(60_000);
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    const asked: unknown[] = [];
    await page.route(/\/api\/ai\/interview\/42\/debrief/, route => {
      asked.push({ url: new URL(route.request().url()).searchParams.get('language'), body: route.request().postDataJSON() });
      return route.fulfill({ json: { outcome: 'NEXT_ROUND', nextRound: en ? 'Technical interview' : 'Entretien technique',
        nextSteps: [en ? 'Send two references' : 'Envoyer deux références'], thankYouSubject: en ? 'Thank you' : 'Merci pour l’échange',
        thankYouBody: en ? 'Hello,\nthank you for your time.' : 'Bonjour,\nmerci pour ce temps.' } });
    });
    const created: unknown[] = [];
    await page.route(/\/api\/interview-steps$/, route => {
      created.push(route.request().postDataJSON());
      return route.fulfill({ status: 201, json: { id: 80, ...route.request().postDataJSON() } });
    });
    const notes: unknown[] = [];
    await page.route(/\/api\/projects\/42\/notes$/, route => {
      if (route.request().method() === 'POST') {
        notes.push(route.request().postDataJSON());
        return route.fulfill({ status: 201, json: { id: 77, projectId: 42, content: 'x', createdAt: '2026-11-25T10:00:00' } });
      }
      return route.fulfill({ json: [] });
    });

    await page.goto('/projects/42');
    await page.locator('[data-ai-debrief-open]').click();
    const dialog = page.getByRole('dialog');
    const read = dialog.locator('[data-ai-debrief-read]');
    await expect(read).toBeDisabled();
    await dialog.getByLabel(en ? 'How did it go?' : "Comment ça s'est passé ?").fill(en ? 'Good feeling, a technical interview next week.'
      : 'Bon feeling, un entretien technique la semaine prochaine.');
    await settleDialog(page);
    expect(await wcagViolations(page), 'debrief to write').toEqual([]);
    await read.click();

    await expect(dialog.locator('[data-ai-debrief-outcome="NEXT_ROUND"]')).toContainText(en ? 'Another interview is coming' : 'Un autre entretien arrive');
    await expect(dialog.locator('[data-ai-debrief-steps]')).toContainText(en ? 'Send two references' : 'Envoyer deux références');
    expect(asked).toEqual([{ url: en ? 'EN' : 'FR', body: { notes: en ? 'Good feeling, a technical interview next week.'
      : 'Bon feeling, un entretien technique la semaine prochaine.' } }]);
    expect(await wcagViolations(page), 'debrief read').toEqual([]);

    // The next round added as a step to plan, once.
    const round = dialog.locator('[data-ai-debrief-round]');
    await round.click();
    await expect(round).toBeDisabled();
    expect(created).toEqual([{ projectId: 42, title: en ? 'Technical interview' : 'Entretien technique', status: 'TO_PLAN' }]);
    // Already at Interview: no stage to move to for another round.
    await expect(dialog.locator('[data-ai-debrief-move]')).toHaveCount(0);
    await dialog.locator('[data-ai-debrief-note]').click();
    await expect.poll(() => notes.length).toBe(1);
    expect(JSON.stringify(notes[0])).toContain(en ? 'Send two references' : 'Envoyer deux références');
    // The thank-you written as the opportunity's email, to one of its contacts.
    await dialog.locator('[data-ai-debrief-write]').click();
    const email = page.getByRole('dialog').filter({ has: page.locator('form.email-form') });
    await expect(email.getByLabel(en ? 'Subject' : 'Objet')).toHaveValue(en ? 'Thank you' : 'Merci pour l’échange');
    await expect(email.getByLabel('Message', { exact: true })).toHaveValue(en ? 'Hello,\nthank you for your time.' : 'Bonjour,\nmerci pour ce temps.');
    await page.keyboard.press('Escape');
    await expect(email).toHaveCount(0);
    await dialog.getByRole('button', { name: en ? 'Close' : 'Fermer' }).click();
    await expect(dialog).toHaveCount(0);
  });
}

test('abandons an opportunity whose debrief says it was turned down', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  await page.route(/\/api\/ai\/interview\/42\/debrief/, route => route.fulfill({ json: { outcome: 'REJECTED', reason: 'PROFILE_MISMATCH', nextRound: null,
    nextSteps: [], thankYouSubject: 'Thank you', thankYouBody: 'Hello,\nthank you for your time.' } }));
  const moves: string[] = [];
  await page.route(/\/api\/projects\/42\/status/, route => {
    const params = new URL(route.request().url()).searchParams;
    moves.push(`${params.get('status')} ${params.get('lostReason')}`);
    return route.fulfill({ json: { id: 42, role: 'Java developer', status: 'LOST', refusedBy: 'RECRUITER' } });
  });

  await page.goto('/projects/42');
  await page.locator('[data-ai-debrief-open]').click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('How did it go?').fill('They went with a more senior profile.');
  await dialog.locator('[data-ai-debrief-read]').click();
  await expect(dialog.locator('[data-ai-debrief-outcome="REJECTED"]')).toContainText('Turned down');
  await expect(dialog.locator('[data-ai-debrief-reason]')).toBeVisible();
  await expect(dialog.locator('[data-ai-debrief-round]')).toHaveCount(0);
  const move = dialog.locator('[data-ai-debrief-move]');
  await move.click();
  await expect(move).toBeDisabled();
  expect(moves).toEqual(['LOST PROFILE_MISMATCH']);
  await dialog.getByRole('button', { name: 'Close' }).click();
  await expect(dialog).toHaveCount(0);
});
