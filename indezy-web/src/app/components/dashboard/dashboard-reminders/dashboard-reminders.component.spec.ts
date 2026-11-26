import { MatDialog } from '@angular/material/dialog';
import { AiAvailabilityDialogComponent } from '../../../shared/components/ai-availability-dialog/ai-availability-dialog.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { DashboardRemindersComponent } from './dashboard-reminders.component';
import { AiMessageDialogComponent } from '../../../shared/components/ai-message-dialog/ai-message-dialog.component';
import { of, throwError } from 'rxjs';
import { DashboardStatsDto, Invoice, OnThisDayItem, ProjectDto, ProjectStatus } from '../../../models';
import { InvoiceService } from '../../../services/invoice/invoice.service';
import { ClientEmailDialogComponent } from '../../../shared/components/client-email-dialog/client-email-dialog.component';
import { ProjectService } from '../../../services/project/project.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { provideHttpClient } from '@angular/common/http';

describe('DashboardRemindersComponent', () => {
  let fixture: ComponentFixture<DashboardRemindersComponent>;
  let component: DashboardRemindersComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardRemindersComponent, RouterModule.forRoot([]), TranslateModule.forRoot()],
      providers: [provideHttpClient()]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardRemindersComponent);
    component = fixture.componentInstance;
  });

  function render(stats: Partial<DashboardStatsDto> | null): HTMLElement {
    fixture.componentRef.setInput('stats', stats as DashboardStatsDto);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function section(element: HTMLElement, accent: string): HTMLElement {
    return element.querySelector<HTMLElement>(`.reminder-card.${accent}`)!;
  }

  function rows(card: HTMLElement): HTMLAnchorElement[] {
    return Array.from(card.querySelectorAll<HTMLAnchorElement>('a.reminder-row'));
  }

  it('returns empty lists when no stats are set', () => {
    expect(component.getMissionsEndingSoon()).toEqual([]);
    expect(component.getDormantContacts()).toEqual([]);
    expect(component.getStaleOpportunities()).toEqual([]);
    expect(component.getUpcomingInterviews()).toEqual([]);
  });

  it('reads reminder lists from the provided stats', () => {
    component.stats = {
      dormantContacts: [{ id: 3, name: 'Old', clientName: 'Acme', monthsSinceActivity: 7 }]
    } as DashboardStatsDto;

    expect(component.getDormantContacts().length).toBe(1);
    expect(component.getMissionsEndingSoon()).toEqual([]);
  });

  it('links "on this day" items by type', () => {
    expect(component.getOnThisDayLink({ type: 'CONTACT', id: 5 } as OnThisDayItem)).toEqual(['/contacts', 5]);
    expect(component.getOnThisDayLink({ type: 'PROJECT', id: 9 } as OnThisDayItem)).toEqual(['/projects', 9]);
  });

  it('renders nothing without reminders', () => {
    expect(render(null).querySelector('.section-title')).toBeNull();
    expect(component.hasReminders()).toBeFalse();

    expect(render({ missionsEndingSoon: [], upcomingRenewals: [] }).querySelector('.reminders-grid')).toBeNull();
  });

  it('lists every kind of reminder with links to the project or contact', () => {
    const element = render({
      missionsEndingSoon: [{ projectId: 1, role: 'Java dev', clientName: 'Acme', endDate: '2026-11-01', daysUntilEnd: 6 }],
      upcomingRenewals: [{ projectId: 2, role: 'Tech lead', clientName: 'Globex', renewalDate: '2026-11-10', daysUntilRenewal: 15 }],
      staleOpportunities: [{ projectId: 3, role: 'Architect', clientName: 'Initech', status: 'IDEA', daysSinceActivity: 40 }],
      dormantContacts: [{ id: 4, name: 'Jane Doe', clientName: 'Umbrella', monthsSinceActivity: 7 }],
      onThisDay: [
        { type: 'PROJECT', id: 5, label: 'Old mission', subLabel: 'Hooli', date: '2025-10-26', yearsAgo: 1 },
        { type: 'CONTACT', id: 6, label: 'Old friend', subLabel: 'Pied Piper', date: '2024-10-26', yearsAgo: 2 }
      ]
    });

    expect(component.hasReminders()).toBeTrue();
    expect(element.querySelector('.section-title')!.textContent).toContain('dashboard.remindersTitle');
    const hrefs = (accent: string): (string | null)[] => rows(section(element, accent)).map(row => row.getAttribute('href'));
    expect(hrefs('accent-orange')).toEqual(['/projects/1']);
    expect(hrefs('accent-blue')).toEqual(['/projects/2']);
    expect(hrefs('accent-brown')).toEqual(['/projects/3']);
    expect(hrefs('accent-grey')).toEqual(['/contacts/4']);
    expect(hrefs('accent-pink')).toEqual(['/projects/5', '/contacts/6']);
    expect(section(element, 'accent-orange').textContent).toContain('Java dev');
    expect(section(element, 'accent-brown').textContent).toContain('projects.statuses.IDEA');
    expect(section(element, 'accent-grey').textContent).toContain('Jane Doe');
    expect(section(element, 'accent-pink').textContent).toContain('Pied Piper');
    expect(element.querySelector('.toggle-btn')).toBeNull();
  });

  it('lists the interviews of the week first, with their time and opportunity', () => {
    const element = render({
      upcomingInterviews: [{ stepId: 9, projectId: 4, title: 'Technical test', role: 'Java dev', clientName: 'Alten', date: '2026-10-27T14:30:00' }],
      missionsEndingSoon: [{ projectId: 1, role: 'Java dev', clientName: 'Acme', endDate: '2026-11-01', daysUntilEnd: 6 }]
    });

    expect(component.hasReminders()).toBeTrue();
    const card = element.querySelector<HTMLElement>('.reminder-card')!;
    expect(card.classList).toContain('accent-teal');
    expect(rows(card).map(row => row.getAttribute('href'))).toEqual(['/projects/4']);
    expect(card.textContent).toContain('Technical test');
    expect(card.textContent).toContain('Java dev · Alten');
    expect(card.textContent).toContain('Tue 27 Oct, 14:30');
    expect(render({ upcomingInterviews: [{ stepId: 1, projectId: 2, title: 'Call', role: 'Ops', date: '2026-10-27T09:00:00' }] })
      .querySelector('.reminder-card.accent-teal')!.textContent).not.toContain(' · ');
  });

  it('shows the first rows of a long list until it is expanded', () => {
    const missions = Array.from({ length: 7 }, (_, index) => ({
      projectId: index + 1, role: `Role ${index + 1}`, clientName: 'Acme', endDate: '2026-11-01', daysUntilEnd: index
    }));
    const element = render({ missionsEndingSoon: missions });
    const card = section(element, 'accent-orange');

    expect(rows(card).length).toBe(DashboardRemindersComponent.COLLAPSED_SIZE);
    expect(card.querySelector('.count')!.textContent).toBe('7');
    const toggle = card.querySelector<HTMLButtonElement>('.toggle-btn')!;
    expect(toggle.textContent).toContain('dashboard.showAll');

    toggle.click();
    fixture.detectChanges();

    expect(component.isExpanded('ending')).toBeTrue();
    expect(rows(card).length).toBe(7);
    expect(toggle.textContent).toContain('dashboard.showLess');

    toggle.click();
    fixture.detectChanges();

    expect(component.isExpanded('ending')).toBeFalse();
    expect(rows(card).length).toBe(5);
  });

  it('expands each long list on its own', () => {
    const many = <T>(make: (index: number) => T): T[] => Array.from({ length: 6 }, (_, index) => make(index + 1));
    const element = render({
      upcomingRenewals: many(id => ({ projectId: id, role: 'R', clientName: 'C', renewalDate: '2026-11-10', daysUntilRenewal: id })),
      staleOpportunities: many(id => ({ projectId: id, role: 'R', clientName: 'C', status: 'IDEA', daysSinceActivity: id })),
      dormantContacts: many(id => ({ id, name: 'N', clientName: 'C', monthsSinceActivity: id })),
      onThisDay: many(id => ({ type: 'PROJECT' as const, id, label: 'L', subLabel: 'S', date: '2025-10-26', yearsAgo: 1 }))
    });

    for (const [accent, key] of [['accent-blue', 'renewals'], ['accent-brown', 'stale'], ['accent-grey', 'dormant'],
      ['accent-pink', 'onThisDay']]) {
      const card = section(element, accent);
      expect(rows(card).length).withContext(accent).toBe(5);
      card.querySelector<HTMLButtonElement>('.toggle-btn')!.click();
      fixture.detectChanges();
      expect(component.isExpanded(key)).withContext(key).toBeTrue();
      expect(rows(card).length).withContext(accent).toBe(6);
      expect(card.querySelector('.toggle-btn')!.textContent).toContain('dashboard.showLess');
    }
  });

  it('caps a list unless it was expanded', () => {
    const list = [1, 2, 3, 4, 5, 6];

    expect(component.visible(list, 'any')).toEqual([1, 2, 3, 4, 5]);
    component.toggle('any');
    expect(component.visible(list, 'any')).toEqual(list);
  });

  it('lists the follow-ups due, longest waiting first, with how long and by which channel', () => {
    const element = render({
      followUpsDue: [
        { contactId: 3, name: 'Bob Durand', clientName: 'Acme', channel: 'EMAIL', sentOn: '2026-09-01', daysWaiting: 11 },
        { contactId: 4, name: 'Zoé Leroy', channel: 'LINKEDIN', sentOn: '2026-09-05', daysWaiting: 7 }
      ]
    });

    expect(component.hasReminders()).toBe(true);
    const card = section(element, 'accent-purple');
    expect(card.textContent).toContain('dashboard.charts.followUpsDue');
    expect(rows(card).map(row => row.getAttribute('href'))).toEqual(['/contacts/3', '/contacts/4']);
    expect(rows(card)[0].textContent).toContain('Bob Durand');
  });

  it('lists the overdue invoices first, linking to the invoices', () => {
    const element = render({
      overdueInvoices: [{ invoiceId: 1, number: 'F2026-0001', clientName: 'Capgemini', total: 1500, dueDate: '2026-10-01', daysLate: 26 }]
    });

    expect(component.hasReminders()).toBe(true);
    const card = section(element, 'accent-red');
    expect(element.querySelector('.reminder-card')).toBe(card);
    expect(card.textContent).toContain('F2026-0001');
    expect(card.textContent).toContain('Capgemini');
    expect(rows(card).map(row => row.getAttribute('href'))).toEqual(['/invoices']);
  });

  it('lists the purchase orders near or over their ceiling, linking to their mission', () => {
    const element = render({
      purchaseOrderAlerts: [
        { orderId: 1, projectId: 8, projectRole: 'Architecte', number: 'BC-118', percentUsed: 116, daysLeft: -0.5, exceeded: true },
        { orderId: 2, projectId: 9, projectRole: 'Lead dev', number: 'BC-120', percentUsed: 85, daysLeft: 3, exceeded: false },
        { orderId: 3, projectId: 9, projectRole: 'Lead dev', number: 'BC-121', percentUsed: 80, daysLeft: null, exceeded: false }
      ]
    });

    expect(component.hasReminders()).toBe(true);
    const card = element.querySelector<HTMLElement>('[data-purchase-order-alerts]')!;
    expect(rows(card).map(row => row.getAttribute('href'))).toEqual(['/projects/8', '/projects/9', '/projects/9']);
    expect(rows(card)[0].textContent).toContain('BC-118');
    expect(rows(card)[0].textContent).toContain('purchaseOrders.daysOver');
    expect(rows(card)[1].textContent).toContain('purchaseOrders.daysLeft');
    expect(rows(card)[2].querySelector('.meta')).toBeNull();
  });

  it('lists the training funding requests to send, linking to the training record', () => {
    const element = render({ trainingFundingDue: [{ trainingId: 4, title: 'CKA', deadline: '2026-11-20' }] });

    expect(component.hasReminders()).toBe(true);
    const card = element.querySelector<HTMLElement>('[data-training-funding-due]')!;
    expect(rows(card).map(row => row.getAttribute('href'))).toEqual(['/documents']);
    expect(rows(card)[0].textContent).toContain('CKA');
  });

  it('lists the compliance documents to renew, linking to the vault', () => {
    const element = render({
      expiringDocuments: [
        { documentId: 1, type: 'PROFESSIONAL_INSURANCE', label: 'RC Pro', expiresOn: '2026-10-25', daysLeft: -2 },
        { documentId: 2, type: 'URSSAF_VIGILANCE', label: null, expiresOn: '2026-11-16', daysLeft: 20 }
      ]
    });

    expect(component.hasReminders()).toBe(true);
    const card = element.querySelector<HTMLElement>('[data-expiring-documents]')!;
    expect(rows(card).map(row => row.getAttribute('href'))).toEqual(['/documents', '/documents']);
    expect(rows(card)[0].textContent).toContain('RC Pro');
    expect(rows(card)[0].textContent).toContain('dashboard.charts.expiredDaysAgo');
    expect(rows(card)[1].textContent).toContain('dashboard.charts.expiresInDays');
    expect(rows(card)[1].querySelector('.secondary')).toBeNull();
  });

  it('shows this week\'s goal against the bench, with its progress', () => {
    const element = render({ benchPlan: { projectId: 4, role: 'Lead Java', endDate: '2027-01-04', weeksLeft: 7, activeOpportunities: 2,
      advancedOpportunities: 1, addedThisWeek: 1, daysSinceLastAdded: 9, weeklyGoal: 3 } });
    const card = element.querySelector('[data-bench-plan]') as HTMLElement;
    expect(card).not.toBeNull();
    expect(card.querySelector('.count')?.textContent).toBe('1/3');
    expect(card.textContent).toContain('dashboard.bench.ends');
    expect(card.textContent).toContain('dashboard.bench.quiet');
    expect(card.querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow')).toBe('33');
    expect(component.benchProgress({ addedThisWeek: 5, weeklyGoal: 3 })).toBe(100);
    expect(component.benchProgress({ addedThisWeek: 0, weeklyGoal: 0 })).toBe(100);
    const open = spyOn(TestBed.inject(MatDialog), 'open');
    (card.querySelector('[data-bench-announce]') as HTMLButtonElement).click();
    expect(open).toHaveBeenCalledWith(AiAvailabilityDialogComponent, jasmine.objectContaining({ autoFocus: false }));
    (card.querySelector('[data-bench-prospect]') as HTMLButtonElement).click();
    expect(open).toHaveBeenCalledWith(AiAvailabilityDialogComponent, jasmine.objectContaining({ data: { prospect: true } }));

    const between = render({ benchPlan: { projectId: null, role: null, endDate: null, weeksLeft: 0, activeOpportunities: 0,
      advancedOpportunities: 0, addedThisWeek: 0, daysSinceLastAdded: 2, weeklyGoal: 8 } });
    expect(between.querySelector('[data-bench-plan]')?.textContent).toContain('dashboard.bench.between');
    expect(between.querySelector('[data-bench-plan]')?.textContent).not.toContain('dashboard.bench.quiet');
  });

  it('tells how long the contact\'s company usually takes to answer', () => {
    const element = render({ followUpsDue: [{ contactId: 6, name: 'Paul', clientName: 'Conseil', channel: 'EMAIL', sentOn: '2026-09-28',
      daysWaiting: 5, usualAnswerDays: 3 }, { contactId: 7, name: 'Zoé', channel: 'EMAIL', sentOn: '2026-09-20', daysWaiting: 9 }] });
    const usual = element.querySelectorAll('[data-usual-answer]');
    expect(usual.length).toBe(1);
    expect(usual[0].textContent).toContain('dashboard.charts.usualAnswer');
  });

  it('tells a contact who answered not now is to be written back on the day they gave', () => {
    const element = render({ followUpsDue: [{ contactId: 6, name: 'Paul', channel: 'EMAIL', sentOn: '2026-09-28', daysWaiting: 57,
      writeBackOn: '2026-11-24' }, { contactId: 7, name: 'Zoé', channel: 'EMAIL', sentOn: '2026-09-20', daysWaiting: 9 }] });
    const writeBack = element.querySelectorAll('[data-write-back]');
    expect(writeBack.length).toBe(1);
    expect(writeBack[0].textContent).toContain('dashboard.charts.writeBack');
    expect(element.textContent).toContain('dashboard.charts.waitingSince');
  });

  it('drafts the follow-up of a message about an opportunity', () => {
    const element = render({ followUpsDue: [{ contactId: 6, name: 'Paul', channel: 'EMAIL', sentOn: '2026-09-28', daysWaiting: 5, projectId: 42 },
      { contactId: 7, name: 'Zoé', channel: 'EMAIL', sentOn: '2026-09-20', daysWaiting: 9 }] });
    const open = spyOn(TestBed.inject(MatDialog), 'open');
    expect(element.querySelectorAll('[data-follow-up-draft]').length).toBe(1);
    element.querySelector<HTMLButtonElement>('[data-follow-up-draft="6"]')!.click();
    expect(open).toHaveBeenCalledWith(AiMessageDialogComponent, jasmine.objectContaining({ data: { projectId: 42, kind: 'FOLLOW_UP' } }));
    component.draftFollowUp({ contactId: 7, name: 'Zoé', channel: 'EMAIL', sentOn: '2026-09-20', daysWaiting: 9 });
    expect(open).toHaveBeenCalledTimes(1);
  });

  it('opens an overdue invoice\'s payment reminder, read whole, and says when it cannot be read', () => {
    const element = render({ overdueInvoices: [{ invoiceId: 3, number: 'F2026-0003', clientName: 'Acme', total: 7800, dueDate: '2026-10-31', daysLate: 25,
      remindedOn: '2026-11-10' }] });
    expect(element.querySelector('[data-reminded-on]')!.textContent).toContain('invoices.remindedOn');
    const open = spyOn(TestBed.inject(MatDialog), 'open');
    const invoices = TestBed.inject(InvoiceService);
    const full = { id: 3, number: 'F2026-0003' } as Invoice;
    const get = spyOn(invoices, 'get').and.returnValue(of(full));
    element.querySelector<HTMLButtonElement>('[data-invoice-remind="3"]')!.click();
    expect(get).toHaveBeenCalledWith(3);
    expect(open).toHaveBeenCalledWith(ClientEmailDialogComponent, jasmine.objectContaining({ data: jasmine.objectContaining({ title: 'invoices.reminder.title' }) }));

    const notifications = TestBed.inject(NotificationService);
    spyOn(notifications, 'error');
    get.and.returnValue(throwError(() => new Error('down')));
    element.querySelector<HTMLButtonElement>('[data-invoice-remind="3"]')!.click();
    expect(notifications.error).toHaveBeenCalledWith('dashboard.charts.remindError');
    expect(open).toHaveBeenCalledTimes(1);
  });

  it('suggests the stage an exchange calls for, moving the opportunity or leaving it for good on this device', () => {
    localStorage.removeItem('indezy.dashboard.dismissedStatusSuggestions');
    const projects = TestBed.inject(ProjectService);
    const update = spyOn(projects, 'updateStatus').and.returnValue(of({} as ProjectDto));
    const notifications = TestBed.inject(NotificationService);
    spyOn(notifications, 'success');
    const element = render({ statusSuggestions: [
      { projectId: 4, interactionId: 40, role: 'Lead Java', clientName: 'Conseil', status: ProjectStatus.INTERVIEW, suggested: ProjectStatus.LOST, excerpt: 'Non retenu',
        occurredOn: '2026-11-20' },
      { projectId: 5, interactionId: 50, role: 'Dev', status: ProjectStatus.CONTACT, suggested: ProjectStatus.INTERVIEW, excerpt: 'Un entretien ?', occurredOn: '2026-11-21' }] });
    expect(element.querySelectorAll('[data-status-suggestion]').length).toBe(2);
    element.querySelector<HTMLButtonElement>('[data-status-suggestion="4"] [data-status-suggestion-apply]')!.click();
    expect(update).toHaveBeenCalledWith(4, ProjectStatus.LOST);
    expect(notifications.success).toHaveBeenCalledWith('dashboard.suggestions.applied');
    component.dismissSuggestion(component.getStatusSuggestions()[0]);
    expect(component.getStatusSuggestions()).toEqual([]);
    expect(JSON.parse(localStorage.getItem('indezy.dashboard.dismissedStatusSuggestions')!)).toEqual([50]);
    localStorage.removeItem('indezy.dashboard.dismissedStatusSuggestions');
  });

  it('says when the stage could not be changed', () => {
    render({ statusSuggestions: [{ projectId: 4, interactionId: 41, role: 'Lead', status: ProjectStatus.CONTACT, suggested: ProjectStatus.INTERVIEW, excerpt: 'x',
      occurredOn: '2026-11-20' }] });
    spyOn(TestBed.inject(ProjectService), 'updateStatus').and.returnValue(throwError(() => new Error('down')));
    const error = spyOn(TestBed.inject(NotificationService), 'error');
    component.applySuggestion(component.getStatusSuggestions()[0]);
    expect(error).toHaveBeenCalledWith('dashboard.suggestions.error');
    expect(component.getStatusSuggestions().length).toBe(1);
  });
});

