import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import en from '../../../../assets/i18n/en.json';
import { AiStatus } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { AiSettingsComponent } from './ai-settings.component';

describe('AiSettingsComponent', () => {
  let ai: jasmine.SpyObj<AiService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const indezy: AiStatus = { available: true, reason: null, ownKey: false, model: 'claude-opus-5-5', spentUsd: 0.5, limitUsd: 2 };

  function create(status: AiStatus = indezy): ComponentFixture<AiSettingsComponent> {
    ai = jasmine.createSpyObj('AiService', ['status', 'setKey', 'removeKey']);
    ai.status.and.returnValue(of(status));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [AiSettingsComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: AiService, useValue: ai },
        { provide: NotificationService, useValue: notifications }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(AiSettingsComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the allowance used on Indezy\'s key, and sets the member\'s own key', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-ai-allowance]')?.textContent).toContain('This month: $0.50 of $2.00');
    expect(fixture.componentInstance.used).toBe(25);
    const component = fixture.componentInstance;
    component.form.patchValue({ apiKey: 'not-a-key' });
    component.saveKey();
    expect(ai.setKey).not.toHaveBeenCalled();
    ai.setKey.and.returnValue(of({ ...indezy, ownKey: true, model: 'claude-haiku-5-5' }));
    component.form.patchValue({ apiKey: ' sk-ant-abc ', model: 'claude-haiku-5-5' });
    component.form.controls.apiKey.setValue('sk-ant-abc');
    component.saveKey();
    expect(ai.setKey).toHaveBeenCalledWith('sk-ant-abc', 'claude-haiku-5-5');
    expect(notifications.success).toHaveBeenCalledWith('ai.settings.keySaved');
    fixture.detectChanges();
    expect(element.querySelector('[data-ai-state="available"]')?.textContent).toContain('your own Anthropic key, with Claude Haiku 5.5');
    expect(component.form.value.apiKey).toBe('');
  });

  it('removes the own key, and says why AI is not available', () => {
    const fixture = create({ ...indezy, available: false, reason: 'NO_KEY', spentUsd: 0 });
    expect(fixture.nativeElement.querySelector('[data-ai-state="NO_KEY"]')?.textContent).toContain('not available on this server yet');
    expect(fixture.nativeElement.querySelector('[data-ai-allowance]')).toBeNull();
    const component = fixture.componentInstance;
    ai.removeKey.and.returnValue(of(indezy));
    component.removeKey();
    expect(notifications.success).toHaveBeenCalledWith('ai.settings.keyRemoved');
    ai.setKey.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
    component.form.setValue({ apiKey: 'sk-ant-x', model: 'claude-opus-5-5' });
    component.saveKey();
    expect(notifications.error).toHaveBeenCalledWith('ai.settings.keyInvalid');
    expect(component.saving).toBeFalse();
  });
});
