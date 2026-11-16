import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';

import { MemberPage, MemberSuggestion, NetworkPost, NetworkPosts } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ConfidentialityGuardService } from '../confidentiality/confidentiality-guard.service';
import { NEWER_EVERY_MS, NetworkFeedComponent } from './network-feed.component';
import en from '../../../../assets/i18n/en.json';

describe('NetworkFeedComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let guard: jasmine.SpyObj<ConfidentialityGuardService>;
  const post = (id: number, own = false): NetworkPost => ({ id, text: `Post ${id}`, tags: [], createdAt: '2026-11-14T10:00:00', own, likes: 0, liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, kind: 'POST' as const, details: null, answerCommentId: null, poll: null, taken: null, useful: null, reactions: [], myReaction: null,
    author: { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: [], city: null, remote: false,
      availability: null, availabilityDate: null, languages: [], verified: false } });

  const suggestion = (handle: string, reason: MemberSuggestion['reason'], followedBy = 0): MemberSuggestion => ({ reason, followedBy,
    followedByName: followedBy ? 'Léo' : null, sharedSkills: reason === 'SAME_STACK' ? ['Kafka'] : [], esn: reason === 'SAME_ESN' ? 'Acme Conseil' : null, card: { ...post(0).author, handle,
      city: 'Lyon' } });

  function create(feed = of({ posts: [post(2), post(1, true)], page: 0, more: true })): ComponentFixture<NetworkFeedComponent> {
    network = jasmine.createSpyObj('NetworkService', ['feed', 'post', 'deletePost', 'followedTags', 'suggestions', 'follow', 'createDraft', 'forYou', 'membership', 'trendingTags', 'newerCount']);
    network.newerCount.and.returnValue(of(0));
    network.trendingTags.and.returnValue(of([{ tag: 'kafka', posts: 4 }, { tag: 'java', posts: 1 }]));
    network.feed.and.returnValue(feed);
    network.followedTags.and.returnValue(of(['kafka']));
    network.membership.and.returnValue(of({ joined: true, handle: 'camille', card: null, privateCard: false, esnSuggestions: false,
      availability: { mode: 'AUTO', date: null }, fields: { photo: true, headline: true, stack: true, location: true, availability: true,
        languages: true, remote: false } }));
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

  it('loads the next page once, near the end, shows the trending tags and offers the newer posts', fakeAsync(() => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-trending-tag="kafka"]')?.textContent).toContain('4 posts');
    expect(element.querySelector('[data-trending-tag="java"]')?.textContent).toContain('1 post');
    expect(element.querySelector('[data-trending-tag="kafka"]')?.getAttribute('href')).toBe('/network/tags/kafka');

    const next = new Subject<NetworkPosts>();
    network.feed.and.returnValue(next);
    component.loadMore();
    component.loadMore();
    fixture.detectChanges();
    expect(network.feed).toHaveBeenCalledTimes(2);
    expect(element.querySelector('[data-feed-more]')).toBeNull();
    next.next({ posts: [post(0)], page: 1, more: false });
    component.loadMore();
    expect(network.feed).toHaveBeenCalledTimes(2);

    network.newerCount.and.returnValue(of(3));
    tick(NEWER_EVERY_MS);
    fixture.detectChanges();
    expect(network.newerCount).toHaveBeenCalledWith('2026-11-14T10:00:00');
    expect(element.querySelector('[data-feed-newer]')?.textContent).toContain('3 new posts');
    network.feed.and.returnValue(of({ posts: [post(5), post(2)], page: 0, more: false }));
    (element.querySelector('[data-feed-newer]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(component.posts.map(item => item.id)).toEqual([5, 2]);
    expect(element.querySelector('[data-feed-newer]')).toBeNull();
    network.newerCount.and.returnValue(of(1));
    component.checkNewer();
    fixture.detectChanges();
    expect(element.querySelector('[data-feed-newer]')?.textContent).toContain('1 new post');

    // Not on For you, nor when the server cannot say.
    network.forYou.and.returnValue(of({ posts: [post(4)], page: 0, more: false }));
    component.show('forYou');
    expect(component.newer).toBe(0);
    component.checkNewer();
    expect(network.newerCount).toHaveBeenCalledTimes(2);
    component.show('following');
    network.newerCount.and.returnValue(throwError(() => new Error('down')));
    component.checkNewer();
    expect(component.newer).toBe(0);
    discardPeriodicTasks();
  }));

  it('opens the composer full screen on a phone, focused, and closes it on Escape or once published', fakeAsync(() => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    const opener = element.querySelector('[data-composer-open]') as HTMLButtonElement;
    spyOnProperty(document, 'activeElement').and.returnValue(opener);
    const refocus = spyOn(opener, 'focus');
    opener.click();
    fixture.detectChanges();
    tick();
    const form = element.querySelector('[data-composer]')!;
    expect(form.classList).toContain('full');
    expect(form.getAttribute('role')).toBe('dialog');
    expect(element.querySelector('[data-composer-fab]')).toBeNull();
    form.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    tick();
    expect(component.composerOpen).toBeFalse();
    expect(refocus).toHaveBeenCalled();
    component.closeComposer();
    expect(component.composerOpen).toBeFalse();

    (element.querySelector('[data-composer-fab]') as HTMLButtonElement).click();
    network.post.and.returnValue(of(post(9, true)));
    component.draft.setValue('Bonjour');
    component.publish();
    tick();
    expect(component.composerOpen).toBeFalse();
    discardPeriodicTasks();
  }));

  it('opens the composer on a phone with what was shared to the network', fakeAsync(() => {
    expect(NetworkFeedComponent.onPhone()).toBe(window.innerWidth < 600);
    spyOn(NetworkFeedComponent, 'onPhone').and.returnValue(true);
    spyOnProperty(history, 'state').and.returnValue({ networkShare: { text: 'Un an de freelance', kind: 'POST', details: null } });
    spyOn(history, 'replaceState');
    const fixture = create();
    tick();
    expect(fixture.componentInstance.composerOpen).toBeTrue();
    expect(fixture.componentInstance.draft.value).toBe('Un an de freelance');
    discardPeriodicTasks();
  }));

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

  it('shows For you, ranked by the server, and the members followed again', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    network.forYou.and.returnValue(of({ posts: [post(9)], page: 0, more: false }));
    component.show('forYou');
    expect(network.forYou).toHaveBeenCalledWith(0);
    expect(component.posts.map(found => found.id)).toEqual([9]);
    expect(component.more).toBeFalse();
    component.show('forYou');
    expect(network.forYou).toHaveBeenCalledTimes(1);
    network.feed.calls.reset();
    component.show('following');
    expect(network.feed).toHaveBeenCalledWith(0);
  });

  it('says why each member is suggested, opens the time to publish on its own, and shows the reader\'s avatar', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const card = { handle: 'paul_d', firstName: 'Paul', lastName: 'Dupont', avatar: null, headline: null, stack: [], city: 'Lyon',
      remote: false, availability: null, availabilityDate: null, languages: [], verified: false };
    const suggestion = (reason: MemberSuggestion['reason'], followedBy = 0): MemberSuggestion =>
      ({ card, reason, followedBy, followedByName: 'Léo', sharedSkills: ['Kafka'], esn: 'Acme Conseil' });
    expect(component.reason(suggestion('CONTACT'))).toBe('In your contacts');
    expect(component.reason(suggestion('FOLLOWED_BY', 1))).toBe('Followed by Léo, whom you follow');
    expect(component.reason(suggestion('FOLLOWED_BY', 3))).toBe('Followed by Léo and 2 other members you follow');
    expect(component.reason(suggestion('SAME_STACK'))).toBe('Also on Kafka');
    expect(component.reason(suggestion('SAME_ESN'))).toBe('Also through Acme Conseil');
    expect(component.reason(suggestion('SAME_CITY'))).toBe('Also in Lyon');

    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-schedule-at]')).toBeNull();
    (element.querySelector('[data-schedule-toggle]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('[data-schedule-at]')).not.toBeNull();
    component.scheduleAt.setValue('2026-11-20T09:00');
    component.toggleScheduling();
    expect(component.scheduleAt.value).toBe('');
    expect(component.scheduling).toBeFalse();

    component.me = card;
    fixture.detectChanges();
    expect(element.querySelector('[data-composer] app-member-avatar')).not.toBeNull();
    expect(element.querySelector('[data-feed-me]')?.getAttribute('href')).toBe('/m/paul_d');
  });
});
