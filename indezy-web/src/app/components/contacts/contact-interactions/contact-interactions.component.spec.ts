import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import { ContactInteraction, ContactInteractions } from '../../../models/contact.models';
import { ProjectDto } from '../../../models/project.models';
import { AuthService } from '../../../services/auth/auth.service';
import { ContactService } from '../../../services/contact/contact.service';
import { ProjectService } from '../../../services/project/project.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ContactInteractionsComponent, localDay } from './contact-interactions.component';

describe('ContactInteractionsComponent', () => {
  let fixture: ComponentFixture<ContactInteractionsComponent>;
  let component: ContactInteractionsComponent;
  let contacts: jasmine.SpyObj<ContactService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let projects: jasmine.SpyObj<ProjectService>;
  const kafka = { id: 40, role: 'Kafka engineer', dailyRate: 700, clientName: 'Acme' } as ProjectDto;
  const java = { id: 41, role: 'Java developer', dailyRate: 600 } as ProjectDto;
  const archived = { id: 42, role: 'Archived role', dailyRate: 500, archivedAt: '2026-10-01T03:30:00' } as ProjectDto;

  const sent: ContactInteraction = { id: 7, occurredOn: '2026-09-10', channel: 'EMAIL', direction: 'OUTGOING', replied: true,
    summary: 'Sent my CV' };
  const received: ContactInteraction = { id: 8, occurredOn: '2026-09-12', channel: 'PHONE', direction: 'INCOMING' };
  const history: ContactInteractions = { interactions: [received, sent], lastContactedOn: '2026-09-12', asked: 1, answered: 1,
    responseRate: 100, preferredChannel: 'PHONE' };

  function create(loaded: Observable<ContactInteractions> = of(history),
                  opportunities: Observable<ProjectDto[]> = of([kafka, archived, java])): void {
    projects = jasmine.createSpyObj('ProjectService', ['getByFreelanceId']);
    projects.getByFreelanceId.and.returnValue(opportunities);
    contacts = jasmine.createSpyObj('ContactService', ['getInteractions', 'addInteraction', 'updateInteraction', 'deleteInteraction']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    contacts.getInteractions.and.returnValue(loaded);
    TestBed.configureTestingModule({
      imports: [ContactInteractionsComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ContactService, useValue: contacts },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: ProjectService, useValue: projects },
        { provide: AuthService, useValue: { getUser: () => ({ id: 7 }) } }
      ]
    });
    fixture = TestBed.createComponent(ContactInteractionsComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('contactId', 3);
    fixture.detectChanges();
  }

  function element(): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function text(): string {
    return element().textContent!.replace(/\s+/g, ' ');
  }

  it('shows the summary and each exchange with its answer', () => {
    create();

    expect(contacts.getInteractions).toHaveBeenCalledWith(3);
    expect(text()).toContain('contacts.interactions.rate');
    expect(text()).toContain('contacts.interactions.channels.PHONE');
    const items = element().querySelectorAll('.interaction');
    expect(items.length).toBe(2);
    expect(items[1].querySelector('.answer-pill.answered')).not.toBeNull();
    expect(items[1].textContent).toContain('Sent my CV');
    expect(items[0].querySelector('.answer-pill')).toBeNull();
  });

  it('says when nothing is known yet', () => {
    create(of({ interactions: [], asked: 0, answered: 0 }));

    expect(text()).toContain('contacts.interactions.never');
    expect(text()).toContain('contacts.interactions.noAnswerKnown');
    expect(text()).toContain('contacts.interactions.noPreference');
    expect(text()).toContain('contacts.interactions.empty');
  });

  it('records an exchange started today, answered or not', () => {
    create();
    contacts.addInteraction.and.returnValue(of({ ...sent, id: 9 }));

    element().querySelector<HTMLButtonElement>('.add-interaction')!.click();
    expect(component.form.getRawValue().occurredOn).toBe(localDay());
    component.form.patchValue({ channel: 'LINKEDIN', answer: 'UNANSWERED', summary: '  Followed up  ' });
    component.save();

    expect(contacts.addInteraction).toHaveBeenCalledWith(3, { occurredOn: localDay(), channel: 'LINKEDIN', direction: 'OUTGOING',
      replied: false, summary: 'Followed up', projectId: null });
    expect(notifications.success).toHaveBeenCalledWith('contacts.interactions.saved');
    expect(contacts.getInteractions).toHaveBeenCalledTimes(2);
    expect(component.formOpen).toBe(false);
  });

  it('keeps no answer on an exchange the contact started and asks nothing while waiting', () => {
    create();
    contacts.addInteraction.and.returnValue(of(received));

    component.startAdd();
    component.form.patchValue({ direction: 'INCOMING', answer: 'ANSWERED' });
    expect(element().querySelector('.answer-field')).toBeNull();
    component.save();
    component.startAdd();
    component.form.patchValue({ summary: '   ' });
    component.save();

    expect(contacts.addInteraction.calls.argsFor(0)[1]).toEqual(jasmine.objectContaining({ replied: null, summary: null }));
    expect(contacts.addInteraction.calls.argsFor(1)[1]).toEqual(jasmine.objectContaining({ direction: 'OUTGOING', replied: null }));
  });

  it('changes an exchange from its values', () => {
    create();
    contacts.updateInteraction.and.returnValue(of(sent));

    component.startEdit(sent);
    expect(component.form.getRawValue()).toEqual({ occurredOn: '2026-09-10', channel: 'EMAIL', direction: 'OUTGOING',
      answer: 'ANSWERED', summary: 'Sent my CV', projectId: null });
    component.form.patchValue({ answer: 'WAITING' });
    component.save();
    expect(contacts.updateInteraction).toHaveBeenCalledWith(3, 7, jasmine.objectContaining({ replied: null }));

    component.startEdit({ ...sent, replied: false, summary: null });
    expect(component.form.getRawValue().answer).toBe('UNANSWERED');
    expect(component.form.getRawValue().summary).toBe('');
    component.startEdit({ ...received, id: undefined });
    expect(component.editingId).toBeNull();
    expect(component.form.getRawValue().answer).toBe('WAITING');
  });

  it('refuses an incomplete exchange and reports a failed save once', () => {
    create();
    contacts.addInteraction.and.returnValue(throwError(() => new Error('down')));

    component.startAdd();
    component.form.patchValue({ occurredOn: '' });
    component.save();
    expect(contacts.addInteraction).not.toHaveBeenCalled();

    component.form.patchValue({ occurredOn: '2026-09-01' });
    component.save();
    expect(notifications.error).toHaveBeenCalledWith('contacts.interactions.saveError');
    expect(component.saving).toBe(false);
    expect(component.formOpen).toBe(true);

    component.saving = true;
    component.save();
    expect(contacts.addInteraction).toHaveBeenCalledTimes(1);
  });

  it('closes the form on cancel', () => {
    create();
    component.startEdit(sent);
    element().querySelector<HTMLButtonElement>('.form-actions button[type="button"]')!.click();

    expect(component.formOpen).toBe(false);
    expect(component.editingId).toBeNull();
  });

  it('deletes an exchange once confirmed and closes it if it was being changed', () => {
    create();
    contacts.deleteInteraction.and.returnValue(of(undefined));
    confirm.confirm.and.returnValues(of(false), of(true), of(true));

    component.remove(sent);
    expect(contacts.deleteInteraction).not.toHaveBeenCalled();

    component.startEdit(sent);
    component.remove(sent);
    expect(contacts.deleteInteraction).toHaveBeenCalledWith(3, 7);
    expect(component.formOpen).toBe(false);

    component.startEdit(received);
    component.remove(sent);
    expect(component.formOpen).toBe(true);
    expect(contacts.getInteractions).toHaveBeenCalledTimes(3);
  });

  it('reports a failed load or delete, and deletes nothing without an id', () => {
    create(throwError(() => new Error('down')));
    expect(notifications.error).toHaveBeenCalledWith('contacts.interactions.loadError');

    contacts.deleteInteraction.and.returnValue(throwError(() => new Error('down')));
    confirm.confirm.and.returnValue(of(true));
    component.remove({ ...sent, id: undefined });
    expect(contacts.deleteInteraction).not.toHaveBeenCalled();
    component.remove(sent);
    expect(notifications.error).toHaveBeenCalledWith('contacts.interactions.deleteError');
  });

  it('writes the local day', () => {
    expect(localDay(new Date(2026, 0, 5, 23, 30))).toBe('2026-01-05');
  });

  it('offers the opportunities in the pipeline, by role, and keeps the one an exchange is about', () => {
    create(of({ ...history, interactions: [{ ...sent, projectId: 40, projectRole: 'Kafka engineer' }, received] }));
    contacts.updateInteraction.and.returnValue(of(sent));

    expect(projects.getByFreelanceId).toHaveBeenCalledWith(7);
    expect(component.projects.map(project => project.role)).toEqual(['Java developer', 'Kafka engineer']);
    const link = element().querySelector<HTMLAnchorElement>('.interaction-project')!;
    expect(link.getAttribute('href')).toBe('/projects/40');
    expect(link.textContent).toContain('Kafka engineer');

    component.startEdit({ ...sent, projectId: 40 });
    expect(component.form.getRawValue().projectId).toBe(40);
    component.form.patchValue({ projectId: 41 });
    component.save();
    expect(contacts.updateInteraction).toHaveBeenCalledWith(3, 7, jasmine.objectContaining({ projectId: 41 }));
  });

  it('records exchanges without opportunities when they cannot be loaded', () => {
    create(of(history), throwError(() => new Error('down')));

    component.startAdd();

    expect(component.projects).toEqual([]);
    expect(element().querySelector('mat-select[formControlName="projectId"]')).toBeNull();
  });
});
