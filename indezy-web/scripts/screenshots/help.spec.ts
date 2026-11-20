import { Page, test } from '@playwright/test';
import { mockDemoWorkspace } from './demo-workspace';

// Writes the help center's screenshots into src/assets/help/screens, from the demo workspace (invented people, clients
// and offers): npx playwright test -c pw.screenshots.config.ts help
const OUT = 'src/assets/help/screens';

async function settle(page: Page): Promise<void> {
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(900);
}

const member = (handle: string, firstName: string, lastName: string, headline: string, stack: string[], city: string) => ({ handle, firstName,
  lastName, avatar: null, headline, stack, city, remote: false, availability: null, availabilityDate: null, languages: [], verified: false });
const leo = member('leo_m', 'Léo', 'Martin', 'Data engineer freelance', ['Kafka', 'Spark'], 'Nantes');
const ines = member('ines_b', 'Inès', 'Bernard', 'Architecte cloud', ['Azure', 'Kubernetes'], 'Lyon');
const me = member('camille_l', 'Camille', 'Laurent', 'Lead développeuse Java', ['Java', 'Kafka', 'Angular'], 'Paris');

/** The member network of the demo: Camille's card, two members, their posts and an event. */
async function mockDemoNetwork(page: Page, language: 'fr' | 'en'): Promise<void> {
  const fr = language === 'fr';
  const fields = { photo: true, headline: true, stack: true, location: true, availability: true, languages: true, remote: false };
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false,
    features: { SOCIAL_NETWORK: true, OFFER_FEED: true, PUBLIC_PROFILES: true, DATA_IMPORT: true, YEARLY_REVIEW: true } } }));
  await page.route('**/api/network/membership', route => route.fulfill({ json: { joined: true, handle: 'camille_l', fields } }));
  await page.route('**/api/network/members/camille_l/page', route => route.fulfill({ json: { card: me, followers: 48, following: 35,
    sharedSkills: [], followed: false, followsYou: false, own: true, muted: false, blocked: false, privateCard: false, requested: false } }));
  const post = (id: number, author: typeof leo, text: string, likes: number, comments: number, extra: Record<string, unknown> = {}) => ({ id,
    text, tags: [], createdAt: new Date(Date.now() - id * 3_600_000).toISOString().slice(0, 19), own: false, likes, liked: false, comments,
    commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, kind: 'POST', details: null,
    answerCommentId: null, poll: null, taken: null, author, ...extra });
  const posts = [
    post(1, leo, fr ? 'Retour d’expérience : deux ans de Kafka en production chez un assureur. Ce qui a marché, ce que je referais autrement. #kafka'
      : 'Two years of Kafka in production at an insurer: what worked, and what I would do differently. #kafka', 24, 6, { tags: ['kafka'] }),
    post(2, ines, fr ? 'Je ne peux pas prendre cette mission d’architecte Azure à Lyon, je la transmets !'
      : 'I can’t take this Azure architect mission in Lyon, passing it on!', 12, 3, { kind: 'MISSION', details: { role: fr ? 'Architecte Azure' : 'Azure architect',
      rateMin: 700, rateMax: 780, city: 'Lyon', remoteDays: 2, start: '2027-01-04', durationMonths: 6, stack: ['Azure', 'Kubernetes'] } })
  ];
  await page.route('**/api/network/page-views', route => route.fulfill({ json: { viewers: 23, private: false, views: [] } }));
  await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts, page: 0, more: false } }));
  await page.route('**/api/network/feed/for-you?**', route => route.fulfill({ json: { posts, page: 0, more: false } }));
  await page.route('**/api/network/feed/missions?**', route => route.fulfill({ json: { posts: [posts[1]], page: 0, more: false } }));
  await page.route('**/api/network/suggestions**', route => route.fulfill({ json: [
    { card: ines, reason: 'SAME_STACK', followedBy: 0, followedByName: null, sharedSkills: ['Kubernetes'], esn: null }] }));
  await page.route('**/api/network/tags/trending**', route => route.fulfill({ json: [{ tag: 'kafka', posts: 14 }, { tag: 'freelance', posts: 9 }] }));
  const event = (id: number, title: string, days: number, online: boolean, place: string | null, going: number) => ({ id, title,
    description: fr ? 'Une soirée entre freelances : retours d’expérience et missions.' : 'An evening among freelancers: experience and missions.',
    startsAt: new Date(Date.now() + days * 86_400_000).toISOString().slice(0, 11) + '18:30:00', endsAt: null, online, place,
    link: online ? 'https://meet.example.org/demo' : null, tags: ['kafka'], organizer: leo, going, interested: 4, mine: id === 1 ? 'GOING' : null,
    own: false, editable: false, cancelled: false });
  await page.route('**/api/network/events?**', route => route.fulfill({ json: { meetups: [
    event(1, fr ? 'Apéro Kafka à Nantes' : 'Kafka drinks in Nantes', 5, false, 'Le Lieu Unique, Nantes', 18),
    event(2, fr ? 'Webinaire : fixer son TJM en 2027' : 'Webinar: setting your daily rate in 2027', 12, true, null, 42)], page: 0, more: false } }));
  const idea = (id: number, title: string, votes: number, status: string, voted = false) => ({ id, title, description: null, status,
    statusNote: status === 'PLANNED' ? (fr ? 'Prévu pour le printemps.' : 'Planned for the spring.') : null, votes, voted, own: false, author: leo,
    createdAt: '2026-11-02T09:00:00', statusChangedAt: null, mergedInto: null });
  await page.route('**/api/network/ideas?**', route => route.fulfill({ json: { ideas: [
    idea(4, fr ? 'Export des factures vers mon comptable' : 'Export invoices to my accountant', 31, 'PLANNED', true),
    idea(7, fr ? 'Application mobile' : 'Mobile app', 22, 'OPEN'), idea(9, fr ? 'Thème sombre' : 'Dark theme', 18, 'DONE')],
    page: 0, more: false, administrator: false } }));
}

for (const language of ['fr', 'en'] as const) {
  test(`help screens (${language})`, async ({ page }) => {
    test.setTimeout(180_000);
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.emulateMedia({ colorScheme: 'light' });
    await mockDemoWorkspace(page, language, 'light');
    await mockDemoNetwork(page, language);
    const shot = async (name: string, path: string, ready: string) => {
      await page.goto(path);
      await page.locator(ready).first().waitFor();
      await settle(page);
      await page.screenshot({ path: `${OUT}/${name}-${language}.jpg`, type: 'jpeg', quality: 78 });
    };
    await shot('projects', '/projects', 'app-project-list, .project-list, main');
    await shot('project', '/projects/42', 'app-referral-card, .details-grid');
    await shot('clients', '/clients', 'h1');
    await shot('contacts', '/contacts', 'h1');
    await shot('calendar', '/calendar', 'h1');
    await shot('profile', '/profile', 'h1');
    await shot('network', '/network', '[data-post="1"]');
    await shot('events', '/network/events', '[data-event="1"]');
    await shot('ideas', '/network/ideas', '[data-idea="4"]');
    await shot('invoices', '/invoices', 'h1');
    await shot('documents', '/documents', 'h1');
  });
}
