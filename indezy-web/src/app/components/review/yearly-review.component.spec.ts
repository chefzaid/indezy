import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NEVER, Subject, of, throwError } from 'rxjs';
import { RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { YearlyReviewComponent } from './yearly-review.component';
import { YearlyReviewService } from '../../services/review/yearly-review.service';
import { AuthService } from '../../services/auth/auth.service';
import { NotificationService } from '../../services/notification/notification.service';
import { User } from '../../models/auth.models';
import { YearlyReview } from '../../models';

describe('YearlyReviewComponent', () => {
  let fixture: ComponentFixture<YearlyReviewComponent>;
  let component: YearlyReviewComponent;
  let reviews: jasmine.SpyObj<YearlyReviewService>;
  let auth: jasmine.SpyObj<AuthService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const thisYear = new Date().getFullYear();

  const review = (over: Partial<YearlyReview> = {}): YearlyReview => ({
    year: thisYear,
    name: 'Alice Martin',
    missions: [
      { projectId: 1, role: 'Java Lead', clientName: 'Acme', dailyRate: 600, start: `${thisYear}-01-01`, end: `${thisYear}-06-30`,
        days: 100, reported: true, revenue: 60000 },
      { projectId: 2, role: 'Kafka Dev', dailyRate: 700, start: `${thisYear}-07-01`, days: 50.5, reported: false, revenue: 35350 }
    ],
    totals: { revenue: 95350, days: 150.5, averageDailyRate: 634, missions: 2 },
    previousYear: { revenue: 80000, days: 140, averageDailyRate: 571, missions: 1 },
    pipeline: { opened: 6, won: 2, lost: 2, conversionRate: 50, lostReasons: [{ reason: 'RATE_TOO_LOW', count: 2 }] },
    topSkills: [{ skill: 'Java', count: 4 }, { skill: 'Kafka', count: 2 }],
    network: { clients: 2, esns: 1, contacts: 5, exchanges: 12 },
    ...over
  });

  const empty = (): YearlyReview => review({
    missions: [],
    totals: { revenue: 0, days: 0, missions: 0 },
    previousYear: { revenue: 0, days: 0, missions: 0 },
    pipeline: { opened: 0, won: 0, lost: 0, lostReasons: [] },
    topSkills: [],
    network: { clients: 0, esns: 0, contacts: 0, exchanges: 0 }
  });

  const text = (): string => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const query = <T extends Element>(selector: string): T | null => (fixture.nativeElement as HTMLElement).querySelector<T>(selector);

  beforeEach(async () => {
    reviews = jasmine.createSpyObj<YearlyReviewService>('YearlyReviewService', ['get', 'downloadPdf']);
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['getUser']);
    notifications = jasmine.createSpyObj<NotificationService>('NotificationService', ['success', 'error']);
    auth.getUser.and.returnValue({ id: 7 } as User);
    reviews.get.and.callFake((_id, year) => of(review({ year })));

    await TestBed.configureTestingModule({
      imports: [YearlyReviewComponent, RouterModule.forRoot([]), TranslateModule.forRoot()],
      providers: [
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: YearlyReviewService, useValue: reviews },
        { provide: AuthService, useValue: auth },
        { provide: NotificationService, useValue: notifications }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(YearlyReviewComponent);
    component = fixture.componentInstance;
  });

  it('shows the current year: figures, missions with their estimates, pipeline, skills and network', () => {
    fixture.detectChanges();

    expect(reviews.get).toHaveBeenCalledWith(7, thisYear);
    expect(query('[data-year]')?.textContent?.trim()).toBe(String(thisYear));
    expect(component.revenueChange).toBe(19);
    expect(query('[data-figure-revenue]')?.textContent).toContain('95,350 €');
    expect(query('[data-figure-days]')?.textContent).toContain('150.5');
    const rows = query('[data-missions]')!.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Acme');
    expect(rows[0].querySelector('.estimate')).toBeNull();
    expect(rows[1].querySelector('.estimate')).not.toBeNull();
    expect(rows[1].textContent).toContain('review.ongoing');
    expect(query('[data-estimate-note]')).not.toBeNull();
    expect(query('[data-pipeline]')?.textContent).toContain('50 %');
    expect(text()).toContain('projects.lostReason.RATE_TOO_LOW');
    expect(query('[data-skills]')?.textContent).toContain('Java');
    expect(query('[data-network]')?.textContent).toContain('12');
    expect(query('[data-review-empty]')).toBeNull();
  });

  it('moves between years without going past the current one', () => {
    fixture.detectChanges();
    const next = query<HTMLButtonElement>('[data-year-next]')!;
    expect(next.disabled).toBeTrue();
    component.changeYear(1);
    expect(reviews.get).toHaveBeenCalledTimes(1);

    query<HTMLButtonElement>('[data-year-previous]')!.click();
    fixture.detectChanges();

    expect(component.year).toBe(thisYear - 1);
    expect(reviews.get).toHaveBeenCalledWith(7, thisYear - 1);
    expect(query<HTMLButtonElement>('[data-year-next]')!.disabled).toBeFalse();

    component.year = component.firstYear;
    component.changeYear(-1);
    expect(component.year).toBe(component.firstYear);
  });

  it('keeps the year asked for last when an earlier answer comes late', () => {
    const late = new Subject<YearlyReview>();
    reviews.get.and.returnValues(late, of(review({ year: thisYear - 1, name: 'Last year' })));
    fixture.detectChanges();

    component.changeYear(-1);
    late.next(review({ name: 'Too late' }));

    expect(component.review?.name).toBe('Last year');
  });

  it('says when the year holds nothing, without a comparison or estimates', () => {
    reviews.get.and.returnValue(of(empty()));
    fixture.detectChanges();

    expect(component.isEmpty).toBeTrue();
    expect(query('[data-review-empty]')).not.toBeNull();
    expect(component.revenueChange).toBeUndefined();
    expect(text()).toContain('review.noComparison');
    expect(text()).toContain('review.noMissions');
    expect(text()).toContain('review.noSkills');
    expect(query('[data-estimate-note]')).toBeNull();
  });

  it('reports a review that cannot be loaded, and loads nothing without a user', () => {
    reviews.get.and.returnValue(throwError(() => new Error('down')));
    fixture.detectChanges();
    expect(component.review).toBeUndefined();
    expect(component.isLoading).toBeFalse();
    expect(notifications.error).toHaveBeenCalledWith('review.loadError');

    reviews.get.calls.reset();
    auth.getUser.and.returnValue(null);
    const other = TestBed.createComponent(YearlyReviewComponent);
    other.detectChanges();
    expect(reviews.get).not.toHaveBeenCalled();
    other.componentInstance.downloadPdf();
    expect(reviews.downloadPdf).not.toHaveBeenCalled();
  });

  it('downloads the PDF in the language of the interface', () => {
    const translate = TestBed.inject(TranslateService);
    const anchors: HTMLAnchorElement[] = [];
    const create = document.createElement.bind(document);
    spyOn(document, 'createElement').and.callFake((tag: string) => {
      const element = create(tag);
      if (tag === 'a') {
        spyOn(element as HTMLAnchorElement, 'click');
        anchors.push(element as HTMLAnchorElement);
      }
      return element;
    });
    reviews.downloadPdf.and.returnValue(of(new Blob(['%PDF'])));
    fixture.detectChanges();
    // The mission links rendered above are anchors too: keep only the downloads'.
    anchors.length = 0;

    spyOn(translate, 'getCurrentLang').and.returnValue('en');
    component.downloadPdf();
    expect(reviews.downloadPdf).toHaveBeenCalledWith(7, thisYear, 'EN');
    expect(anchors[0].download).toBe(`indezy-year-in-review-${thisYear}.pdf`);

    (translate.getCurrentLang as jasmine.Spy).and.returnValue('fr');
    component.downloadPdf();
    expect(reviews.downloadPdf).toHaveBeenCalledWith(7, thisYear, 'FR');
    expect(anchors[1].download).toBe(`indezy-bilan-${thisYear}.pdf`);
    expect(component.isDownloading).toBeFalse();
  });

  it('downloads one PDF at a time and reports a failure', () => {
    reviews.downloadPdf.and.returnValue(NEVER);
    fixture.detectChanges();
    component.downloadPdf();
    component.downloadPdf();
    expect(reviews.downloadPdf).toHaveBeenCalledTimes(1);

    component.isDownloading = false;
    reviews.downloadPdf.and.returnValue(throwError(() => new Error('down')));
    component.downloadPdf();
    expect(component.isDownloading).toBeFalse();
    expect(notifications.error).toHaveBeenCalledWith('review.pdfError');
  });
});
