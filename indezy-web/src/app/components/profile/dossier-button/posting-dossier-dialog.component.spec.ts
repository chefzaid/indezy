import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { AiCvRewrite } from '../../../models';
import { of, throwError } from 'rxjs';
import { AiService } from '../../../services/ai/ai.service';
import { PostingDossierDialogComponent } from './posting-dossier-dialog.component';

describe('PostingDossierDialogComponent', () => {
  let ai: jasmine.SpyObj<AiService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<PostingDossierDialogComponent>>;

  function create(): PostingDossierDialogComponent {
    ai = jasmine.createSpyObj('AiService', ['reviewPostingDossier', 'postingPdfText']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    TestBed.configureTestingModule({
      imports: [PostingDossierDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: AiService, useValue: ai }, { provide: MatDialogRef, useValue: dialogRef }]
    });
    const fixture = TestBed.createComponent(PostingDossierDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  function pdfEvent(file: File): Event {
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', { value: [file] });
    return { target: input } as unknown as Event;
  }

  it('has the AI tailor the dossier to the posting pasted, in the language chosen, then gives it back to review', () => {
    const component = create();
    const rewrite = { skills: [] } as unknown as AiCvRewrite;
    ai.reviewPostingDossier.and.returnValue(of(rewrite));

    component.make();
    expect(ai.reviewPostingDossier).not.toHaveBeenCalled();
    component.text.setValue('  Mission Kafka, 12 mois. ');
    component.language = 'EN';
    component.make();
    expect(ai.reviewPostingDossier).toHaveBeenCalledWith('Mission Kafka, 12 mois.', 'EN');
    expect(dialogRef.close).toHaveBeenCalledWith({ rewrite, language: 'EN' });
  });

  it('reads the posting from its PDF, and says why one cannot be read', () => {
    const component = create();
    ai.postingPdfText.and.returnValue(of({ text: 'Mission Kafka', cut: true }));
    component.fromPdf(pdfEvent(new File(['%PDF'], 'posting.pdf')));
    expect(component.text.value).toBe('Mission Kafka');
    expect(component.pdfCut).toBeTrue();

    ai.postingPdfText.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'The PDF is protected' } })));
    component.fromPdf(pdfEvent(new File(['%PDF'], 'posting.pdf')));
    expect(component.errorKey).toBe('ai.posting.pdfProtected');

    component.fromPdf(pdfEvent(new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.pdf')));
    expect(component.errorKey).toBe('ai.posting.pdfTooLarge');
  });

  it('says why the AI could not tailor the dossier', () => {
    const component = create();
    component.text.setValue('Mission Kafka');
    ai.reviewPostingDossier.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409,
      error: { message: 'The AI declined this request' } })));
    component.make();
    expect(component.errorKey).toBe('ai.errors.declined');
    expect(component.making).toBeFalse();
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
