import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import { ContactDto, ContactInteraction } from '../../../models/contact.models';
import { ProjectDto, ProjectNote } from '../../../models';
import { ContactService } from '../../../services/contact/contact.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProjectService } from '../../../services/project/project.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { localDay } from '../../contacts/contact-interactions/contact-interactions.component';
import { ProjectJournalComponent } from './project-journal.component';

describe('ProjectJournalComponent', () => {
  const mission = { id: 12, role: 'Développeur Java', dailyRate: 650, clientId: 3, middlemanId: 8 } as ProjectDto;
  const notes: ProjectNote[] = [
    { id: 5, projectId: 12, content: '**Relance** envoyée', createdAt: '2026-10-21T09:30:00' },
    { id: 4, projectId: 12, content: 'Premier contact', createdAt: '2026-10-19T14:00:00' }
  ];
  const call: ContactInteraction = { id: 30, contactId: 9, contactName: 'Nadia Kaci', occurredOn: '2026-10-20', channel: 'PHONE',
    direction: 'OUTGOING', replied: true, summary: 'Point sur le budget', projectId: 12 };
  const nadia: ContactDto = { id: 9, firstName: 'Nadia', lastName: 'Kaci', clientId: 8, clientName: 'Esn' };
  const alice: ContactDto = { id: 2, firstName: 'Alice', clientId: 3, clientName: 'Acme' };

  let fixture: ComponentFixture<ProjectJournalComponent>;
  let component: ProjectJournalComponent;
  let projectService: jasmine.SpyObj<ProjectService>;
  let contacts: jasmine.SpyObj<ContactService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirmDialog: jasmine.SpyObj<ConfirmDialogService>;

  function create(project: ProjectDto = mission, before: () => void = () => undefined,
                  exchanges: Observable<ContactInteraction[]> = of([call])): void {
    projectService = jasmine.createSpyObj('ProjectService', ['getProjectNotes', 'addProjectNote', 'deleteProjectNote']);
    projectService.getProjectNotes.and.returnValue(of([...notes]));
    contacts = jasmine.createSpyObj('ContactService', ['getByClientId', 'getProjectInteractions', 'addInteraction']);
    contacts.getByClientId.and.callFake((clientId: number) => of(clientId === 3 ? [alice] : [nadia]));
    contacts.getProjectInteractions.and.returnValue(exchanges);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    confirmDialog = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    confirmDialog.confirm.and.returnValue(of(true));
    before();
    TestBed.configureTestingModule({
      imports: [ProjectJournalComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ProjectService, useValue: projectService },
        { provide: ContactService, useValue: contacts },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirmDialog }
      ]
    });
    fixture = TestBed.createComponent(ProjectJournalComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('project', project);
    fixture.detectChanges();
  }

  function element(): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('merges the notes and the exchanges about the opportunity, newest first', () => {
    create();
    const entries = Array.from(element().querySelectorAll('.journal-entry'));

    expect(projectService.getProjectNotes).toHaveBeenCalledWith(12);
    expect(contacts.getProjectInteractions).toHaveBeenCalledWith(12);
    expect(entries.length).toBe(3);
    expect(entries[0].querySelector('.journal-content strong')?.textContent).toBe('Relance');
    expect(entries[1].classList).toContain('exchange-entry');
    expect(entries[1].textContent).toContain('Nadia Kaci');
    expect(entries[1].textContent).toContain('Point sur le budget');
    expect(entries[1].querySelector('a')!.getAttribute('href')).toBe('/contacts/9');
    expect(entries[2].textContent).toContain('Premier contact');
    expect(element().textContent).not.toContain('projects.notes.empty');
  });

  it('reports notes or exchanges that could not be loaded', () => {
    create(mission, () => projectService.getProjectNotes.and.returnValue(throwError(() => new Error('down'))),
      throwError(() => new Error('down')));

    expect(notifications.error).toHaveBeenCalledWith('projects.notes.loadError');
    expect(notifications.error).toHaveBeenCalledWith('projects.journal.exchangesError');
    expect(element().textContent).toContain('projects.notes.empty');
  });

  it('previews and adds a note at the top of the journal', async () => {
    create();
    const added: ProjectNote = { id: 6, projectId: 12, content: 'Proposition *reçue*', createdAt: '2026-10-22T08:00:00' };
    projectService.addProjectNote.and.returnValue(of(added));
    const textarea = element().querySelector<HTMLTextAreaElement>('.journal-input textarea')!;

    textarea.value = '  Proposition *reçue*  ';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(element().querySelector('.journal-preview em')?.textContent).toBe('reçue');
    element().querySelector<HTMLButtonElement>('.journal-add .journal-actions button[color="primary"]')!.click();

    expect(projectService.addProjectNote).toHaveBeenCalledWith(12, 'Proposition *reçue*');
    expect(component.notes[0]).toEqual(added);
    expect(component.newNoteContent).toBe('');
    expect(component.isSavingNote).toBe(false);
    expect(element().querySelectorAll('.journal-entry').length).toBe(4);
  });

  it('adds no blank note, nor one while another is saved, and keeps one that could not be saved', () => {
    create();
    component.newNoteContent = '   ';
    component.addNote();
    component.newNoteContent = 'Relancer';
    component.isSavingNote = true;
    component.addNote();
    expect(projectService.addProjectNote).not.toHaveBeenCalled();

    component.isSavingNote = false;
    projectService.addProjectNote.and.returnValue(throwError(() => new Error('down')));
    component.addNote();
    expect(notifications.error).toHaveBeenCalledWith('projects.notes.saveError');
    expect(component.newNoteContent).toBe('Relancer');
    expect(component.isSavingNote).toBe(false);
  });

  it('deletes a note once confirmed, keeps it otherwise, and reports a failed deletion', () => {
    create();
    projectService.deleteProjectNote.and.returnValues(of(undefined), throwError(() => new Error('down')));

    element().querySelector<HTMLButtonElement>('.journal-delete')!.click();
    expect(confirmDialog.confirm).toHaveBeenCalledWith({ messageKey: 'projects.notes.deleteConfirm', danger: true });
    expect(projectService.deleteProjectNote).toHaveBeenCalledWith(12, 5);
    expect(component.notes.map(note => note.id)).toEqual([4]);

    confirmDialog.confirm.and.returnValue(of(false));
    component.deleteNote(4);
    expect(projectService.deleteProjectNote).toHaveBeenCalledTimes(1);
    confirmDialog.confirm.and.returnValue(of(true));
    component.deleteNote(4);
    expect(notifications.error).toHaveBeenCalledWith('projects.notes.deleteError');
    expect(component.notes.length).toBe(1);
  });

  it('inserts a note template, after what is already typed', () => {
    create();
    component.selectedNoteTemplate = 'callDebrief';
    component.applyNoteTemplate('callDebrief');
    expect(component.newNoteContent).toBe('projects.notes.templates.callDebrief.body');
    expect(component.selectedNoteTemplate).toBe('');

    component.newNoteContent = 'Appel de 10h  \n';
    component.applyNoteTemplate('negotiationSummary');
    expect(component.newNoteContent).toBe('Appel de 10h\n\nprojects.notes.templates.negotiationSummary.body');
    component.applyNoteTemplate('');
    expect(component.newNoteContent).toBe('Appel de 10h\n\nprojects.notes.templates.negotiationSummary.body');
  });

  it('records an exchange about the opportunity with a contact of the client or its ESN', () => {
    create();
    contacts.addInteraction.and.returnValue(of(call));

    expect(component.contacts.map(contact => component.nameOf(contact))).toEqual(['Alice', 'Nadia Kaci']);
    element().querySelector<HTMLButtonElement>('.record-exchange')!.click();
    expect(component.exchangeForm.getRawValue().contactId).toBeNull();
    component.recordExchange();
    expect(contacts.addInteraction).not.toHaveBeenCalled();

    component.exchangeForm.patchValue({ contactId: 9, channel: 'LINKEDIN', answered: false, summary: '  Relance  ' });
    component.recordExchange();

    expect(contacts.addInteraction).toHaveBeenCalledWith(9, { occurredOn: localDay(), channel: 'LINKEDIN', direction: 'OUTGOING',
      replied: false, summary: 'Relance', projectId: 12 });
    expect(component.recording).toBe(false);
    expect(contacts.getProjectInteractions).toHaveBeenCalledTimes(2);
  });

  it('keeps no answer on an exchange the contact started, and reports one that could not be saved', () => {
    create({ ...mission, middlemanId: undefined });
    contacts.addInteraction.and.returnValue(throwError(() => new Error('down')));

    component.startRecording();
    expect(component.exchangeForm.getRawValue().contactId).toBe(2);
    component.exchangeForm.patchValue({ direction: 'INCOMING', answered: true, summary: ' ' });
    component.recordExchange();

    expect(contacts.addInteraction).toHaveBeenCalledWith(2, jasmine.objectContaining({ direction: 'INCOMING', replied: null,
      summary: null }));
    expect(notifications.error).toHaveBeenCalledWith('contacts.interactions.saveError');
    expect(component.recording).toBe(true);
    expect(component.isSavingExchange).toBe(false);
  });

  it('says how to add a contact when the client has none', () => {
    create({ ...mission, clientId: undefined, middlemanId: undefined });

    component.startRecording();

    expect(contacts.getByClientId).not.toHaveBeenCalled();
    expect(element().textContent).toContain('projects.journal.noContacts');
  });
});
