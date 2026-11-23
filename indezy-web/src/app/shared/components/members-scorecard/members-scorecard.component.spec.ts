import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';

import { EsnFigures, EsnScorecardService } from '../../../services/esn-scorecard/esn-scorecard.service';
import { MembersScorecardComponent } from './members-scorecard.component';

describe('MembersScorecardComponent', () => {
  const shown: EsnFigures = { company: 'Conseil Exemple', freelancers: 6, threshold: 5, offerRate: 40, processDays: 21, marginLow: 18,
    marginHigh: 25, answerDays: 3, paymentDays: null, sharing: true };

  function create(answer: Observable<EsnFigures>): HTMLElement {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [MembersScorecardComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: EsnScorecardService, useValue: { company: () => answer } }]
    });
    const fixture = TestBed.createComponent(MembersScorecardComponent);
    fixture.componentRef.setInput('clientId', 4);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the figures five freelancers back, and those still too few', () => {
    const element = create(of(shown));
    expect(element.querySelector('[data-esn-offer-rate]')!.textContent).toContain('40 %');
    expect(element.querySelector('[data-esn-margin]')).not.toBeNull();
    expect(element.querySelector('[data-esn-payment]')).toBeNull();
    expect(create(of({ ...shown, freelancers: 2, offerRate: null })).querySelector('[data-members-scorecard-too-few]')).not.toBeNull();
  });

  it('stays away for an ESN no member dealt with, or outside the network', () => {
    expect(create(of({ ...shown, freelancers: 0 })).querySelector('[data-members-scorecard]')).toBeNull();
    expect(create(throwError(() => new Error('409'))).querySelector('[data-members-scorecard]')).toBeNull();
  });
});
