import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { AiService } from '../../../services/ai/ai.service';
import { PostingDossierDialogComponent } from './posting-dossier-dialog.component';

describe('PostingDossierDialogComponent', () => {
  let ai: jasmine.SpyObj<AiService>;

  function create(): PostingDossierDialogComponent {
    ai = jasmine.createSpyObj('AiService', ['postingDossier', 'postingPdfText']);
    TestBed.configureTestingModule({
      imports: [PostingDossierDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: AiService, useValue: ai }]
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

  it('downloads the dossier tailored to the posting pasted, in the layout and language chosen', () => {
    const component = create();
    const link = document.createElement('a');
    spyOn(document, 'createElement').and.returnValue(link);
    spyOn(link, 'click');
    ai.postingDossier.and.returnValue(of(new Blob(['%PDF'])));

    component.make();
    expect(ai.postingDossier).not.toHaveBeenCalled();
    component.text.setValue('  Mission Kafka, 12 mois. ');
    component.layout = 'ANONYMOUS';
    component.language = 'EN';
    component.make();
    expect(ai.postingDossier).toHaveBeenCalledWith('Mission Kafka, 12 mois.', 'ANONYMOUS', 'EN');
    expect(link.download).toBe('skills-profile-anonymous-tailored.pdf');
    expect(component.making).toBeFalse();
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

  it('says why the AI could not tailor the dossier', async () => {
    const component = create();
    component.text.setValue('Mission Kafka');
    ai.postingDossier.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409,
      error: new Blob(['{"message":"The AI declined this request"}']) })));
    component.make();
    await new Promise(resolve => setTimeout(resolve, 20));
    expect(component.errorKey).toBe('ai.errors.declined');
  });
});
