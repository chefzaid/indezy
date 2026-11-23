import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';

import { EsnBoard, EsnFigures, EsnScorecardService } from '../../../services/esn-scorecard/esn-scorecard.service';
import { ESNS_SHOWN, EsnScorecardsCardComponent } from './esn-scorecards-card.component';

describe('EsnScorecardsCardComponent', () => {
  const esn = (index: number): EsnFigures => ({ company: 'ESN ' + index, freelancers: 9 - index, threshold: 5, offerRate: 30 + index,
    processDays: null, marginLow: null, marginHigh: null, answerDays: 2, paymentDays: 45, sharing: false });

  function create(board: EsnBoard): HTMLElement {
    return fixture(board).nativeElement as HTMLElement;
  }

  function fixture(board: EsnBoard): ComponentFixture<EsnScorecardsCardComponent> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [EsnScorecardsCardComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: EsnScorecardService, useValue: { board: () => of(board) } }]
    });
    const created = TestBed.createComponent(EsnScorecardsCardComponent);
    created.detectChanges();
    return created;
  }

  it('lists the most backed ESNs first, the others on demand', () => {
    const card = fixture({ companies: [0, 1, 2, 3].map(esn), threshold: 5, sharing: false });
    const element = card.nativeElement as HTMLElement;
    expect(element.querySelectorAll('[data-esn-scorecard]').length).toBe(ESNS_SHOWN);
    expect(element.querySelector('[data-esn-scorecard="ESN 0"] [data-esn-offer-rate]')!.textContent).toContain('30 %');
    (element.querySelector('[data-esn-scorecards-all]') as HTMLButtonElement).click();
    card.debugElement.injector.get(ChangeDetectorRef).markForCheck();
    card.detectChanges();
    expect(element.querySelectorAll('[data-esn-scorecard]').length).toBe(4);
    expect(element.querySelector('[data-esn-scorecards-share]')).not.toBeNull();
  });

  it('says when no ESN is backed enough yet', () => {
    expect(create({ companies: [], threshold: 5, sharing: true }).querySelector('[data-esn-scorecards-empty]')).not.toBeNull();
  });
});
