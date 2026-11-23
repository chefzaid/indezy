import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';

import { CompanyScorecard } from '../../../models/client.models';
import { ClientService } from '../../../services/client/client.service';
import { CompanyScorecardComponent } from './company-scorecard.component';
import en from '../../../../assets/i18n/en.json';

describe('CompanyScorecardComponent', () => {
  const full: CompanyScorecard = { opportunities: 4, open: 2, won: 1, offerRate: 67, processDays: 15, processes: 2, marginMin: 20,
    marginMax: 29, margins: 2, answerDays: 3 };

  function show(clientId: number | null, answer: Observable<CompanyScorecard> = of(full)): HTMLElement {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [CompanyScorecardComponent, TranslateModule.forRoot()],
      providers: [{ provide: ClientService, useValue: { getScorecard: () => answer } }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(CompanyScorecardComponent);
    fixture.componentRef.setInput('clientId', clientId);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('sums up the opportunities with the company, each figure once backed', () => {
    const element = show(3);
    expect(element.querySelector('[data-scorecard-opportunities]')!.textContent).toContain('4 opportunities, 1 won, 2 open');
    expect(element.querySelector('[data-scorecard-offer-rate]')!.textContent).toContain('67 % of those past the interviews');
    expect(element.querySelector('[data-scorecard-process]')!.textContent).toContain('15 days to the outcome (median of 2)');
    expect(element.querySelector('[data-scorecard-margin]')!.textContent).toContain("20 to 29 % of the client's rate (2 missions)");
    expect(element.querySelector('[data-scorecard-answer]')!.textContent).toContain('in 3 days usually');

    const sparse = show(3, of({ ...full, offerRate: null, processDays: null, marginMin: 20, marginMax: 20, margins: 1, answerDays: null }));
    expect(sparse.querySelector('[data-scorecard-offer-rate]')).toBeNull();
    expect(sparse.querySelector('[data-scorecard-process]')).toBeNull();
    expect(sparse.querySelector('[data-scorecard-answer]')).toBeNull();
    expect(sparse.querySelector('[data-scorecard-margin]')!.textContent).toContain("20 % of the client's rate (1 mission)");
  });

  it('shows nothing without an opportunity, a company or an answer', () => {
    expect(show(3, of({ ...full, opportunities: 0 })).querySelector('[data-company-scorecard]')).toBeNull();
    expect(show(null).querySelector('[data-company-scorecard]')).toBeNull();
    expect(show(3, throwError(() => new Error('500'))).querySelector('[data-company-scorecard]')).toBeNull();
  });
});
