import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { AiOfferVerdict } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { OfferVerdictComponent } from './offer-verdict.component';

describe('OfferVerdictComponent', () => {
  let ai: jasmine.SpyObj<AiService>;
  const verdict: AiOfferVerdict = { offerId: 9, goodFit: false, verdict: 'On site every day.', dealBreakers: ['5 days on site'],
    createdAt: '2026-11-22T10:00:00' };

  function create(kept: AiOfferVerdict | null = null): ComponentFixture<OfferVerdictComponent> {
    ai = jasmine.createSpyObj('AiService', ['offerVerdict', 'triageOffer']);
    ai.offerVerdict.and.returnValue(of(kept));
    TestBed.configureTestingModule({
      imports: [OfferVerdictComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: AiService, useValue: ai }]
    });
    const fixture = TestBed.createComponent(OfferVerdictComponent);
    fixture.componentInstance.offerId = 9;
    fixture.componentInstance.ngOnChanges();
    fixture.detectChanges();
    return fixture;
  }

  it('shows the verdict kept, with its deal-breakers', () => {
    const fixture = create(verdict);
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-offer-verdict-fit="false"]')?.textContent).toContain('On site every day.');
    expect(element.querySelector('[data-offer-deal-breakers]')?.textContent).toContain('5 days on site');
  });

  it('asks for a verdict, and says why it could not', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    expect(component.verdict).toBeNull();
    ai.triageOffer.and.returnValue(of({ ...verdict, goodFit: true, dealBreakers: [] }));
    component.ask();
    expect(ai.triageOffer).toHaveBeenCalledWith(9, 'FR');
    expect(component.verdict?.goodFit).toBeTrue();
    ai.triageOffer.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Your AI allowance for this month is used up' } })));
    component.ask();
    expect(component.errorKey).toBe('ai.errors.accountLimit');
    expect(component.asking).toBeFalse();
  });
});
