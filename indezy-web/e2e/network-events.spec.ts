import AxeBuilder from '@axe-core/playwright';
import { wcagViolations } from './helpers/axe';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

const organizer = { handle: 'ines_b', firstName: 'Inès', lastName: 'Bernard', avatar: null, headline: null, stack: [], city: null,
  remote: false, availability: null, availabilityDate: null, languages: [], verified: false };
const meetup = (id: number, title: string, changes: Record<string, unknown> = {}) => ({ id, title, description: 'On parle Kafka en production.',
  startsAt: '2026-12-03T18:30:00', endsAt: '2026-12-03T21:00:00', online: false, place: 'Le Comptoir, Lyon', link: null, tags: ['kafka'],
  organizer, going: 3, interested: 1, mine: null, own: false, editable: false, cancelled: false, ...changes });

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`answers an event, opens it and adds it to the calendar, then organizes one (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    await page.route('**/api/network/events?**', route => route.fulfill({ json: { meetups: [meetup(4, 'Apéro Kafka'),
      meetup(5, 'Webinar Flink', { online: true, place: null, link: 'https://meet.example.org/flink', startsAt: '2026-12-08T12:00:00', endsAt: null })],
      page: 0, more: false } }));
    const answers: string[] = [];
    await page.route('**/api/network/events/4/attendance**', route => {
      answers.push(new URL(route.request().url()).searchParams.get('status') ?? route.request().method());
      return route.fulfill({ json: meetup(4, 'Apéro Kafka', { mine: 'GOING', going: 4 }) });
    });
    await page.route('**/api/network/events/4', route => route.fulfill({ json: meetup(4, 'Apéro Kafka', { mine: 'GOING', going: 4 }) }));
    await page.route('**/api/network/events/4/attendees', route => route.fulfill({ json: { going: [organizer], interested: [] } }));
    await page.route('**/api/network/events/4/ics', route => route.fulfill({ status: 200, contentType: 'text/calendar',
      body: 'BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n' }));
    const organized: Record<string, unknown>[] = [];
    await page.route('**/api/network/events', route => {
      organized.push(route.request().postDataJSON());
      return route.fulfill({ status: 201, json: meetup(9, 'Atelier Spark', { own: true, editable: true, mine: 'GOING' }) });
    });
    await page.route('**/api/network/events/9', route => route.fulfill({ json: meetup(9, 'Atelier Spark', { own: true, editable: true, mine: 'GOING' }) }));
    await page.route('**/api/network/events/9/attendees', route => route.fulfill({ json: { going: [], interested: [] } }));

    await page.goto('/network/events');
    await expect(page.locator('[data-event]')).toHaveCount(2);
    await expect(page.locator('[data-event="5"]')).toContainText(en ? 'Online' : 'En ligne');
    await page.locator('[data-event="4"] [data-event-going-button]').click();
    await expect(page.locator('[data-event="4"] [data-event-going-button]')).toHaveAttribute('aria-pressed', 'true');
    expect(answers).toEqual(['GOING']);
    const audit = await new AxeBuilder({ page }).include('main').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);

    await page.locator('[data-event="4"] h3 a').click();
    await expect(page).toHaveURL(/\/network\/events\/4$/);
    await expect(page.locator('[data-event-about]')).toContainText('On parle Kafka');
    await expect(page.locator('[data-event-attendees]')).toContainText('Inès Bernard');
    expect(await wcagViolations(page), 'event page').toEqual([]);
    const download = page.waitForEvent('download');
    await page.locator('[data-event-ics]').click();
    expect((await download).suggestedFilename()).toBe('indezy-event-4.ics');

    await page.goto('/network/events');
    await page.locator('[data-event-new]').click();
    await page.locator('[data-event-title]').fill('Atelier Spark');
    await page.locator('[data-event-starts]').fill('2026-12-10T18:00');
    await page.locator('[data-event-place]').fill('La Cordée, Lyon');
    await page.locator('[data-event-tags]').fill('#spark lyon');
    const form = await new AxeBuilder({ page }).include('[data-event-form]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(form.violations.map(violation => violation.id)).toEqual([]);
    await page.locator('[data-event-save]').click();
    await expect(page).toHaveURL(/\/network\/events\/9$/);
    expect(organized).toEqual([{ title: 'Atelier Spark', description: null, startsAt: '2026-12-10T18:00', endsAt: null, online: false,
      place: 'La Cordée, Lyon', link: null, tags: ['spark', 'lyon'] }]);
    await expect(page.locator('[data-event-edit]')).toBeVisible();
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(0);
  });
}
