import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { NetworkIdea, NetworkIdeas } from '../../../models';
import { NetworkIdeasService } from '../../../services/network/network-ideas.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { NetworkIdeasComponent } from './network-ideas.component';
import en from '../../../../assets/i18n/en.json';
import { stubNetworkNav } from '../nav/network-nav.stub';
import { stubNetworkColumns } from '../side/network-columns.stub';

describe('NetworkIdeasComponent', () => {
  let ideas: jasmine.SpyObj<NetworkIdeasService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  const idea = (id: number, changes: Partial<NetworkIdea> = {}): NetworkIdea => ({ id, title: `Idée ${id}`, description: 'Le besoin',
    status: 'OPEN', statusNote: null, votes: 1, voted: false, own: false, author: null, createdAt: '2026-11-20T09:00:00',
    statusChangedAt: null, mergedInto: null, ...changes });
  const board = (list: NetworkIdea[], changes: Partial<NetworkIdeas> = {}): NetworkIdeas => ({ ideas: list, page: 0, more: false,
    administrator: false, ...changes });

  function create(first: NetworkIdeas = board([idea(1, { votes: 4 }), idea(2, { own: true, voted: true })]), focus: NetworkIdea | null = null) {
    ideas = jasmine.createSpyObj('NetworkIdeasService', ['list', 'get', 'create', 'update', 'remove', 'vote', 'setStatus', 'merge']);
    ideas.list.and.returnValue(of(first));
    if (focus) {
      ideas.get.and.returnValue(of(focus));
    }
    notifications = jasmine.createSpyObj('NotificationService', ['error', 'success']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    params = new BehaviorSubject(convertToParamMap(focus ? { id: String(focus.id) } : {}));
    TestBed.configureTestingModule({
      imports: [NetworkIdeasComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: ActivatedRoute, useValue: { paramMap: params } },
        { provide: NetworkIdeasService, useValue: ideas }, { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }]
    });
    stubNetworkNav(NetworkIdeasComponent);
    stubNetworkColumns(NetworkIdeasComponent);
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(NetworkIdeasComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the board, the most supported first, each with its votes and status', () => {
    const fixture = create();
    expect(ideas.list).toHaveBeenCalledWith('TOP', null, '');
    const cards = fixture.nativeElement.querySelectorAll('[data-idea]') as NodeListOf<HTMLElement>;
    expect(Array.from(cards).map(card => card.getAttribute('data-idea'))).toEqual(['1', '2']);
    expect(cards[0].querySelector('[data-idea-votes]')?.textContent?.trim()).toBe('4');
    expect(cards[0].querySelector('[data-idea-status]')?.textContent).toContain('Open');
    expect(cards[1].querySelector('[data-idea-vote]')?.getAttribute('aria-pressed')).toBe('true');
    // Only the author's open idea has a menu, for a member.
    expect(cards[0].querySelector('[data-idea-actions]')).toBeNull();
    expect(cards[1].querySelector('[data-idea-actions]')).not.toBeNull();
  });

  it('orders by the newest and narrows to a status, from the first page', () => {
    const fixture = create();
    (fixture.nativeElement.querySelector('[data-ideas-sort-new]') as HTMLButtonElement).click();
    expect(ideas.list).toHaveBeenCalledWith('NEW', null, '');
    (fixture.nativeElement.querySelector('[data-ideas-status="PLANNED"]') as HTMLButtonElement).click();
    expect(ideas.list).toHaveBeenCalledWith('NEW', 'PLANNED', '');
    ideas.list.and.returnValue(of(board([])));
    fixture.componentInstance.narrow('DONE');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-ideas-none]').textContent).toContain('No idea matches.');
  });

  it('votes at once, the count following, and puts it back when the vote fails', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    ideas.vote.and.returnValue(of(idea(1, { votes: 5, voted: true })));
    component.vote(component.ideas![0]);
    expect(ideas.vote).toHaveBeenCalledWith(1, true);
    expect(component.ideas![0].votes).toBe(5);
    ideas.vote.and.returnValue(throwError(() => new Error('down')));
    component.vote(component.ideas![0]);
    expect(ideas.vote).toHaveBeenCalledWith(1, false);
    expect(component.ideas![0]).toEqual(jasmine.objectContaining({ votes: 5, voted: true }));
    expect(notifications.error).toHaveBeenCalledWith('network.ideas.saveError');
    // A shipped idea takes no more votes.
    ideas.vote.calls.reset();
    component.vote(idea(9, { status: 'DONE' }));
    expect(ideas.vote).not.toHaveBeenCalled();
  });

  it('shows the similar ideas while one is written, then proposes it first', fakeAsync(() => {
    const fixture = create();
    const component = fixture.componentInstance;
    (fixture.nativeElement.querySelector('[data-idea-new]') as HTMLButtonElement).click();
    fixture.detectChanges();
    ideas.list.and.returnValue(of(board([idea(1), idea(3), idea(4), idea(5)])));
    component.title.setValue('Export CSV');
    tick(300);
    fixture.detectChanges();
    expect(ideas.list).toHaveBeenCalledWith('TOP', null, 'Export CSV');
    expect(fixture.nativeElement.querySelectorAll('[data-idea-similar-item]').length).toBe(3);
    // Unread, none is suggested, and the next words are looked up again.
    ideas.list.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    component.title.setValue('Export CSV mensuel');
    tick(300);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('[data-idea-similar-item]').length).toBe(0);
    ideas.list.and.returnValue(of(board([idea(1), idea(3)])));
    component.title.setValue('Export CSV');
    tick(300);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('[data-idea-similar-item]').length).toBe(2);
    ideas.create.and.returnValue(of(idea(7, { title: 'Export CSV', own: true, voted: true })));
    component.description.setValue('  ');
    component.create();
    tick(300);
    expect(ideas.create).toHaveBeenCalledWith('Export CSV', null);
    expect(component.ideas!.map(item => item.id)).toEqual([7, 1, 2]);
    expect(component.creating).toBeFalse();
    expect(notifications.success).toHaveBeenCalledWith('network.ideas.created');
    ideas.create.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.title.setValue('Une autre idée');
    component.create();
    tick(300);
    expect(notifications.error).toHaveBeenCalledWith('network.ideas.tooMany');
  }));

  it('lets its author change an open idea and delete it once confirmed', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    component.startEdit(component.ideas![1]);
    component.editTitle.setValue('Idée revue');
    ideas.update.and.returnValue(of(idea(2, { title: 'Idée revue', own: true })));
    component.saveEdit(component.ideas![1]);
    expect(ideas.update).toHaveBeenCalledWith(2, 'Idée revue', 'Le besoin');
    expect(component.ideas![1].title).toBe('Idée revue');
    expect(component.editing).toBeNull();
    confirm.confirm.and.returnValue(of(false));
    component.remove(component.ideas![1]);
    expect(ideas.remove).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    ideas.remove.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.remove(component.ideas![1]);
    expect(notifications.error).toHaveBeenCalledWith('network.ideas.cannotDelete');
    ideas.remove.and.returnValue(of(undefined));
    component.remove(component.ideas![1]);
    expect(component.ideas!.map(item => item.id)).toEqual([1]);
  });

  it('lets an administrator answer an idea and merge a duplicate into another', () => {
    const fixture = create(board([idea(1, { votes: 4 }), idea(2)], { administrator: true }));
    const component = fixture.componentInstance;
    expect(fixture.nativeElement.querySelectorAll('[data-idea-actions]').length).toBe(2);
    component.startAnswer(component.ideas![0]);
    component.answerStatus.setValue('PLANNED');
    component.answerNote.setValue(' Au printemps ');
    ideas.setStatus.and.returnValue(of(idea(1, { votes: 4, status: 'PLANNED', statusNote: 'Au printemps' })));
    component.saveAnswer(component.ideas![0]);
    fixture.detectChanges();
    expect(ideas.setStatus).toHaveBeenCalledWith(1, 'PLANNED', 'Au printemps');
    expect(fixture.nativeElement.querySelector('[data-idea="1"] [data-idea-note]').textContent).toContain('Au printemps');
    component.startMerge(component.ideas![1]);
    component.mergeInto.setValue(1);
    ideas.merge.and.returnValue(of(idea(1, { votes: 5, status: 'PLANNED' })));
    component.saveMerge(component.ideas![1]);
    expect(ideas.merge).toHaveBeenCalledWith(2, 1);
    expect(component.ideas!.map(item => item.id)).toEqual([1]);
    expect(component.ideas![0].votes).toBe(5);
    expect(notifications.success).toHaveBeenCalledWith('network.ideas.merged', undefined, { title: 'Idée 1' });
  });

  it('shows the idea of its address first, and where a merged one went', () => {
    const fixture = create(board([idea(1)]), idea(2, { status: 'MERGED', mergedInto: { id: 1, title: 'Idée 1', status: 'OPEN' } }));
    expect(ideas.get).toHaveBeenCalledWith(2);
    const focused = fixture.nativeElement.querySelector('[data-idea-focused]') as HTMLElement;
    expect(focused.querySelector('[data-idea="2"]')).not.toBeNull();
    expect(focused.querySelector('[data-idea-merged-into]')?.textContent).toContain('#1 Idée 1');
    // The board's own address shows none first.
    params.next(convertToParamMap({}));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-idea-focused]')).toBeNull();
  });
});
