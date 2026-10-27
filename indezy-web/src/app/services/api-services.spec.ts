import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Observable, firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';
import { AuthService } from './auth/auth.service';
import { ClientService } from './client/client.service';
import { ContactService } from './contact/contact.service';
import { GlobalSearchService } from './search/global-search.service';
import { FreelanceService } from './freelance/freelance.service';
import { InterviewStepService } from './interview-step/interview-step.service';
import { ProjectService } from './project/project.service';
import { SourceService } from './source/source.service';
import { UserManagementService } from './user-management/user-management.service';
import { ActivityReportService } from './activity-report/activity-report.service';
import { YearlyReviewService } from './review/yearly-review.service';
import { PublicProfileService } from './public-profile/public-profile.service';
import { AdminService } from './admin/admin.service';
import { AccountSecurityService } from './account-security/account-security.service';
import { BillingService } from './billing/billing.service';
import { InvoiceService } from './invoice/invoice.service';
import { PurchaseOrderService } from './purchase-order/purchase-order.service';
import { ComplianceService } from './compliance/compliance.service';
import { WebhookService } from './webhook/webhook.service';
import { ProjectStatus, StepStatus } from '../models';

/** One HTTP wrapper call: what the service is asked, and the request it must send. */
interface ApiCase {
  name: string;
  call: () => Observable<unknown>;
  method: string;
  url: string;
  params?: Record<string, string>;
  body?: unknown;
  /** Response to flush when the endpoint does not return JSON. */
  response?: unknown;
  headers?: Record<string, string>;
}

describe('API services', () => {
  const api = environment.apiUrl;
  let http: HttpTestingController;
  let user: { id: number } | null;

  beforeEach(() => {
    user = { id: 7 };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { getUser: () => user } }
      ]
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const client = (): ClientService => TestBed.inject(ClientService);
  const contact = (): ContactService => TestBed.inject(ContactService);
  const globalSearch = (): GlobalSearchService => TestBed.inject(GlobalSearchService);
  const freelance = (): FreelanceService => TestBed.inject(FreelanceService);
  const steps = (): InterviewStepService => TestBed.inject(InterviewStepService);
  const project = (): ProjectService => TestBed.inject(ProjectService);
  const source = (): SourceService => TestBed.inject(SourceService);
  const users = (): UserManagementService => TestBed.inject(UserManagementService);
  const reports = (): ActivityReportService => TestBed.inject(ActivityReportService);
  const reportsOf2 = `${api}/projects/2/activity-reports`;
  const review = (): YearlyReviewService => TestBed.inject(YearlyReviewService);
  const publicProfile = (): PublicProfileService => TestBed.inject(PublicProfileService);
  const admin = (): AdminService => TestBed.inject(AdminService);
  const accountSecurity = (): AccountSecurityService => TestBed.inject(AccountSecurityService);
  const billing = (): BillingService => TestBed.inject(BillingService);
  const invoices = (): InvoiceService => TestBed.inject(InvoiceService);
  const purchaseOrders = (): PurchaseOrderService => TestBed.inject(PurchaseOrderService);
  const compliance = (): ComplianceService => TestBed.inject(ComplianceService);
  const webhooks = (): WebhookService => TestBed.inject(WebhookService);
  const packToken = { 'X-Pack-Token': 'pack-token' };
  const linkToken = { 'X-Report-Token': 'link-token' };
  const decision = { approved: false, validatorName: 'Jean Dupont', comment: 'Le 11 est férié' };

  const cases: ApiCase[] = [
    { name: 'clients of the signed-in freelance', call: () => client().getForCurrentFreelance(), method: 'GET', url: `${api}/clients/by-freelance/7` },
    { name: 'client by id', call: () => client().getById(3), method: 'GET', url: `${api}/clients/3` },
    { name: 'create client', call: () => client().create({ companyName: 'Acme' } as never), method: 'POST', url: `${api}/clients`, body: { companyName: 'Acme' } },
    { name: 'update client', call: () => client().update(3, { id: 3 } as never), method: 'PUT', url: `${api}/clients/3`, body: { id: 3 } },
    { name: 'delete picked clients', call: () => client().deleteAll([3, 6]), method: 'POST', url: `${api}/clients/bulk-delete`, body: { ids: [3, 6] } },
    { name: 'delete client', call: () => client().delete(3), method: 'DELETE', url: `${api}/clients/3` },

    { name: 'contacts of the signed-in freelance', call: () => contact().getForCurrentFreelance(), method: 'GET', url: `${api}/contacts/by-freelance/7` },
    { name: 'contact by id', call: () => contact().getById(4), method: 'GET', url: `${api}/contacts/4` },
    { name: 'search everything', call: () => globalSearch().search('kafka'), method: 'GET', url: `${api}/search`, params: { q: 'kafka' } },
    { name: 'contacts of a client', call: () => contact().getByClientId(3), method: 'GET', url: `${api}/contacts/by-client/3` },
    { name: 'create contact', call: () => contact().create({ firstName: 'Ana' } as never), method: 'POST', url: `${api}/contacts`, body: { firstName: 'Ana' } },
    { name: 'update contact', call: () => contact().update(4, { id: 4 } as never), method: 'PUT', url: `${api}/contacts/4`, body: { id: 4 } },
    { name: 'delete contact', call: () => contact().delete(4), method: 'DELETE', url: `${api}/contacts/4` },
    { name: 'import contacts', call: () => contact().importForClient(3, 'csv'), method: 'POST', url: `${api}/contacts/import/by-client/3`, body: { content: 'csv' } },
    { name: 'delete picked contacts', call: () => contact().deleteAll([4, 5]), method: 'POST', url: `${api}/contacts/bulk-delete`, body: { ids: [4, 5] } },
    { name: 'contact exchanges', call: () => contact().getInteractions(4), method: 'GET', url: `${api}/contacts/4/interactions` },
    { name: 'exchanges about an opportunity', call: () => contact().getProjectInteractions(12), method: 'GET', url: `${api}/projects/12/interactions` },
    { name: 'record an exchange', call: () => contact().addInteraction(4, { occurredOn: '2026-09-01' } as never), method: 'POST', url: `${api}/contacts/4/interactions`, body: { occurredOn: '2026-09-01' } },
    { name: 'change an exchange', call: () => contact().updateInteraction(4, 2, { occurredOn: '2026-09-01' } as never), method: 'PUT', url: `${api}/contacts/4/interactions/2`, body: { occurredOn: '2026-09-01' } },
    { name: 'delete an exchange', call: () => contact().deleteInteraction(4, 2), method: 'DELETE', url: `${api}/contacts/4/interactions/2` },
    { name: 'people a contact works with', call: () => contact().getRelations(4), method: 'GET', url: `${api}/contacts/4/relations` },
    { name: 'links of a client\'s contacts', call: () => contact().getClientRelations(3), method: 'GET', url: `${api}/clients/3/contact-relations` },
    { name: 'link two contacts', call: () => contact().link(4, 5, 'Same team'), method: 'PUT', url: `${api}/contacts/4/relations`, body: { otherId: 5, note: 'Same team' } },
    { name: 'unlink two contacts', call: () => contact().unlink(4, 5), method: 'DELETE', url: `${api}/contacts/4/relations/5` },

    { name: 'freelance by id', call: () => freelance().getById(7), method: 'GET', url: `${api}/freelances/7` },
    { name: 'update freelance', call: () => freelance().update(7, { id: 7 } as never), method: 'PUT', url: `${api}/freelances/7`, body: { id: 7 } },

    { name: 'steps of a project', call: () => steps().getByProjectIdOrderByDate(2), method: 'GET', url: `${api}/interview-steps/by-project/2/ordered` },
    { name: 'steps of a freelance', call: () => steps().getByFreelanceIdAndStatus(7), method: 'GET', url: `${api}/interview-steps/by-freelance/7` },
    { name: 'steps with a status', call: () => steps().getByFreelanceIdAndStatus(7, StepStatus.PLANNED), method: 'GET', url: `${api}/interview-steps/by-freelance/7`, params: { status: 'PLANNED' } },
    { name: 'create step', call: () => steps().create({ title: 'Call' } as never), method: 'POST', url: `${api}/interview-steps`, body: { title: 'Call' } },
    { name: 'update step', call: () => steps().update(5, { id: 5 } as never), method: 'PUT', url: `${api}/interview-steps/5`, body: { id: 5 } },
    { name: 'step status', call: () => steps().updateStatus(5, StepStatus.VALIDATED), method: 'PATCH', url: `${api}/interview-steps/5/status`, params: { status: 'VALIDATED' } },
    { name: 'delete step', call: () => steps().delete(5), method: 'DELETE', url: `${api}/interview-steps/5` },

    { name: 'project by id', call: () => project().getById(2), method: 'GET', url: `${api}/projects/2` },
    { name: 'projects of a freelance', call: () => project().getByFreelanceId(7), method: 'GET', url: `${api}/projects/by-freelance/7` },
    { name: 'projects of a client', call: () => project().getByClientId(3), method: 'GET', url: `${api}/projects/by-client/3` },
    { name: 'create project', call: () => project().create({ role: 'Dev' } as never), method: 'POST', url: `${api}/projects`, body: { role: 'Dev' } },
    { name: 'update project', call: () => project().update(2, { role: 'Dev' } as never), method: 'PUT', url: `${api}/projects/2`, body: { role: 'Dev' } },
    { name: 'delete project', call: () => project().delete(2), method: 'DELETE', url: `${api}/projects/2` },
    { name: 'project status', call: () => project().updateStatus(2, ProjectStatus.LOST, 'RATE_TOO_LOW'), method: 'PATCH', url: `${api}/projects/2/status`, params: { status: 'LOST', lostReason: 'RATE_TOO_LOW' } },
    { name: 'favorite', call: () => project().toggleFavorite(2), method: 'PATCH', url: `${api}/projects/2/favorite` },
    { name: 'rename tag', call: () => project().renameTag(7, 'JS', 'JavaScript'), method: 'PUT', url: `${api}/projects/by-freelance/7/tags/rename`, body: { from: 'JS', to: 'JavaScript' } },
    { name: 'kanban board', call: () => project().getKanbanBoard(7), method: 'GET', url: `${api}/projects/kanban/7` },
    { name: 'season kanban board', call: () => project().getKanbanBoard(7, 9), method: 'GET', url: `${api}/projects/kanban/7`, params: { seasonId: '9' } },
    { name: 'reorder column', call: () => project().reorderKanbanColumn(7, [3, 1]), method: 'PUT', url: `${api}/projects/kanban/7/reorder`, body: [3, 1] },
    { name: 'dashboard stats', call: () => project().getDashboardStats(7), method: 'GET', url: `${api}/projects/stats/dashboard/7` },
    { name: 'season dashboard stats', call: () => project().getDashboardStats(7, 9), method: 'GET', url: `${api}/projects/stats/dashboard/7`, params: { seasonId: '9' } },
    { name: 'yearly CSV', call: () => project().downloadYearlySummary(7, 2026), method: 'GET', url: `${api}/projects/export/csv/7`, params: { year: '2026' }, response: new Blob(['csv']) },
    { name: 'yearly review', call: () => review().get(7, 2026), method: 'GET', url: `${api}/projects/review/7`, params: { year: '2026' } },
    { name: 'yearly review PDF', call: () => review().downloadPdf(7, 2026, 'EN'), method: 'GET', url: `${api}/projects/review/7/pdf`, params: { year: '2026', language: 'EN' }, response: new Blob(['%PDF']) },
    { name: 'project notes', call: () => project().getProjectNotes(2), method: 'GET', url: `${api}/projects/2/notes` },
    { name: 'add note', call: () => project().addProjectNote(2, 'Called'), method: 'POST', url: `${api}/projects/2/notes`, body: { content: 'Called' } },
    { name: 'delete note', call: () => project().deleteProjectNote(2, 8), method: 'DELETE', url: `${api}/projects/2/notes/8` },

    { name: 'activity reports of a mission', call: () => reports().getReports(2), method: 'GET', url: reportsOf2 },
    { name: 'activity report of a month', call: () => reports().getReport(2, 2026, 11), method: 'GET', url: `${reportsOf2}/2026/11` },
    { name: 'save activity report', call: () => reports().save(2, 2026, 11, { days: [] }), method: 'PUT', url: `${reportsOf2}/2026/11`, body: { days: [] } },
    { name: 'submit activity report', call: () => reports().submit(2, 2026, 11), method: 'POST', url: `${reportsOf2}/2026/11/submit` },
    { name: 'renew validation link', call: () => reports().renewLink(2, 2026, 11), method: 'POST', url: `${reportsOf2}/2026/11/link` },
    { name: 'reopen activity report', call: () => reports().reopen(2, 2026, 11), method: 'POST', url: `${reportsOf2}/2026/11/reopen` },
    { name: 'activity report PDF', call: () => reports().downloadPdf(2, 2026, 11), method: 'GET', url: `${reportsOf2}/2026/11/pdf`, response: new Blob(['%PDF']) },
    { name: 'public activity report', call: () => reports().getPublicReport('link-token'), method: 'GET', url: `${api}/public/activity-reports`, headers: linkToken },
    { name: 'public activity report PDF', call: () => reports().downloadPublicPdf('link-token'), method: 'GET', url: `${api}/public/activity-reports/pdf`, headers: linkToken, response: new Blob(['%PDF']) },
    { name: 'activity report decision', call: () => reports().decide('link-token', decision), method: 'POST', url: `${api}/public/activity-reports/decision`, headers: linkToken, body: decision },

    { name: 'source by id', call: () => source().getById(6), method: 'GET', url: `${api}/sources/6` },
    { name: 'sources of a freelance', call: () => source().getByFreelanceId(7), method: 'GET', url: `${api}/sources/by-freelance/7` },
    { name: 'create source', call: () => source().create({ name: 'Malt' } as never), method: 'POST', url: `${api}/sources`, body: { name: 'Malt' } },
    { name: 'update source', call: () => source().update(6, { name: 'Malt' } as never), method: 'PUT', url: `${api}/sources/6`, body: { name: 'Malt' } },
    { name: 'delete source', call: () => source().delete(6), method: 'DELETE', url: `${api}/sources/6` },

    { name: 'profile', call: () => users().getUserProfile(), method: 'GET', url: `${api}/users/profile` },
    { name: 'update profile', call: () => users().updateUserProfile({ firstName: 'Zaid' }), method: 'PUT', url: `${api}/users/profile`, body: { firstName: 'Zaid' } },
    { name: 'avatar upload', call: () => users().uploadAvatar(new Blob(['x'])), method: 'POST', url: `${api}/users/avatar`, response: 'data:image/jpeg;base64,eA==' },
    { name: 'password change', call: () => users().changePassword({ currentPassword: 'a', newPassword: 'b', confirmPassword: 'b' }), method: 'POST', url: `${api}/users/change-password`, body: { currentPassword: 'a', newPassword: 'b', confirmPassword: 'b' } },
    { name: 'preferences', call: () => users().getUserPreferences(), method: 'GET', url: `${api}/users/preferences` },
    { name: 'update preferences', call: () => users().updateUserPreferences({ theme: 'dark' }), method: 'PUT', url: `${api}/users/preferences`, body: { theme: 'dark' } },
    { name: 'notification settings', call: () => users().getNotificationSettings(), method: 'GET', url: `${api}/users/notifications` },
    { name: 'update notification settings', call: () => users().updateNotificationSettings({ weeklyReports: true }), method: 'PUT', url: `${api}/users/notifications`, body: { weeklyReports: true } },
    { name: 'security settings', call: () => users().getSecuritySettings(), method: 'GET', url: `${api}/users/security` },
    { name: 'two-factor setup', call: () => users().enableTwoFactor(), method: 'POST', url: `${api}/users/security/2fa/enable`, body: {} },
    { name: 'two-factor verification', call: () => users().verifyTwoFactor('123456'), method: 'POST', url: `${api}/users/security/2fa/verify`, body: { code: '123456' } },
    { name: 'two-factor removal', call: () => users().disableTwoFactor('123456'), method: 'POST', url: `${api}/users/security/2fa/disable`, body: { code: '123456' } },
    { name: 'data export', call: () => users().exportUserData(), method: 'GET', url: `${api}/users/export`, response: new Blob(['{}']) },
    { name: 'data import preview', call: () => users().previewImport(new Blob(['{}'])), method: 'POST', url: `${api}/users/import/preview` },
    { name: 'public profile settings', call: () => publicProfile().settings(), method: 'GET', url: `${api}/users/public-profile` },
    { name: 'save public profile settings', call: () => publicProfile().save({ enabled: true, slug: 'alice' }), method: 'PUT', url: `${api}/users/public-profile`, body: { enabled: true, slug: 'alice' } },
    { name: 'public profile', call: () => publicProfile().view('alice martin'), method: 'GET', url: `${api}/public/profiles/alice%20martin` },
    { name: 'data import', call: () => users().importData(new Blob(['{}'])), method: 'POST', url: `${api}/users/import` },

    { name: 'invoices', call: () => invoices().list(7), method: 'GET', url: `${api}/invoices/by-freelance/7` },
    { name: 'invoice by hand', call: () => invoices().create({ clientId: 3 } as never), method: 'POST', url: `${api}/invoices`, body: { clientId: 3 } },
    { name: 'invoice of a report', call: () => invoices().fromReport(2, 2026, 11), method: 'POST', url: `${api}/invoices/from-report`, body: { projectId: 2, year: 2026, month: 11 } },
    { name: 'credit note of an invoice', call: () => invoices().creditNote(5), method: 'POST', url: `${api}/invoices/5/credit-note` },
    { name: 'accountant export', call: () => invoices().exportYear(2026, 'REGISTER', 'EN'), method: 'GET',
      url: `${api}/invoices/export/2026`, params: { format: 'REGISTER', language: 'EN' }, response: new Blob(['csv']) },
    { name: 'purchase orders of a mission', call: () => purchaseOrders().list(2), method: 'GET', url: `${api}/projects/2/purchase-orders` },
    { name: 'add a purchase order', call: () => purchaseOrders().create(2, { number: 'BC' } as never), method: 'POST',
      url: `${api}/projects/2/purchase-orders`, body: { number: 'BC' } },
    { name: 'change a purchase order', call: () => purchaseOrders().update(4, { number: 'BC' } as never), method: 'PUT',
      url: `${api}/purchase-orders/4`, body: { number: 'BC' } },
    { name: 'delete a purchase order', call: () => purchaseOrders().delete(4), method: 'DELETE', url: `${api}/purchase-orders/4` },
    { name: 'compliance documents', call: () => compliance().list(), method: 'GET', url: `${api}/compliance-documents` },
    { name: 'store a compliance document', call: () => compliance().upload(new File(['%PDF'], 'a.pdf'), { type: 'OTHER', label: 'Diploma',
      issuedOn: null, expiresOn: '2027-01-01' }), method: 'POST', url: `${api}/compliance-documents` },
    { name: 'change a compliance document', call: () => compliance().update(4, { type: 'IDENTITY' } as never), method: 'PUT',
      url: `${api}/compliance-documents/4`, body: { type: 'IDENTITY' } },
    { name: 'delete a compliance document', call: () => compliance().delete(4), method: 'DELETE', url: `${api}/compliance-documents/4` },
    { name: 'compliance document content', call: () => compliance().content(4), method: 'GET', url: `${api}/compliance-documents/4/content`,
      response: new Blob(['%PDF']) },
    { name: 'shared links', call: () => compliance().packs(), method: 'GET', url: `${api}/compliance-packs` },
    { name: 'share documents', call: () => compliance().share({ recipient: 'ESN' } as never), method: 'POST', url: `${api}/compliance-packs`,
      body: { recipient: 'ESN' } },
    { name: 'revoke a link', call: () => compliance().revoke(5), method: 'POST', url: `${api}/compliance-packs/5/revoke`, body: null },
    { name: 'shared documents', call: () => compliance().publicPack('pack-token'), method: 'GET', url: `${api}/public/compliance-packs`,
      headers: packToken },
    { name: 'shared document', call: () => compliance().publicDocument('pack-token', 4), method: 'GET',
      url: `${api}/public/compliance-packs/documents/4`, headers: packToken, response: new Blob(['%PDF']) },
    { name: 'webhooks', call: () => webhooks().list(), method: 'GET', url: `${api}/users/webhooks` },
    { name: 'add a webhook', call: () => webhooks().create({ url: 'https://x', events: [] }), method: 'POST', url: `${api}/users/webhooks`,
      body: { url: 'https://x', events: [] } },
    { name: 'change a webhook', call: () => webhooks().update(3, { url: 'https://x', events: [] }), method: 'PUT',
      url: `${api}/users/webhooks/3`, body: { url: 'https://x', events: [] } },
    { name: 'delete a webhook', call: () => webhooks().delete(3), method: 'DELETE', url: `${api}/users/webhooks/3` },
    { name: 'test a webhook', call: () => webhooks().ping(3), method: 'POST', url: `${api}/users/webhooks/3/ping`, body: null },
    { name: 'webhook deliveries', call: () => webhooks().deliveries(3), method: 'GET', url: `${api}/users/webhooks/3/deliveries` },
    { name: 'change a draft', call: () => invoices().update(5, { clientId: 3 } as never), method: 'PUT', url: `${api}/invoices/5`, body: { clientId: 3 } },
    { name: 'delete a draft', call: () => invoices().delete(5), method: 'DELETE', url: `${api}/invoices/5` },
    { name: 'issue an invoice', call: () => invoices().issue(5, '2026-10-27'), method: 'POST', url: `${api}/invoices/5/issue`, body: { issueDate: '2026-10-27' } },
    { name: 'invoice paid', call: () => invoices().setPaid(5, '2026-11-02'), method: 'PUT', url: `${api}/invoices/5/payment`, body: { paidOn: '2026-11-02' } },
    { name: 'invoice PDF', call: () => invoices().downloadPdf(5), method: 'GET', url: `${api}/invoices/5/pdf`, response: new Blob(['%PDF']) },
    { name: 'billing identity', call: () => billing().identity(), method: 'GET', url: `${api}/users/billing` },
    { name: 'save billing identity', call: () => billing().saveIdentity({ legalName: 'Alice' } as never), method: 'PUT', url: `${api}/users/billing`, body: { legalName: 'Alice' } },
    { name: 'open sessions', call: () => accountSecurity().sessions(), method: 'GET', url: `${api}/users/sessions` },
    { name: 'end a session', call: () => accountSecurity().end(9), method: 'DELETE', url: `${api}/users/sessions/9` },
    { name: 'end the other sessions', call: () => accountSecurity().endOthers(), method: 'POST', url: `${api}/users/sessions/end-others` },
    { name: 'API tokens', call: () => accountSecurity().apiTokens(), method: 'GET', url: `${api}/users/api-tokens` },
    { name: 'create an API token', call: () => accountSecurity().createApiToken('Zapier', 90), method: 'POST', url: `${api}/users/api-tokens`, body: { name: 'Zapier', expiresInDays: 90, scopes: [] } },
    { name: 'revoke an API token', call: () => accountSecurity().revokeApiToken(4), method: 'DELETE', url: `${api}/users/api-tokens/4` },
    { name: 'account activity', call: () => accountSecurity().activity(1), method: 'GET', url: `${api}/users/activity`, params: { page: '1' } },

    { name: 'admin overview', call: () => admin().overview(), method: 'GET', url: `${api}/admin/overview` },
    { name: 'admin accounts', call: () => admin().accounts('ali', 2), method: 'GET', url: `${api}/admin/accounts`, params: { query: 'ali', page: '2' } },
    { name: 'disable account', call: () => admin().setDisabled(4, true), method: 'POST', url: `${api}/admin/accounts/4/disable` },
    { name: 'enable account', call: () => admin().setDisabled(4, false), method: 'POST', url: `${api}/admin/accounts/4/enable` },
    { name: 'feature flags', call: () => admin().features(), method: 'GET', url: `${api}/admin/features` },
    { name: 'turn a feature off', call: () => admin().setFeature('OFFER_FEED', false), method: 'PUT', url: `${api}/admin/features/OFFER_FEED`, body: { enabled: false } }
  ];

  for (const apiCase of cases) {
    it(`sends the ${apiCase.name} request`, async () => {
      const response = firstValueFrom(apiCase.call());
      const req = http.expectOne(r => r.url === apiCase.url);
      expect(req.request.method).toBe(apiCase.method);
      for (const [key, value] of Object.entries(apiCase.params ?? {})) {
        expect(req.request.params.get(key)).toBe(value);
      }
      if (!apiCase.params) {
        expect(req.request.params.keys()).toEqual([]);
      }
      if (apiCase.body !== undefined) {
        expect(req.request.body).toEqual(apiCase.body);
      }
      for (const [name, value] of Object.entries(apiCase.headers ?? {})) {
        expect(req.request.headers.get(name)).toBe(value);
      }
      expect(req.request.urlWithParams).not.toContain('link-token');
      req.flush(apiCase.response ?? (apiCase.method === 'DELETE' ? null : {}));
      await response;
    });
  }

  it('lists nothing without a signed-in freelance', async () => {
    user = null;
    expect(await firstValueFrom(client().getForCurrentFreelance())).toEqual([]);
    expect(await firstValueFrom(contact().getForCurrentFreelance())).toEqual([]);
  });

  it('knows every source type', () => {
    expect(source().getSourceTypes()).toEqual(['JOB_BOARD', 'SOCIAL_MEDIA', 'EMAIL', 'CALL', 'SMS']);
  });
});
