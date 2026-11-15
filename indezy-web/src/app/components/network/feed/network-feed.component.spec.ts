import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { MemberPage, MemberSuggestion, NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ConfidentialityGuardService } from '../confidentiality/confidentiality-guard.service';
import { NetworkFeedComponent } from './network-feed.component';
import en from '../../../../assets/i18n/en.json';

describe('NetworkFeedComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let guard: jasmine.SpyObj<ConfidentialityGuardService>;
  const post = (id: number, own = false): NetworkPost => ({ id, text: `Post ${id}`, tags: [], createdAt: '2026-11-14T10:00:00', own, likes: 0, liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, kind: 'POST' as const, details: null, answerCommentId: null, poll: null, taken: null,
    author: { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: [], city: null, remote: false,
      availability: null, availabilityDate: null, languages: [], verified: false } });

  const suggestion = (handle: string, reason: MemberSuggestion['reason'], followedBy = 0): MemberSuggestion => ({ reason, followedBy,
    followedByName: followedBy ? 'Léo' : null, sharedSkills: reason === 'SAME_STACK' ? ['Kafka'] : [], card: { ...post(0).author, handle,
      city: 'Lyon' } });

  function create(feed = of({ posts: [post(2), post(1, true)], page: 0, more: true })): ComponentFixture<NetworkFeedComponent> {
    network = jasmine.createSpyObj('NetworkService', ['feed', 'post', 'deletePost', 'followedTags', 'suggestions', 'follow', 'createDraft']);
    network.feed.and.returnValue(feed);
    network.followedTags.and.returnValue(of(['kafka']));
    network.suggestions.and.returnValue(of([suggestion('ines_b', 'FOLLOWED_BY', 3), suggestion('paul_d', 'SAME_STACK'),
      suggestion('zoe_l', 'SAME_CITY'), suggestion('jade_k', 'FOLLOWED_BY', 1), suggestion('marc_c', 'CONTACT')]));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    guard = jasmine.createSpyObj('ConfidentialityGuardService', ['review']);
    guard.review.and.callFake((text: string) => of({ action: 'publish' as const, text }));
    TestBed.configureTestingModule({
      imports: [NetworkFeedComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }, { provide: ConfidentialityGuardService, useValue: guard }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(NetworkFeedComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the posts, pages through them, publishes a post on top and deletes one once confirmed', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelectorAll('[data-post]').length).toBe(2);
    expect(element.querySelector('[data-followed-tags] a')?.getAttribute('href')).toBe('/network/tags/kafka');
    network.feed.and.returnValue(of({ posts: [post(0)], page: 1, more: false }));
    (element.querySelector('[data-feed-more]') as HTMLButtonElement).click();
    expect(network.feed).toHaveBeenCalledWith(1);
    expect(component.posts.map(item => item.id)).toEqual([2, 1, 0]);
    expect(component.more).toBeFalse();

    component.publish();
    expect(network.post).not.toHaveBeenCalled();
    network.post.and.returnValue(of(post(9, true)));
    component.draft.setValue('  Bonjour #Kafka  ');
    component.publish();
    expect(network.post).toHaveBeenCalledWith('Bonjour #Kafka', 'POST', null, null);
    expect(component.posts[0].id).toBe(9);
    expect(component.draft.value).toBe('');
    guard.review.and.returnValue(of({ action: 'edit' as const, text: 'Chez un client à Paris' }));
    component.draft.setValue('Chez Société Générale');
    component.publish();
    expect(component.draft.value).toBe('Chez un client à Paris');
    expect(notifications.success).toHaveBeenCalledWith('network.confidentiality.anonymized');
    expect(component.isPosting).toBeFalse();
    guard.review.and.returnValue(of(null));
    network.post.calls.reset();
    component.publish();
    expect(network.post).not.toHaveBeenCalled();
    expect(component.draft.value).toBe('Chez un client à Paris');
    guard.review.and.callFake((text: string) => of({ action: 'publish' as const, text }));
    network.post.and.returnValue(of({ held: true }));
    component.draft.setValue('Achetez');
    component.publish();
    expect(notifications.success).toHaveBeenCalledWith('network.feed.held');
    expect(component.posts[0].id).toBe(9);
    expect(component.draft.value).toBe('');
    network.post.and.returnValue(throwError(() => new Error('down')));
    component.draft.setValue('Again');
    component.publish();
    expect(notifications.error).toHaveBeenCalledWith('network.feed.postError');
    network.post.and.returnValue(throwError(() => new HttpErrorResponse({ status: 429 })));
    component.publish();
    expect(notifications.error).toHaveBeenCalledWith('network.feed.tooManyPosts');

    confirm.confirm.and.returnValue(of(false));
    component.remove(post(1, true));
    expect(network.deletePost).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    network.deletePost.and.returnValue(of(undefined));
    component.remove(post(1, true));
    expect(component.posts.some(item => item.id === 1)).toBeFalse();
    network.deletePost.and.returnValue(throwError(() => new Error('down')));
    component.remove(post(2));
    expect(notifications.error).toHaveBeenCalledTimes(3);
  });

  it('keeps the text as a draft, or schedules it at the time chosen', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    component.keep();
    expect(network.createDraft).not.toHaveBeenCalled();
    network.createDraft.and.returnValue(of({ id: 1, text: 'Plus tard', scheduledAt: null, updatedAt: '2026-11-14T10:00:00' }));
    component.draft.setValue(' Plus tard ');
    component.keep();
    expect(network.createDraft).toHaveBeenCalledWith('Plus tard', null);
    expect(notifications.success).toHaveBeenCalledWith('network.drafts.keptDone');
    component.draft.setValue('Lundi');
    component.scheduleAt.setValue('2026-11-16T09:00');
    component.keep();
    expect(network.createDraft).toHaveBeenCalledWith('Lundi', new Date('2026-11-16T09:00').toISOString());
    expect(notifications.success).toHaveBeenCalledWith('network.drafts.scheduledDone');
    expect(component.scheduleAt.value).toBe('');
    for (const [status, key] of [[400, 'network.drafts.scheduleError'], [500, 'network.drafts.saveError']] as const) {
      network.createDraft.and.returnValue(throwError(() => new HttpErrorResponse({ status })));
      component.draft.setValue('Encore');
      component.keep();
      expect(notifications.error).toHaveBeenCalledWith(key);
    }
  });

  it('suggests members to follow with their reason, and follows one', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    const text = (handle: string) => element.querySelector(`[data-suggestion="${handle}"] .why`)?.textContent?.replace(/\s+/g, ' ').trim();
    expect(text('ines_b')).toBe('Followed by Léo and 2 other members you follow');
    expect(text('jade_k')).toBe('Followed by Léo, whom you follow');
    expect(text('paul_d')).toBe('Also on Kafka');
    expect(text('zoe_l')).toBe('Also in Lyon');
    expect(text('marc_c')).toBe('In your contacts');
    network.follow.and.returnValue(of({} as MemberPage));
    (element.querySelector('[data-suggestion="paul_d"] [data-suggestion-follow]') as HTMLButtonElement).click();
    expect(network.follow).toHaveBeenCalledWith('paul_d');
    expect(fixture.componentInstance.suggestions.length).toBe(4);
    expect(network.feed).toHaveBeenCalledTimes(2);
    network.follow.and.returnValue(throwError(() => new HttpErrorResponse({ status: 429 })));
    fixture.componentInstance.follow(fixture.componentInstance.suggestions[0]);
    expect(notifications.error).toHaveBeenCalledWith('network.page.tooManyFollows');
    network.follow.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    fixture.componentInstance.follow(fixture.componentInstance.suggestions[0]);
    expect(notifications.error).toHaveBeenCalledWith('network.page.followError');
  });

  it('asks a visitor to join, says when the posts cannot be loaded, and when there are none', () => {
    let fixture = create(throwError(() => new HttpErrorResponse({ status: 409 })));
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-feed-not-member]')).not.toBeNull();
    TestBed.resetTestingModule();
    fixture = create(throwError(() => new HttpErrorResponse({ status: 500 })));
    expect(notifications.error).toHaveBeenCalledWith('network.feed.loadError');
    TestBed.resetTestingModule();
    fixture = create(of({ posts: [], page: 0, more: false }));
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-feed-empty]')).not.toBeNull();
  });

  it('fills the composer with what another page shared, once', () => {
    history.replaceState({ networkShare: { text: 'Une mission que je ne prends pas.', kind: 'MISSION', details: null } }, '');
    const fixture = create();
    expect(fixture.componentInstance.draft.value).toBe('Une mission que je ne prends pas.');
    expect(fixture.componentInstance.shared?.kind).toBe('MISSION');
    expect((history.state as Record<string, unknown>)['networkShare']).toBeNull();
  });
});
