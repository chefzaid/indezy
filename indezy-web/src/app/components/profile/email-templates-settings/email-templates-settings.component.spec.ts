import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { of, Subject, throwError } from 'rxjs';
import { EmailTemplate } from '../../../models';
import { EmailTemplateService } from '../../../services/email-template/email-template.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { EmailTemplatesSettingsComponent } from './email-templates-settings.component';

describe('EmailTemplatesSettingsComponent', () => {
  let templates: jasmine.SpyObj<EmailTemplateService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  const relaunch: EmailTemplate = { id: 1, name: 'Relance', subject: 'Suivi', body: 'Bonjour {{firstName}}' };
  const announce: EmailTemplate = { id: 2, name: 'Annonce', subject: 'Dispo', body: 'Bonjour' };

  function create(list = of([relaunch, announce])): ComponentFixture<EmailTemplatesSettingsComponent> {
    templates = jasmine.createSpyObj('EmailTemplateService', ['list', 'create', 'update', 'delete']);
    templates.list.and.returnValue(list);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    TestBed.configureTestingModule({
      imports: [EmailTemplatesSettingsComponent, TranslateModule.forRoot()],
      providers: [
        { provide: EmailTemplateService, useValue: templates },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }
      ]
    });
    const fixture = TestBed.createComponent(EmailTemplatesSettingsComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<EmailTemplatesSettingsComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  it('lists the templates by name and says when there are none or they cannot be loaded', () => {
    const fixture = create();
    expect(fixture.componentInstance.templates.map(t => t.name)).toEqual(['Annonce', 'Relance']);
    expect(text(fixture)).toContain('RelanceSuivi');

    TestBed.resetTestingModule();
    expect(text(create(of([])))).toContain('emails.settings.none');
    TestBed.resetTestingModule();
    create(throwError(() => new Error('down')));
    expect(notifications.error).toHaveBeenCalledWith('emails.settings.loadError');
  });

  it('writes a new template with variables and saves it', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    templates.create.and.returnValue(of({ id: 3, name: 'Proposition', subject: 'TJM', body: 'Mon TJM : {{rate}}' }));

    component.add();
    fixture.detectChanges();
    component.form.patchValue({ name: 'Proposition', subject: 'TJM', body: 'Mon TJM : ' });
    (fixture.nativeElement.querySelectorAll('mat-chip')[4] as HTMLElement).click();
    expect(component.form.value.body).toBe('Mon TJM : {{rate}}');
    component.save();

    expect(templates.create).toHaveBeenCalledWith({ name: 'Proposition', subject: 'TJM', body: 'Mon TJM : {{rate}}' });
    expect(component.editing).toBeNull();
    expect(component.templates.map(t => t.name)).toEqual(['Annonce', 'Proposition', 'Relance']);
    expect(notifications.success).toHaveBeenCalledWith('emails.settings.saved');
  });

  it('changes a template, or cancels', () => {
    const component = create().componentInstance;
    templates.update.and.returnValue(of({ ...relaunch, subject: 'Relance mission' }));

    component.edit(relaunch);
    expect(component.form.value).toEqual({ name: 'Relance', subject: 'Suivi', body: 'Bonjour {{firstName}}' });
    component.form.patchValue({ subject: 'Relance mission' });
    component.save();
    expect(templates.update).toHaveBeenCalledWith(1, { name: 'Relance', subject: 'Relance mission', body: 'Bonjour {{firstName}}' });
    expect(component.templates.find(t => t.id === 1)!.subject).toBe('Relance mission');

    component.edit(announce);
    component.cancel();
    expect(component.editing).toBeNull();
  });

  it('marks a taken name, reports other refusals and saves once at a time', () => {
    const component = create().componentInstance;
    component.add();
    component.form.patchValue({ name: 'Relance', subject: 'x', body: 'y' });
    templates.create.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'An email template is already named "Relance"' } })),
      throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'A workspace keeps at most 50 email templates' } })),
      throwError(() => new HttpErrorResponse({ status: 500 })),
      new Subject<EmailTemplate>());

    component.save();
    expect(component.form.get('name')!.hasError('taken')).toBeTrue();
    component.form.get('name')!.setValue('Autre');
    component.save();
    component.save();
    expect(notifications.error.calls.allArgs()).toEqual([['emails.settings.limit'], ['emails.settings.saveError']]);
    component.save();
    component.save();
    expect(templates.create).toHaveBeenCalledTimes(4);

    component.cancel();
    component.save();
    expect(templates.create).toHaveBeenCalledTimes(4);
  });

  it('deletes a template once confirmed', () => {
    const component = create().componentInstance;
    confirm.confirm.and.returnValues(of(false), of(true), of(true));
    templates.delete.and.returnValues(of(undefined), throwError(() => new Error('down')));

    component.remove(relaunch);
    component.remove(relaunch);
    component.remove(announce);

    expect(templates.delete.calls.allArgs()).toEqual([[1], [2]]);
    expect(component.templates.map(t => t.id)).toEqual([2]);
    expect(notifications.error).toHaveBeenCalledWith('emails.settings.deleteError');
  });
});
