import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { Conversation, Message, MessageThread, ProjectDto } from '../../../models';
import { NetworkMessagesService } from '../../../services/network/network-messages.service';
import { NetworkNoticesService } from '../../../services/network/network-notices.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { MessageThreadComponent, THREAD_REFRESH_MS } from './message-thread.component';
import { ReportDialogComponent } from '../report-dialog/report-dialog.component';
import { ProjectService } from '../../../services/project/project.service';
import { AuthService } from '../../../services/auth/auth.service';
import { ConfidentialityGuardService } from '../confidentiality/confidentiality-guard.service';
import en from '../../../../assets/i18n/en.json';

describe('MessageThreadComponent', () => {
  let api: jasmine.SpyObj<NetworkMessagesService>;
  let notices: jasmine.SpyObj<NetworkNoticesService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let projects: jasmine.SpyObj<ProjectService>;
  let guard: jasmine.SpyObj<ConfidentialityGuardService>;
  let fixture: ComponentFixture<MessageThreadComponent>;
  let emitted: Conversation[];
  const conversation: Conversation = { id: 4, with: { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null,
    stack: [], city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false }, last: null, unread: 1,
    muted: false, archived: false, closed: null, lastMessageAt: '2026-11-16T09:00:00' };
  const message = (id: number, changes: Partial<Message> = {}): Message => ({ id, mine: false, text: `Message ${id}`,
    sentAt: `2026-11-1${id < 3 ? 5 : 6}T09:0${id}:00`, edited: false, deleted: false, editable: false, replyTo: null, reactions: [],
    myReaction: null, card: null, ...changes });
  const thread = (messages: Message[], changes: Partial<MessageThread> = {}): MessageThread => ({ conversation, messages, more: false,
    ...changes });

  function create(first = of(thread([message(1), message(2), message(3, { mine: true, editable: true })], { more: true }))): HTMLElement {
    api = jasmine.createSpyObj('NetworkMessagesService', ['thread', 'read', 'send', 'edit', 'remove', 'react', 'settings', 'pdf', 'take']);
    api.thread.and.returnValue(first);
    api.read.and.returnValue(of({ ...conversation, unread: 0 }));
    notices = jasmine.createSpyObj('NetworkNoticesService', ['refreshMessages']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    projects = jasmine.createSpyObj('ProjectService', ['getByFreelanceId']);
    guard = jasmine.createSpyObj('ConfidentialityGuardService', ['review']);
    guard.review.and.callFake((text: string) => of({ action: 'publish' as const, text }));
    TestBed.configureTestingModule({
      imports: [MessageThreadComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: NetworkMessagesService, useValue: api }, { provide: NetworkNoticesService, useValue: notices },
        { provide: NotificationService, useValue: notifications }, { provide: ConfirmDialogService, useValue: confirm },
        { provide: ProjectService, useValue: projects }, { provide: AuthService, useValue: { getUser: () => ({ id: 3 }) } },
        { provide: ConfidentialityGuardService, useValue: guard }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    fixture = TestBed.createComponent(MessageThreadComponent);
    emitted = [];
    fixture.componentInstance.changed.subscribe(changed => emitted.push(changed));
    fixture.componentRef.setInput('conversationId', 4);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('shows the messages by day, the reader\'s on the right, reads them, and loads earlier ones', fakeAsync(() => {
    const element = create();
    expect(element.querySelectorAll('[data-message]').length).toBe(3);
    expect(element.querySelectorAll('.day').length).toBe(2);
    expect(element.querySelector('[data-message="3"]')?.classList).toContain('mine');
    expect(element.querySelector('[data-message="1"] [data-message-edit]')).toBeNull();
    expect(element.querySelector('[data-message="3"] [data-message-edit]')).not.toBeNull();
    tick();
    expect(api.read).toHaveBeenCalledWith(4);
    expect(notices.refreshMessages).toHaveBeenCalled();
    expect(emitted.at(-1)?.unread).toBe(0);

    api.thread.and.returnValue(of(thread([message(0)], { more: false })));
    (element.querySelector('[data-thread-older]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(api.thread).toHaveBeenCalledWith(4, 1);
    expect(fixture.componentInstance.messages.map(item => item.id)).toEqual([0, 1, 2, 3]);
    expect(element.querySelector('[data-thread-older]')).toBeNull();
    discardPeriodicTasks();
  }));

  it('asks for new messages every ten seconds while seen, keeping the earlier ones loaded', fakeAsync(() => {
    create();
    api.thread.and.returnValue(of(thread([message(2, { edited: true }), message(3, { mine: true }), message(4)],
      { conversation: { ...conversation, unread: 1 } })));
    tick(THREAD_REFRESH_MS);
    expect(fixture.componentInstance.messages.map(item => item.id)).toEqual([1, 2, 3, 4]);
    expect(fixture.componentInstance.messages[1].edited).toBeTrue();
    expect(api.read).toHaveBeenCalledTimes(2);
    spyOnProperty(document, 'hidden').and.returnValue(true);
    tick(THREAD_REFRESH_MS);
    expect(api.thread).toHaveBeenCalledTimes(2);
    discardPeriodicTasks();
  }));

  it('sends with Enter, replies quoting a message, and edits or deletes the reader\'s own', fakeAsync(() => {
    const element = create();
    const component = fixture.componentInstance;
    const field = element.querySelector('[data-thread-text]') as HTMLTextAreaElement;
    api.send.and.returnValue(of(message(5, { mine: true, text: 'Avec plaisir', replyTo: { id: 1, mine: false, excerpt: 'Message 1',
      deleted: false } })));
    (element.querySelector('[data-message="1"] [data-message-reply]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('[data-thread-context]')?.textContent).toContain('Message 1');
    component.draft.setValue('Avec plaisir');
    field.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true }));
    expect(api.send).not.toHaveBeenCalled();
    field.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    fixture.detectChanges();
    expect(api.send).toHaveBeenCalledWith(4, 'Avec plaisir', 1);
    expect(element.querySelector('[data-message="5"] [data-message-quote]')?.textContent).toContain('Message 1');
    expect(emitted.at(-1)?.last?.text).toBe('Avec plaisir');
    expect(component.draft.value).toBe('');

    component.startEditing(component.messages.find(item => item.id === 3)!);
    fixture.detectChanges();
    expect(component.draft.value).toBe('Message 3');
    field.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(component.editing).toBeNull();
    expect(component.draft.value).toBe('');
    component.startEditing(component.messages.find(item => item.id === 3)!);
    component.draft.setValue('Message corrigé');
    api.edit.and.returnValue(of(message(3, { mine: true, text: 'Message corrigé', edited: true })));
    component.submit();
    expect(api.edit).toHaveBeenCalledWith(3, 'Message corrigé');
    expect(component.messages.find(item => item.id === 3)?.edited).toBeTrue();

    confirm.confirm.and.returnValue(of(false));
    component.remove(component.messages.find(item => item.id === 3)!);
    expect(api.remove).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    api.remove.and.returnValue(of(message(3, { mine: true, text: '', deleted: true })));
    component.remove(component.messages.find(item => item.id === 3)!);
    fixture.detectChanges();
    expect(element.querySelector('[data-message="3"]')?.textContent).toContain('Message deleted');
    api.remove.and.returnValue(throwError(() => new Error('down')));
    component.remove(message(5));
    expect(notifications.error).toHaveBeenCalledWith('network.messages.changeError');
    component.submit();
    expect(api.send).toHaveBeenCalledTimes(1);
    discardPeriodicTasks();
  }));

  it('shares one of the reader\'s opportunities as a card, which the other member adds to their pipeline once', fakeAsync(() => {
    const card = { role: 'Lead Kafka', dailyRate: 720, city: 'Lyon', startDate: '2026-12-01', remoteDaysPerMonth: 8, stack: ['Kafka'],
      takenProjectId: null };
    const element = create(of(thread([message(1, { text: '', card })])));
    const component = fixture.componentInstance;
    const shown = element.querySelector('[data-message="1"] [data-message-card]')!;
    expect(shown.textContent).toContain('Lead Kafka');
    expect(shown.textContent).toContain('720 € a day');
    expect(shown.textContent).toContain('8 remote days a month');
    expect(element.querySelector('[data-message="1"] .text')).toBeNull();

    api.take.and.returnValue(of({ id: 42 } as ProjectDto));
    (shown.querySelector('[data-card-take]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(api.take).toHaveBeenCalledWith(1);
    expect(notifications.success).toHaveBeenCalledWith('network.pipeline.added');
    expect(element.querySelector('[data-card-open]')?.getAttribute('href')).toBe('/projects/42');
    api.take.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.take(component.messages[0]);
    expect(notifications.error).toHaveBeenCalledWith('network.messages.cardAlready');
    api.take.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.take(component.messages[0]);
    expect(notifications.error).toHaveBeenCalledWith('network.messages.cardError');

    projects.getByFreelanceId.and.returnValue(of([{ id: 7, role: 'Lead Java', dailyRate: 650 }, { id: 8, role: 'Ancienne', archivedAt: '2026-01-01' },
      { id: 9, role: 'Data' }] as ProjectDto[]));
    component.loadOpportunities();
    component.loadOpportunities();
    expect(projects.getByFreelanceId).toHaveBeenCalledOnceWith(3);
    expect(component.opportunities?.map(project => project.id)).toEqual([9, 7]);
    api.send.and.returnValue(of(message(5, { mine: true, text: 'Pour toi', card: { ...card, role: 'Lead Java' } })));
    component.draft.setValue('Pour toi');
    component.share(component.opportunities![1]);
    fixture.detectChanges();
    expect(api.send).toHaveBeenCalledWith(4, 'Pour toi', null, 7);
    expect(element.querySelector('[data-message="5"] [data-message-card]')?.textContent).toContain('Lead Java');
    expect(element.querySelector('[data-message="5"] [data-card-take]')).toBeNull();
    api.send.and.returnValue(throwError(() => new Error('down')));
    component.share(component.opportunities![0]);
    expect(notifications.error).toHaveBeenCalledWith('network.messages.sendError');
    discardPeriodicTasks();
  }));

  it('says when the opportunities to share cannot be loaded', fakeAsync(() => {
    create();
    projects.getByFreelanceId.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.loadOpportunities();
    expect(fixture.componentInstance.opportunities).toEqual([]);
    expect(notifications.error).toHaveBeenCalledWith('network.messages.loadError');
    discardPeriodicTasks();
  }));

  it('reports another member\'s message, never the reader\'s own', fakeAsync(() => {
    const element = create();
    expect(element.querySelector('[data-message="3"] [data-message-report]')).toBeNull();
    const open = spyOn(ReportDialogComponent, 'open').and.returnValue(of(true));
    (element.querySelector('[data-message="1"] [data-message-report]') as HTMLButtonElement).click();
    expect(open).toHaveBeenCalledWith(jasmine.anything(), { target: 'messages', id: 1 });
    discardPeriodicTasks();
  }));

  it('downloads the conversation as a PDF in the page\'s language, or says it could not', fakeAsync(() => {
    create();
    const component = fixture.componentInstance;
    const click = spyOn(HTMLAnchorElement.prototype, 'click');
    api.pdf.and.returnValue(of(new Blob(['%PDF'], { type: 'application/pdf' })));
    component.downloadPdf();
    expect(api.pdf).toHaveBeenCalledWith(4, 'EN');
    expect(click).toHaveBeenCalled();
    TestBed.inject(TranslateService).use('fr');
    api.pdf.and.returnValue(throwError(() => new Error('down')));
    component.downloadPdf();
    expect(api.pdf).toHaveBeenCalledWith(4, 'FR');
    expect(notifications.error).toHaveBeenCalledWith('network.messages.pdfError');
    discardPeriodicTasks();
  }));

  it('points out the workspace\'s names before a message goes, sending what the reader chose', fakeAsync(() => {
    create();
    const component = fixture.componentInstance;
    guard.review.and.returnValue(of({ action: 'edit' as const, text: 'Une mission chez un client à Paris' }));
    component.draft.setValue('Une mission chez Société Générale');
    component.submit();
    expect(guard.review).toHaveBeenCalledWith('Une mission chez Société Générale');
    expect(api.send).not.toHaveBeenCalled();
    expect(component.draft.value).toBe('Une mission chez un client à Paris');
    expect(notifications.success).toHaveBeenCalledWith('network.confidentiality.anonymized');
    guard.review.and.returnValue(of(null));
    component.submit();
    expect(api.send).not.toHaveBeenCalled();
    expect(component.sending).toBeFalse();
    discardPeriodicTasks();
  }));

  it('reacts to a message, takes the same reaction back, and mutes or archives the conversation', fakeAsync(() => {
    const element = create();
    const component = fixture.componentInstance;
    api.react.and.returnValue(of(message(1, { reactions: [{ reaction: 'BRAVO', count: 1 }], myReaction: 'BRAVO' })));
    component.react(component.messages[0], 'BRAVO');
    fixture.detectChanges();
    expect(api.react).toHaveBeenCalledWith(1, 'BRAVO');
    const chip = element.querySelector('[data-message="1"] [data-message-reactions] button') as HTMLButtonElement;
    expect(chip.textContent).toContain('👏 1');
    api.react.and.returnValue(of(message(1)));
    chip.click();
    expect(api.react).toHaveBeenCalledWith(1, null);
    api.react.and.returnValue(throwError(() => new Error('down')));
    component.react(component.messages[0], 'WOW');
    expect(notifications.error).toHaveBeenCalledWith('network.messages.changeError');

    api.settings.and.returnValue(of({ ...conversation, muted: true }));
    component.set({ muted: true });
    fixture.detectChanges();
    expect(api.settings).toHaveBeenCalledWith(4, { muted: true });
    expect(element.querySelector('[data-thread-muted]')).not.toBeNull();
    expect(emitted.at(-1)?.muted).toBeTrue();
    api.settings.and.returnValue(throwError(() => new Error('down')));
    component.set({ archived: true });
    expect(notifications.error).toHaveBeenCalledTimes(2);
    discardPeriodicTasks();
  }));

  it('says why no message can go, and says when sending fails or the conversation is gone', fakeAsync(() => {
    const element = create(of(thread([message(1)], { conversation: { ...conversation, closed: 'NOT_FOLLOWED' } })));
    const component = fixture.componentInstance;
    expect(element.querySelector('[data-thread-closed]')?.textContent).toContain('Léo does not follow you');
    expect(element.querySelector('[data-thread-composer]')).toBeNull();
    expect(element.querySelector('[data-message-reply]')).toBeNull();
    component.draft.setValue('Toujours là ?');
    component.submit();
    expect(api.send).not.toHaveBeenCalled();

    component.conversation = { ...conversation };
    api.send.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.submit();
    expect(notifications.error).toHaveBeenCalledWith('network.messages.closedError');
    expect(component.conversation?.closed).toBe('NOT_FOLLOWED');
    component.conversation = { ...conversation };
    api.send.and.returnValue(throwError(() => new HttpErrorResponse({ status: 429 })));
    component.submit();
    expect(notifications.error).toHaveBeenCalledWith('network.messages.tooMany');
    api.send.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.submit();
    expect(notifications.error).toHaveBeenCalledWith('network.messages.sendError');

    api.thread.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    component.load(false);
    fixture.detectChanges();
    expect(element.querySelector('[data-thread-missing]')).not.toBeNull();
    api.thread.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.load(true);
    expect(notifications.error).toHaveBeenCalledWith('network.messages.loadError');
    component.loadOlder();
    expect(notifications.error).toHaveBeenCalledTimes(5);
    discardPeriodicTasks();
  }));
});
