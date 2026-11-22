import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { AiService } from '../../../services/ai/ai.service';
import { AiNegotiationDialogComponent } from './ai-negotiation-dialog.component';

describe('AiNegotiationDialogComponent', () => {
  let ai: jasmine.SpyObj<AiService>;

  function create(setup: () => void) {
    ai = jasmine.createSpyObj('AiService', ['negotiation']);
    setup();
    TestBed.configureTestingModule({
      imports: [AiNegotiationDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: AiService, useValue: ai },
        { provide: MAT_DIALOG_DATA, useValue: { projectId: 9 } }]
    });
    const fixture = TestBed.createComponent(AiNegotiationDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('prepares the negotiation as it opens', () => {
    const fixture = create(() => ai.negotiation.and.returnValue(of({ counterOffer: 680, walkAway: 620, strategy: 'Ancrer haut.',
      arguments: ['Kafka rare'], marginEuros: 150, marginPercent: 20, marketMedian: 640 })));
    const element: HTMLElement = fixture.nativeElement;
    expect(ai.negotiation).toHaveBeenCalledWith(9, 'FR');
    expect(element.querySelector('[data-ai-negotiation-counter]')?.textContent).toContain('680');
    expect(element.textContent).toContain('150');
    expect(element.textContent).toContain('Kafka rare');
  });

  it('says why it could not, and leaves out the margin without a client rate', () => {
    const fixture = create(() => ai.negotiation.and.returnValue(throwError(() =>
      new HttpErrorResponse({ status: 409, error: { message: 'AI is not available yet' } }))));
    expect(fixture.componentInstance.errorKey).toBe('ai.errors.unavailable');
    ai.negotiation.and.returnValue(of({ counterOffer: 600, walkAway: 550, strategy: '', arguments: [], marginEuros: null,
      marginPercent: null, marketMedian: 0 }));
    fixture.componentInstance.prepare();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('ai.negotiation.margin');
    expect(fixture.nativeElement.textContent).not.toContain('ai.negotiation.market');
  });
});
