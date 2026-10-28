import { Page } from '@playwright/test';
import { offer } from '../../e2e/helpers/offer-feed';
import { emptyDashboardStats, mockWorkspaceSession } from '../../e2e/helpers/workspace';
import { mockWorkspaceData } from '../../e2e/helpers/workspace-data';

/**
 * A demo workspace for the presentation page's screenshots: an invented freelancer, invented ESNs and clients, and
 * offers written for the picture. No real client, contact or offer.
 */
export async function mockDemoWorkspace(page: Page, language: 'fr' | 'en', theme: 'light' | 'dark'): Promise<void> {
  await mockWorkspaceSession(page, language);
  await page.addInitScript(([lang, mode]) => {
    localStorage.setItem('indezy-theme', mode);
    localStorage.setItem('indezy_user', JSON.stringify({ id: 1, email: 'camille@demo.example', firstName: 'Camille', lastName: 'Laurent' }));
    localStorage.setItem('indezy-lang', lang);
  }, [language, theme]);
  await mockWorkspaceData(page, language);
  const card = (projectId: number, role: string, status: string, clientName: string, dailyRate: number, techStack: string,
                extra: Record<string, unknown> = {}) => ({ projectId, role, status, clientName, dailyRate, techStack, workMode: 'HYBRID',
    sourceName: 'Free-Work', startDate: '2026-12-01', durationInMonths: 6, totalSteps: 0, completedSteps: 0, failedSteps: 0,
    updatedAt: new Date().toISOString(), ...extra });
  await page.route('**/api/projects/kanban/1*', route => route.fulfill({ json: { inactivityDays: 14,
    columnOrder: ['CONTACT', 'INTERVIEW', 'OFFER', 'LOST', 'WON'], columns: {
      CONTACT: [card(1, 'Lead développeur Java', 'CONTACT', 'Nova Conseil', 680, 'Java, Spring Boot, Kafka'),
        card(2, 'DevOps AWS', 'CONTACT', 'Atlas Digital', 620, 'AWS, Terraform, Kubernetes', { workMode: 'REMOTE' }),
        card(3, 'Développeur Angular', 'CONTACT', 'Héliade', 560, 'Angular, TypeScript')],
      INTERVIEW: [card(4, 'Architecte cloud', 'INTERVIEW', 'Banque Méridienne', 780, 'Azure, Kubernetes', { totalSteps: 3, completedSteps: 2 }),
        card(5, 'Tech lead Node.js', 'INTERVIEW', 'Orion Services', 700, 'Node.js, PostgreSQL', { totalSteps: 2, completedSteps: 1 })],
      OFFER: [card(6, 'Data engineer', 'OFFER', 'Énergie Verte', 650, 'Python, Spark, Airflow', { totalSteps: 3, completedSteps: 3 })],
      LOST: [card(8, 'Développeur Python', 'LOST', 'Studio Pixel', 480, 'Python, Django', { refusedBy: 'FREELANCE', lostReason: 'RATE_TOO_LOW' }),
        card(9, 'Développeur Go', 'LOST', 'Fintech Halo', 600, 'Go, gRPC', { refusedBy: 'RECRUITER', lostReason: 'POSITION_FILLED' })],
      WON: [card(7, 'Lead Java Kafka', 'WON', 'Assurances du Lac', 720, 'Java, Kafka', { totalSteps: 3, completedSteps: 3 })]
    } } }));
  const day = (offset: number) => new Date(Date.now() - offset * 3600 * 1000).toISOString();
  const offers = [
    offer(1, { title: 'Développeur Java Spring Boot senior', company: 'Nova Conseil', rateMin: 600, rateMax: 700, city: 'Paris', departmentCode: '75',
      departmentName: 'Paris', publishedAt: day(2), match: { score: 92, skills: ['Java', 'Spring Boot', 'Kafka'], rate: 'MEETS', place: 'NEAR' } }),
    offer(2, { title: 'Architecte Kubernetes / AWS', company: 'Atlas Digital', siteName: 'Collective.work', site: 'COLLECTIVE', rateMin: 750,
      rateMax: 850, workMode: 'REMOTE', remoteDaysPerWeek: 5, city: null, skills: ['Kubernetes', 'AWS', 'Terraform'], publishedAt: day(5) }),
    offer(3, { title: 'Tech lead Angular', company: 'Héliade', siteName: 'Cherry Pick', site: 'CHERRY_PICK', rateMin: 620, rateMax: 680,
      city: 'Lyon', departmentCode: '69', departmentName: 'Rhône', skills: ['Angular', 'TypeScript', 'RxJS'], publishedAt: day(9) }),
    offer(4, { title: 'Data engineer Python / Spark', company: 'Énergie Verte', siteName: 'LeHibou', site: 'LEHIBOU', rateMin: 580, rateMax: 650,
      city: 'Nantes', departmentCode: '44', departmentName: 'Loire-Atlantique', skills: ['Python', 'Spark', 'Airflow'], publishedAt: day(20) }),
    offer(5, { title: 'Développeur Go backend', company: 'Orion Services', siteName: 'Free-Work', rateMin: 600, rateMax: 650, city: 'Lille',
      departmentCode: '59', departmentName: 'Nord', skills: ['Go', 'gRPC', 'PostgreSQL'], publishedAt: day(30) })
  ];
  const site = (code: string, name: string, openOffers: number) => ({ site: code, name, homeUrl: 'https://example.org', openOffers,
    lastSuccessAt: new Date().toISOString(), failing: false });
  await page.route('**/api/offers/sites', route => route.fulfill({ json: [site('FREE_WORK', 'Free-Work', 812),
    site('COLLECTIVE', 'Collective.work', 430), site('CHERRY_PICK', 'Cherry Pick', 96), site('LEHIBOU', 'LeHibou', 64),
    site('WORKDISPO', 'WorkDispo', 48)] }));
  await page.route('**/api/offers?*', route => route.fulfill({ json: { content: offers, page: 0, size: 20, totalElements: 1284, totalPages: 65,
    distanceAvailable: true, matchAvailable: true } }));
  await page.route('**/api/offers', route => route.fulfill({ json: { content: offers, page: 0, size: 20, totalElements: 1284, totalPages: 65,
    distanceAvailable: true, matchAvailable: true } }));
  const soon = (days: number, hour: number) => {
    const date = new Date();
    date.setDate(date.getDate() + days);
    date.setHours(hour, 0, 0, 0);
    return date.toISOString().slice(0, 19);
  };
  await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: { ...emptyDashboardStats,
    targetDailyRate: 680, averageObtainedDailyRate: 705, totalProjects: 24, activeProjects: 6, wonProjects: 5, lostProjects: 7,
    averageDailyRate: 662, totalEstimatedRevenue: 158400, forecastRevenue: 61200,
    projectsByStatus: { CONTACT: 3, INTERVIEW: 2, OFFER: 1, WON: 5, LOST: 7 }, projectsByWorkMode: { HYBRID: 12, REMOTE: 8, ONSITE: 4 },
    upcomingInterviews: [
      { stepId: 1, projectId: 4, title: 'Entretien technique', date: soon(1, 10), role: 'Architecte cloud', clientName: 'Banque Méridienne' },
      { stepId: 2, projectId: 5, title: 'Rencontre client', date: soon(3, 14), role: 'Tech lead Node.js', clientName: 'Orion Services' }],
    followUpsDue: [{ contactId: 3, name: 'Julie Moreau', clientName: 'Nova Conseil', daysWaiting: 6, channel: 'EMAIL' }],
    trends: [{ metric: 'OPPORTUNITIES', current: 14, previous: 9 }, { metric: 'INTERVIEWS', current: 7, previous: 4 },
      { metric: 'WON', current: 2, previous: 1 }, { metric: 'DAILY_RATE', current: 705, previous: 650 }],
    sourceRoi: [{ sourceName: 'Free-Work', totalProjects: 9, wonProjects: 2, conversionRate: 22 },
      { sourceName: 'Réseau', totalProjects: 6, wonProjects: 2, conversionRate: 33 }, { sourceName: 'LeHibou', totalProjects: 5, wonProjects: 1, conversionRate: 20 }],
    dailyRateEvolution: [{ period: '2026-Q1', averageAskedRate: 640, averageObtainedRate: 620, projectCount: 5 },
      { period: '2026-Q2', averageAskedRate: 660, averageObtainedRate: 650, projectCount: 6 },
      { period: '2026-Q3', averageAskedRate: 690, averageObtainedRate: 680, projectCount: 7 },
      { period: '2026-Q4', averageAskedRate: 720, averageObtainedRate: 705, projectCount: 6 }],
    conversionFunnel: [], funnelBySource: [], funnelByClientType: [], funnelByEsn: [], missionsEndingSoon: [], staleOpportunities: [],
    upcomingRenewals: [], onThisDay: [], dormantContacts: [], skillTrends: [], processDurations: [], activityHeatmap: [], lostReasonsBreakdown: {} } }));
  await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, firstName: 'Camille', lastName: 'Laurent',
    email: 'camille@demo.example', skills: ['Java', 'Spring Boot', 'Kafka'], targetDailyRate: 680, city: 'Paris' } }));
  await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme, language, inactivityDays: 14, followUpDays: 5 } }));
}

/** An activity report waiting for its client's approval, as the validation link shows it. */
export async function mockDemoReport(page: Page, language: 'fr' | 'en', theme: 'light' | 'dark'): Promise<void> {
  await page.addInitScript(([lang, mode]) => {
    localStorage.setItem('indezy-lang', lang);
    localStorage.setItem('indezy-theme', mode);
  }, [language, theme]);
  const days = [];
  for (let date = new Date(2026, 10, 2); date.getMonth() === 10; date.setDate(date.getDate() + 1)) {
    const weekday = date.getDay();
    const iso = `2026-11-${String(date.getDate()).padStart(2, '0')}`;
    if (weekday !== 0 && weekday !== 6 && iso !== '2026-11-11') {
      days.push({ date: iso, type: weekday === 3 || weekday === 5 ? 'REMOTE' : 'ONSITE', halfDay: iso === '2026-11-20' });
    }
  }
  const sum = (filter: (d: { type: string; halfDay: boolean }) => boolean) => days.filter(filter).reduce((t, d) => t + (d.halfDay ? 0.5 : 1), 0);
  await page.route('**/api/public/activity-reports', route => route.fulfill({ json: {
    consultantName: 'Camille Laurent', clientName: 'Assurances du Lac', middlemanName: 'Nova Conseil', missionRole: 'Lead Java Kafka',
    showsLocation: true, report: { projectId: 7, year: 2026, month: 11, status: 'SUBMITTED', days,
      publicHolidays: [{ date: '2026-11-01', holiday: 'ALL_SAINTS' }, { date: '2026-11-11', holiday: 'ARMISTICE' }], businessDays: 20,
      workedDays: sum(() => true), onsiteDays: sum(d => d.type === 'ONSITE'), remoteDays: sum(d => d.type === 'REMOTE'), absenceDays: 0,
      dailyRate: 720, amount: sum(() => true) * 720, missionStart: '2026-09-01', missionEnd: '2027-02-28' } } }));
}
