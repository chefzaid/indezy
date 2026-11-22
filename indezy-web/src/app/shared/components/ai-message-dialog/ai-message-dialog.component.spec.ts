import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Clipboard } from '@angular/cdk/clipboard';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { AiService } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { AiMessageDialogComponent } from './ai-message-dialog.component';

describe('AiMessageDialogComponent', () => {
  let ai: jasmine.SpyObj<AiService>;
  let clipboard: jasmine.SpyObj<Clipboard>;
  let notifications: jasmine.SpyObj<NotificationService>;

  function create(): AiMessageDialogComponent {
    ai = jasmine.createSpyObj('AiService', ['draftMessage']);
    clipboard = jasmine.createSpyObj('Clipboard', ['copy']);
    clipboard.copy.and.returnValue(true);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [AiMessageDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: AiService, useValue: ai },
        { provide: MAT_DIALOG_DATA, useValue: { projectId: 42, kind: 'APPLICATION' } }, { provide: Clipboard, useValue: clipboard },
        { provide: NotificationService, useValue: notifications }]
    });
    const fixture = TestBed.createComponent(AiMessageDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('drafts the message asked, editable, then copies it with its subject', () => {
    const component = create();
    expect(component.form.value.kind).toBe('APPLICATION');
    ai.draftMessage.and.returnValue(of({ subject: 'Mission Java', body: 'Bonjour,\n\nAlice' }));
    component.form.patchValue({ language: 'FR', notes: ' dispo en janvier ' });
    component.write();
    expect(ai.draftMessage).toHaveBeenCalledWith(42, 'APPLICATION', 'FR', 'dispo en janvier');
    expect(component.drafted).toBeTrue();
    component.draft.patchValue({ body: 'Bonjour,\n\nAlice M.' });
    component.copy();
    expect(clipboard.copy).toHaveBeenCalledWith('Mission Java\n\nBonjour,\n\nAlice M.');
    expect(notifications.success).toHaveBeenCalledWith('ai.message.copied');
  });

  it('says why no message could be drafted', () => {
    const component = create();
    ai.draftMessage.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'The AI declined this request' } })));
    component.write();
    expect(component.errorKey).toBe('ai.errors.declined');
    expect(component.drafted).toBeFalse();
    expect(component.writing).toBeFalse();
  });
});
