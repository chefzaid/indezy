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
    ai = jasmine.createSpyObj('AiService', ['readPosting']);
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
});
