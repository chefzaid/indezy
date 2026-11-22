import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { AiPosting } from '../../../../models';
import { AiService } from '../../../../services/ai/ai.service';
import { AiPostingDialogComponent } from './ai-posting-dialog.component';

describe('AiPostingDialogComponent', () => {
  let ai: jasmine.SpyObj<AiService>;
  let close: jasmine.Spy;
  const posting = { role: 'Java lead', techStack: ['Java'] } as AiPosting;

  function create(): AiPostingDialogComponent {
    ai = jasmine.createSpyObj('AiService', ['readPosting', 'postingPdfText']);
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [AiPostingDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: AiService, useValue: ai },
        { provide: MatDialogRef, useValue: { close } }]
    });
    const fixture = TestBed.createComponent(AiPostingDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('reads the pasted posting and closes with it and its text', () => {
    const component = create();
    component.read();
    expect(ai.readPosting).not.toHaveBeenCalled();
    ai.readPosting.and.returnValue(of(posting));
    component.text.setValue('  Mission Java lead  ');
    component.read();
    expect(ai.readPosting).toHaveBeenCalledWith('Mission Java lead');
    expect(close).toHaveBeenCalledWith({ posting, text: 'Mission Java lead' });
  });

  it('says why the posting could not be read, and stays open', () => {
    const component = create();
    ai.readPosting.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'AI is not available yet' } })));
    component.text.setValue('Mission');
    component.read();
    expect(component.errorKey).toBe('ai.errors.unavailable');
    expect(component.reading).toBeFalse();
    expect(close).not.toHaveBeenCalled();
  });

  function choose(component: AiPostingDialogComponent, file: File | null): void {
    const input = document.createElement('input');
    input.type = 'file';
    if (file) {
      const files = new DataTransfer();
      files.items.add(file);
      input.files = files.files;
    }
    component.fromPdf({ target: input } as unknown as Event);
  }

  it('puts the text of a PDF in the box to check before reading it', () => {
    const component = create();
    ai.postingPdfText.and.returnValue(of({ text: 'Mission Kafka', cut: true }));
    choose(component, new File(['%PDF'], 'annonce.pdf', { type: 'application/pdf' }));
    expect(ai.postingPdfText).toHaveBeenCalledTimes(1);
    expect(component.text.value).toBe('Mission Kafka');
    expect(component.pdfCut).toBeTrue();
    expect(ai.readPosting).not.toHaveBeenCalled();
    choose(component, null);
    expect(ai.postingPdfText).toHaveBeenCalledTimes(1);
  });

  it('says why a PDF could not be read', () => {
    const component = create();
    choose(component, new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.pdf'));
    expect(component.errorKey).toBe('ai.posting.pdfTooLarge');
    expect(ai.postingPdfText).not.toHaveBeenCalled();
    const refused = (file: string) => throwError(() => new HttpErrorResponse({ status: 400, error: { validationErrors: { file } } }));
    ai.postingPdfText.and.returnValue(refused('This PDF is protected: save it without a password'));
    choose(component, new File(['%PDF'], 'a.pdf'));
    expect(component.errorKey).toBe('ai.posting.pdfProtected');
    ai.postingPdfText.and.returnValue(refused('This PDF has no text to read: it may be a scanned page'));
    choose(component, new File(['%PDF'], 'a.pdf'));
    expect(component.errorKey).toBe('ai.posting.pdfNoText');
    ai.postingPdfText.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    choose(component, new File(['%PDF'], 'a.pdf'));
    expect(component.errorKey).toBe('ai.posting.pdfUnreadable');
    expect(component.loadingPdf).toBeFalse();
  });
});
