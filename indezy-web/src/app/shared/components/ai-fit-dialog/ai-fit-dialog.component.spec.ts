import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import en from '../../../../assets/i18n/en.json';
import { AiFit } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { AiFitDialogComponent } from './ai-fit-dialog.component';

describe('AiFitDialogComponent', () => {
  let ai: jasmine.SpyObj<AiService>;
  const fit: AiFit = { score: 78, verdict: 'A good match.', pros: ['Java stack'], cons: [], softCriteria: ['Team size'],
    questions: ['Who leads the team?'], rateAdvice: 'Within the market.', suggestedRate: 700 };

  function create(): ComponentFixture<AiFitDialogComponent> {
    ai = jasmine.createSpyObj('AiService', ['weigh']);
    TestBed.configureTestingModule({
      imports: [AiFitDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: AiService, useValue: ai },
        { provide: MAT_DIALOG_DATA, useValue: { projectId: 42 } }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(AiFitDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('weighs the opportunity with the priorities given and shows each group of points', () => {
    const fixture = create();
    ai.weigh.and.returnValue(of(fit));
    fixture.componentInstance.form.setValue({ priorities: ' Remote first ' });
    fixture.componentInstance.weigh();
    fixture.detectChanges();
    expect(ai.weigh).toHaveBeenCalledWith(42, 'EN', 'Remote first');
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-ai-fit-score]')?.classList).toContain('good');
    expect(element.querySelector('[data-ai-fit-group="pros"]')?.textContent).toContain('Java stack');
    expect(element.querySelector('[data-ai-fit-group="cons"]')).toBeNull();
    expect(element.querySelector('[data-ai-fit-rate]')?.textContent).toContain('Rate to ask: 700 € a day');
    expect(fixture.componentInstance.band(50)).toBe('mixed');
    expect(fixture.componentInstance.band(10)).toBe('poor');
  });

  it('says why the opportunity could not be weighed', () => {
    const fixture = create();
    ai.weigh.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503, error: { message: 'The AI service is unavailable for now' } })));
    fixture.componentInstance.weigh();
    expect(fixture.componentInstance.errorKey).toBe('ai.errors.failed');
    expect(fixture.componentInstance.weighing).toBeFalse();
  });
});
