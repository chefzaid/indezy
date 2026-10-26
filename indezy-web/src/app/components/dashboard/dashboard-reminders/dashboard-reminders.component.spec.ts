import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { DashboardRemindersComponent } from './dashboard-reminders.component';
import { DashboardStatsDto, OnThisDayItem } from '../../../models';

describe('DashboardRemindersComponent', () => {
  let fixture: ComponentFixture<DashboardRemindersComponent>;
  let component: DashboardRemindersComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardRemindersComponent, RouterModule.forRoot([]), TranslateModule.forRoot()]
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
});
