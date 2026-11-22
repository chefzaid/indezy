import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { AiService } from '../../../services/ai/ai.service';
import { OfferBriefDialogComponent, toFilters } from './offer-brief-dialog.component';

describe('OfferBriefDialogComponent', () => {
  const brief = { skills: ['Java', 'Kafka'], rateMin: 650, minRemoteDays: 2, workModes: ['HYBRID' as const], regions: ['11'],
    startBefore: '2027-03-01', durationMin: null, q: null, avoid: ['banque'] };

  function create() {
    const ai = jasmine.createSpyObj('AiService', ['offerBrief']);
    const close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [OfferBriefDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: AiService, useValue: ai },
        { provide: MatDialogRef, useValue: { close } }]
    });
    const fixture = TestBed.createComponent(OfferBriefDialogComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, ai, close };
  }

  it('reads the brief into the feed filters and closes with them and what to avoid', () => {
    const { component, ai, close } = create();
    component.read();
    expect(ai.offerBrief).not.toHaveBeenCalled();
    ai.offerBrief.and.returnValue(of(brief));
    component.text.setValue(' Java Kafka, 650 € ');
    component.read();
    expect(ai.offerBrief).toHaveBeenCalledWith('Java Kafka, 650 €');
    const result = close.calls.mostRecent().args[0];
    expect(result.filters).toEqual(jasmine.objectContaining({ skills: ['Java', 'Kafka'], rateMin: 650, minRemoteDays: 2, regions: ['11'],
      startBefore: '2027-03-01', durationMin: undefined, q: undefined, exclude: 'banque' }));
    expect(result.avoid).toEqual(['banque']);
  });

  it('says why the brief could not be read', () => {
    const { component, ai, close } = create();
    ai.offerBrief.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'AI is not available yet' } })));
    component.text.setValue('Java');
    component.read();
    expect(component.errorKey).toBe('ai.errors.unavailable');
    expect(component.reading).toBeFalse();
    expect(close).not.toHaveBeenCalled();
    expect(toFilters({ ...brief, rateMin: null, workModes: [], avoid: [] }).rateMin).toBeUndefined();
    expect(toFilters({ ...brief, avoid: [] }).exclude).toBeUndefined();
  });
});
