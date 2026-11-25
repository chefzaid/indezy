import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { AiChatTurn } from '../../../../models';
import { AiService } from '../../../../services/ai/ai.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { AiChatComponent } from './ai-chat.component';

describe('AiChatComponent', () => {
  let ai: jasmine.SpyObj<AiService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  const turns: AiChatTurn[] = [{ id: 1, fromMember: true, text: 'How to prepare?', createdAt: '2026-11-22T10:00:00' },
    { id: 2, fromMember: false, text: 'Bring three examples.', createdAt: '2026-11-22T10:00:05' }];

  function create(history: AiChatTurn[] = []): ComponentFixture<AiChatComponent> {
    ai = jasmine.createSpyObj('AiService', ['chatHistory', 'ask', 'clearChat']);
    ai.chatHistory.and.returnValue(of(history));
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    TestBed.configureTestingModule({
      imports: [AiChatComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: AiService, useValue: ai },
        { provide: ConfirmDialogService, useValue: confirm }]
    });
    const fixture = TestBed.createComponent(AiChatComponent);
    fixture.componentInstance.projectId = 42;
    fixture.componentInstance.ngOnChanges();
    fixture.detectChanges();
    return fixture;
  }

  it('opens on a conversation already started and asks the next question', () => {
    const fixture = create(turns);
    const component = fixture.componentInstance;
    expect(component.open).toBeTrue();
    expect(fixture.nativeElement.querySelectorAll('[data-ai-chat-turn]').length).toBe(2);
    ai.ask.and.returnValue(of([...turns, { id: 3, fromMember: true, text: 'And the rate?', createdAt: '' }]));
    component.question.setValue(' And the rate? ');
    component.ask();
    expect(ai.ask).toHaveBeenCalledWith(42, 'And the rate?');
    expect(component.turns.length).toBe(3);
    expect(component.question.value).toBe('');
  });

  it('starts closed, suggests questions, says why one failed and clears once confirmed', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    expect(component.open).toBeFalse();
    component.open = true;
    component.suggest('interview');
    ai.ask.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'AI is not available yet' } })));
    component.ask();
    expect(component.errorKey).toBe('ai.errors.unavailable');
    expect(component.question.value).toBe('ai.chat.suggestions.interview');
    component.turns = turns;
    confirm.confirm.and.returnValue(of(true));
    ai.clearChat.and.returnValue(of(undefined));
    component.clear();
    expect(ai.clearChat).toHaveBeenCalledWith(42);
    expect(component.turns).toEqual([]);
    // Not cleared: the conversation stays, and it is said.
    component.turns = turns;
    ai.clearChat.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    component.clear();
    expect(component.turns).toEqual(turns);
    expect(component.errorKey).toBe('ai.chat.clearError');
  });
});
