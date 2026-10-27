import { Page } from '@playwright/test';
import { offer } from './offer-feed';
import { emptyDashboardStats } from './workspace';

/**
 * A small workspace behind every API call: one client with a contact, two opportunities, a source and a season, so
 * each page renders its usual content. Calls not listed answer an empty list.
 */
export async function mockWorkspaceData(page: Page, language: string): Promise<void> {
  const client = { id: 3, companyName: 'Acme', city: 'Lyon', isFinal: true, totalProjects: 2 };
  const contact = { id: 2, firstName: 'Alice', lastName: 'Martin', email: 'alice@acme.example', clientId: 3, clientName: 'Acme' };
  const project = {
    id: 42, role: 'Java developer', status: 'INTERVIEW', clientId: 3, clientName: 'Acme', dailyRate: 650, workMode: 'HYBRID',
    startDate: '2026-11-02', durationInMonths: 6, techStack: 'Java, Kafka', totalSteps: 1, completedSteps: 0
  };
  const other = { ...project, id: 43, role: 'Data engineer', status: 'CONTACT', techStack: 'Python' };
  const json: [RegExp, unknown][] = [
    [/\/api\/users\/preferences$/, { theme: 'light', language, inactivityDays: 14, followUpDays: 5 }],
    [/\/api\/users\/profile$/, { id: 1, firstName: 'Workspace', lastName: 'Test', email: 'workspace@example.test', skills: ['Java'] }],
    [/\/api\/users\/security$/, { twoFactorEnabled: false }],
    [/\/api\/freelances\/1$/, { id: 1, firstName: 'Workspace', lastName: 'Test' }],
    [/\/api\/projects\/stats\/dashboard\/1/, { ...emptyDashboardStats, totalProjects: 2, activeProjects: 1, averageDailyRate: 650 }],
    [/\/api\/projects\/kanban\/1/, { inactivityDays: 14, columnOrder: ['CONTACT', 'INTERVIEW', 'OFFER', 'WON', 'LOST'], columns: {
      CONTACT: [{ projectId: 43, role: 'Data engineer', status: 'CONTACT', clientName: 'Acme', dailyRate: 650, totalSteps: 0,
        completedSteps: 0, failedSteps: 0 }],
      INTERVIEW: [{ projectId: 42, role: 'Java developer', status: 'INTERVIEW', clientName: 'Acme', dailyRate: 650, totalSteps: 1,
        completedSteps: 0, failedSteps: 0 }],
      OFFER: [], WON: [], LOST: [] } }],
    [/\/api\/projects\/by-freelance\/1$/, [project, other]],
    [/\/api\/projects\/42$/, project],
    [/\/api\/interview-steps\/by-project\/42\/ordered$/, ['TO_PLAN', 'PLANNED', 'CANCELED', 'WAITING_FEEDBACK', 'VALIDATED',
      'FAILED'].map((status, i) => ({ id: i + 1, title: `Step ${i + 1}`, status, date: '2026-11-03T10:00:00', projectId: 42 }))],
    [/\/api\/projects\/42\/notes$/, [{ id: 5, projectId: 42, content: 'First **call**', createdAt: '2026-10-21T09:30:00' }]],
    [/\/api\/clients\/by-freelance\/1$/, [client]],
    [/\/api\/clients\/3$/, client],
    [/\/api\/contacts\/by-freelance\/1$/, [contact]],
    [/\/api\/contacts\/by-client\/3$/, [contact]],
    [/\/api\/contacts\/2$/, contact],
    [/\/api\/contacts\/2\/interactions$/, { interactions: [{ id: 30, contactId: 2, occurredOn: '2026-10-20', channel: 'EMAIL',
      direction: 'OUTGOING', replied: true, summary: 'Sent my CV' }], lastContactedOn: '2026-10-20', asked: 1, answered: 1,
      responseRate: 100, preferredChannel: 'EMAIL' }],
    [/\/api\/sources\/by-freelance\/1$/, [{ id: 7, name: 'Malt', type: 'JOB_BOARD', isListing: true }]],
    [/\/api\/seasons\/by-freelance\/1$/, [{ id: 9, name: 'Autumn 2026', startDate: '2026-09-01', active: true }]],
    [/\/api\/skills\/aliases$/, { JavaScript: ['JS'] }],
    [/\/api\/offers$/, { content: [offer(1)], page: 0, size: 20, totalElements: 1, totalPages: 1, distanceAvailable: false,
      matchAvailable: false }],
    [/\/api\/offers\/searches\/alerts$/, { newOffers: 0 }],
    [/\/api\/offers\/searches\/suggestions$/, { suggestions: [], missing: [] }],
    [/\/api\/users\/public-profile$/, { enabled: true, slug: 'workspace-test' }],
    [/\/api\/users\/activity$/, { events: [], total: 0, page: 0, size: 20 }],
    [/\/api\/users\/notifications$/, { weeklyReports: false, weeklyDigestSections: [], bookingEmails: true,
      newOffersBadge: true, emailsAvailable: true }],
    [/\/api\/public\/profiles\/workspace-test$/, { firstName: 'Workspace', lastName: 'Test', position: 'Java developer',
      city: 'Lyon', bio: 'Java and Kafka.', skills: [{ name: 'Java', level: 'EXPERT', years: 8 }], languages: ['French'],
      website: 'https://workspace.example', availableNow: true }],
    [/\/api\/users\/booking-page$/, { slug: null, settings: { enabled: false, title: '', durationMinutes: 30, bufferMinutes: 15,
      minNoticeHours: 24, maxDaysAhead: 21, windows: [] } }],
    [/\/api\/projects\/review\/1$/, { year: 2026, name: 'Workspace Test', missions: [{ projectId: 42, role: 'Java developer',
      clientName: 'Acme', dailyRate: 650, start: '2026-01-05', days: 120, reported: false, revenue: 78000 }],
      totals: { revenue: 78000, days: 120, averageDailyRate: 650, missions: 1 },
      previousYear: { revenue: 60000, days: 100, averageDailyRate: 600, missions: 1 },
      pipeline: { opened: 4, won: 1, lost: 1, conversionRate: 50, lostReasons: [{ reason: 'RATE_TOO_LOW', count: 1 }] },
      topSkills: [{ skill: 'Java', count: 3 }], network: { clients: 1, esns: 0, contacts: 2, exchanges: 4 } }]
  ];
  await page.route('**/api/**', route => {
    const url = new URL(route.request().url()).pathname;
    const found = json.find(([pattern]) => pattern.test(url));
    return route.fulfill({ json: found ? found[1] : [] });
  });
}
