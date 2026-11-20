import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';

import { Conversation, MemberCard, MemberPage, NetworkMembership, NetworkPost, PastMission } from '../../../models';
import { ShownTestimonial } from '../../../models/testimonial.models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { MemberPageComponent } from './member-page.component';
import { ReportDialogComponent } from '../report-dialog/report-dialog.component';
import { NetworkMessagesService } from '../../../services/network/network-messages.service';
import en from '../../../../assets/i18n/en.json';
import { stubNetworkNav } from '../nav/network-nav.stub';
import { stubNetworkColumns } from '../side/network-columns.stub';

describe('MemberPageComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const card = (handle: string, changes: Partial<MemberCard> = {}): MemberCard => ({ handle, firstName: 'Camille', lastName: 'Durand',
    avatar: null, headline: 'Architecte Java', stack: ['Java'], city: 'Lyon', remote: false, availability: 'ON_MISSION',
    availabilityDate: '2027-03-31', languages: [], verified: false, ...changes });
  const page: MemberPage = { card: card('camille'), followers: 2, following: 1, sharedSkills: ['Java'], followed: false, followsYou: true,
    own: false, muted: false, blocked: false, privateCard: false, requested: false };
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let messages: jasmine.SpyObj<NetworkMessagesService>;

  let query: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let mutualFollows = of({ count: 0, members: [] as MemberCard[] });
  let pinnedAnswer: Observable<NetworkPost> = throwError(() => new Error('not readable'));
  let postsAnswer = of({ posts: [] as NetworkPost[], page: 0, more: false });
  let aboutAnswer = of({ about: null as string | null, missions: [] as PastMission[] });

  function create(read = of(page), tab: string | null = null, recommended = of([] as ShownTestimonial[])): ComponentFixture<MemberPageComponent> {
    network = jasmine.createSpyObj('NetworkService', ['page', 'follow', 'unfollow', 'followers', 'following', 'removeFollower', 'mute',
      'block', 'memberPosts', 'deletePost', 'saved', 'drafts', 'recommendations', 'recordView', 'pageViews', 'activity', 'mutual', 'onePost', 'memberAbout', 'setOpenToWork']);
    network.memberAbout.and.returnValue(aboutAnswer);
    network.onePost.and.returnValue(pinnedAnswer);
    network.mutual.and.returnValue(mutualFollows);
    network.activity.and.returnValue(of({ postsThisWeek: 2, reachedThisWeek: 40, followersGained: 3, missionsPassedOn: 1, missionsTaken: 4 }));
    network.recordView.and.returnValue(of(undefined));
    network.pageViews.and.returnValue(of({ viewers: 0, viewsThisWeek: 0, named: [], anonymous: 0, privateViewing: false }));
    network.recommendations.and.returnValue(recommended);
    network.memberPosts.and.returnValue(postsAnswer);
    network.saved.and.returnValue(of({ page: { posts: [], page: 0, more: false }, tags: [] }));
    network.drafts.and.returnValue(of([]));
    query = new BehaviorSubject(convertToParamMap(tab ? { tab } : {}));
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    messages = jasmine.createSpyObj('NetworkMessagesService', ['open']);
    network.page.and.returnValue(read);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [MemberPageComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ handle: 'camille' })), queryParamMap: query,
          get snapshot() { return { queryParamMap: query.value }; } } },
        { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }, { provide: NetworkMessagesService, useValue: messages }]
    });
    stubNetworkNav(MemberPageComponent);
    stubNetworkColumns(MemberPageComponent);
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(MemberPageComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the card, the counts, the shared skills and that the member follows the viewer, then follows and unfollows', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    expect(network.page).toHaveBeenCalledWith('camille');
    expect(element.querySelector('[data-member-card="camille"]')).not.toBeNull();
    expect(element.querySelector('[data-followers]')?.textContent).toContain('2 followers');
    expect(element.querySelector('[data-follows-you]')).not.toBeNull();
    expect(element.querySelector('[data-shared-skills]')?.textContent).toContain('Java');

    network.follow.and.returnValue(of({ ...page, followed: true, followers: 3 }));
    fixture.componentInstance.toggleFollow();
    fixture.detectChanges();
    expect(element.querySelector('[data-follow]')?.textContent).toContain('Unfollow');
    network.unfollow.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.toggleFollow();
    expect(notifications.error).toHaveBeenCalledWith('network.page.followError');
    fixture.componentInstance.page = { ...page, followed: false };
    network.follow.and.returnValue(throwError(() => new HttpErrorResponse({ status: 429 })));
    fixture.componentInstance.toggleFollow();
    expect(notifications.error).toHaveBeenCalledWith('network.page.tooManyFollows');
    expect(fixture.componentInstance.isFollowing).toBeFalse();
  });

  it('lists the followers and the following, and lets the viewer remove a follower of their own page', () => {
    const fixture = create(of({ ...page, own: true, followsYou: false, sharedSkills: [] }));
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-follow]')).toBeNull();
    network.followers.and.returnValue(of([card('leo', { firstName: 'Léo' }), card('ines')]));
    component.show('followers');
    fixture.detectChanges();
    expect(element.querySelectorAll('[data-member-list="followers"] li').length).toBe(2);
    network.removeFollower.and.returnValue(of(undefined));
    (element.querySelector('[data-remove-follower="leo"]') as HTMLButtonElement).click();
    expect(component.members.map(member => member.handle)).toEqual(['ines']);
    expect(component.page?.followers).toBe(1);
    network.removeFollower.and.returnValue(throwError(() => new Error('down')));
    component.removeFollower(card('ines'));
    expect(notifications.error).toHaveBeenCalledWith('network.page.followError');

    network.following.and.returnValue(of([]));
    component.show('following');
    fixture.detectChanges();
    expect(element.querySelector('[data-member-list="following"]')?.textContent).toContain('No member yet.');
    network.following.and.returnValue(throwError(() => new Error('down')));
    component.show('following');
    expect(notifications.error).toHaveBeenCalledWith('network.page.listError');
  });

  it('links the viewer\'s own page to their card, colors the cover and goes back to the posts from a list', () => {
    const fixture = create(of({ ...page, own: true }));
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-edit-card]')).not.toBeNull();
    expect(component.cover).toContain('linear-gradient');
    network.followers.and.returnValue(of([card('leo')]));
    (element.querySelector('[data-followers]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('[data-member-list="followers"] app-member-avatar')).not.toBeNull();
    expect(element.querySelector('[data-member-posts]')).toBeNull();
    (element.querySelector('[data-tab-posts]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(component.list).toBeNull();
    expect(element.querySelector('[data-member-posts]')).not.toBeNull();
  });

  it('opens the reader\'s saved posts and drafts as tabs of their own page, from the address too', () => {
    const fixture = create(of({ ...page, own: true }), 'saved');
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(component.list).toBe('saved');
    expect(element.querySelector('app-saved-posts')).not.toBeNull();
    expect(element.querySelector('[data-tab-saved]')?.getAttribute('aria-selected')).toBe('true');
    expect(network.followers).not.toHaveBeenCalled();
    (element.querySelector('[data-tab-drafts]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('app-network-drafts')).not.toBeNull();
    // The card beside the feed asks for another tab of the same page.
    network.following.and.returnValue(of([card('leo')]));
    query.next(convertToParamMap({ tab: 'following' }));
    fixture.detectChanges();
    expect(component.list).toBe('following');
    query.next(convertToParamMap({ tab: 'nonsense' }));
    expect(component.list).toBeNull();
  });

  it('shows the recommendations the member approved in their tab, and asks for some on one\'s own page', () => {
    let fixture = create();
    let element: HTMLElement = fixture.nativeElement;
    expect(network.recommendations).toHaveBeenCalledWith('camille');
    // None approved: no tab on another member's page.
    expect(element.querySelector('[data-tab-recommendations]')).toBeNull();
    TestBed.resetTestingModule();
    const recommended = [{ authorName: 'Léa Martin', authorTitle: 'CTO, Acme', text: 'Rigoureuse et claire.' },
      { authorName: 'Paul Durand', authorTitle: null, text: 'Une refonte menée à temps.' }];
    fixture = create(of(page), 'recommendations', of(recommended));
    element = fixture.nativeElement;
    expect(element.querySelector('[data-tab-recommendations]')?.textContent).toContain('2');
    expect(element.querySelectorAll('[data-recommendation]').length).toBe(2);
    expect(element.querySelector('[data-recommendation]')?.textContent).toContain('CTO, Acme');
    expect(element.querySelector('[data-ask-recommendation]')).toBeNull();
    TestBed.resetTestingModule();
    fixture = create(of({ ...page, own: true }), 'recommendations');
    element = fixture.nativeElement;
    expect(element.querySelector('[data-tab-recommendations]')).not.toBeNull();
    expect(element.querySelector('[data-ask-recommendation]')?.getAttribute('href')).toBe('/profile?tab=public');
  });

  it('tells the member their page was viewed, never for one\'s own, and opens one\'s viewers in a tab', () => {
    let fixture = create();
    expect(network.recordView).toHaveBeenCalledWith('camille');
    expect(fixture.nativeElement.querySelector('[data-tab-viewers]')).toBeNull();
    TestBed.resetTestingModule();
    fixture = create(of({ ...page, own: true }), 'viewers');
    expect(network.recordView).not.toHaveBeenCalled();
    expect(fixture.componentInstance.list).toBe('viewers');
    expect(fixture.nativeElement.querySelector('app-network-viewers')).not.toBeNull();
    // Beside one's own page, one's activity.
    expect(network.activity).toHaveBeenCalledWith(true);
    expect(fixture.nativeElement.querySelector('[data-activity-reached]')?.textContent).toContain('40');
    expect(fixture.nativeElement.querySelector('[data-activity-taken]')?.textContent).toContain('4');
  });

  it('says which members the viewer follows follow this one too', () => {
    mutualFollows = of({ count: 3, members: [card('leo', { firstName: 'Léo' }), card('ines', { firstName: 'Inès' })] });
    let fixture = create();
    const component = fixture.componentInstance;
    expect(network.mutual).toHaveBeenCalledWith('camille');
    expect(fixture.nativeElement.querySelector('[data-mutual]')?.textContent).toContain('Followed by Léo and 2 other members you follow');
    expect(fixture.nativeElement.querySelector('[data-mutual]')?.getAttribute('href')).toBe('/m/camille?tab=followers');
    expect(component.mutualText({ count: 1, members: [card('leo', { firstName: 'Léo' })] }).key).toBe('network.page.mutualOne');
    expect(component.mutualText({ count: 2, members: [card('leo'), card('ines')] }).key).toBe('network.page.mutualTwo');
    TestBed.resetTestingModule();
    mutualFollows = of({ count: 0, members: [] });
    fixture = create();
    expect(fixture.nativeElement.querySelector('[data-mutual]')).toBeNull();
    TestBed.resetTestingModule();
    fixture = create(of({ ...page, own: true }));
    expect(network.mutual).not.toHaveBeenCalled();
  });

  it('shows the post the member pinned first, once, and follows the reader pinning another', () => {
    const post = (id: number): NetworkPost => ({ id, text: `Post ${id}`, tags: [], createdAt: '2026-11-14T10:00:00', own: false, likes: 0,
      liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false,
      kind: 'POST', details: null, answerCommentId: null, poll: null, taken: null, reactions: [], myReaction: null, preview: null,
      author: card('camille') });
    pinnedAnswer = of(post(2));
    postsAnswer = of({ posts: [post(2), post(1)], page: 0, more: false });
    const fixture = create(of({ ...page, pinnedPostId: 2 }));
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(network.onePost).toHaveBeenCalledWith(2);
    expect(element.querySelector('[data-pinned-post] [data-post-pinned]')).not.toBeNull();
    expect(element.querySelectorAll('[data-post]').length).toBe(2);
    expect(component.listedPosts.map(item => item.id)).toEqual([1]);
    component.pinChanged(post(1), true);
    expect(component.pinnedPost?.id).toBe(1);
    expect(component.page?.pinnedPostId).toBe(1);
    component.pinChanged(post(1), false);
    expect(component.pinnedPost).toBeNull();
    pinnedAnswer = throwError(() => new Error('not readable'));
    postsAnswer = of({ posts: [], page: 0, more: false });
  });

  it('shows what the member wrote about themselves and their missions, and invites one to write it on one\'s own page', () => {
    aboutAnswer = of({ about: 'Architecte Java, 12 ans.', missions: [{ role: 'Lead Kafka', stack: ['Java', 'Kafka'], start: '2026-02-02',
      end: null }, { role: 'Lead Java', stack: [], start: '2025-01-06', end: '2025-07-05' }] });
    let fixture = create();
    let element: HTMLElement = fixture.nativeElement;
    expect(network.memberAbout).toHaveBeenCalledWith('camille');
    expect(element.querySelector('[data-about-text]')?.textContent).toContain('Architecte Java, 12 ans.');
    expect(element.querySelectorAll('[data-past-mission]').length).toBe(2);
    expect(element.querySelector('[data-past-mission]')?.textContent).toContain('Java · Kafka');
    expect(element.querySelector('[data-past-mission]')?.textContent).toContain('now');
    TestBed.resetTestingModule();
    aboutAnswer = of({ about: null, missions: [] });
    fixture = create();
    expect(fixture.nativeElement.querySelector('[data-member-about-section]')).toBeNull();
    TestBed.resetTestingModule();
    fixture = create(of({ ...page, own: true }));
    element = fixture.nativeElement;
    expect(element.querySelector('[data-about-edit]')?.getAttribute('href')).toBe('/profile?tab=network');
  });

  it('says one is open to work in one click from one\'s own page, the badge then on one\'s photo', () => {
    const fixture = create(of({ ...page, own: true, card: card('camille', { availability: 'ON_MISSION' }) }));
    const element: HTMLElement = fixture.nativeElement;
    const toggle = () => element.querySelector('[data-open-to-work-toggle]') as HTMLButtonElement;
    expect(toggle().getAttribute('aria-pressed')).toBe('false');
    network.setOpenToWork.and.returnValue(of({ card: card('camille', { availability: 'AVAILABLE', availabilityDate: null }) } as NetworkMembership));
    toggle().click();
    fixture.detectChanges();
    expect(network.setOpenToWork).toHaveBeenCalledWith(true);
    expect(toggle().getAttribute('aria-pressed')).toBe('true');
    expect(notifications.success).toHaveBeenCalledWith('network.openToWork.onDone');
    network.setOpenToWork.and.returnValue(throwError(() => new Error('down')));
    toggle().click();
    expect(network.setOpenToWork).toHaveBeenCalledWith(false);
    expect(notifications.error).toHaveBeenCalledWith('network.page.followError');
    // Another member's page has no such switch.
    TestBed.resetTestingModule();
    expect(create().nativeElement.querySelector('[data-open-to-work-toggle]')).toBeNull();
  });

  it('keeps another member\'s page off the saved and drafts tabs', () => {
    const fixture = create(of(page), 'drafts');
    const element: HTMLElement = fixture.nativeElement;
    expect(fixture.componentInstance.list).toBeNull();
    expect(element.querySelector('[data-tab-saved]')).toBeNull();
    expect(element.querySelector('[data-tab-drafts]')).toBeNull();
    expect(element.querySelector('[data-tab-viewers]')).toBeNull();
    expect(element.querySelector('[data-member-posts]')).not.toBeNull();
  });

  it('asks to follow a private card, withdraws the request, and says its posts are for its followers', () => {
    const fixture = create(of({ ...page, privateCard: true }));
    const element: HTMLElement = fixture.nativeElement;
    const button = () => element.querySelector('[data-follow]') as HTMLButtonElement;
    expect(button().textContent).toContain('Ask to follow');
    expect(element.querySelector('[data-posts-private]')?.textContent).toContain('Camille');
    network.follow.and.returnValue(of({ ...page, privateCard: true, requested: true }));
    button().click();
    fixture.detectChanges();
    expect(network.follow).toHaveBeenCalledWith('camille');
    expect(button().textContent).toContain('Request sent');
    expect(button().getAttribute('aria-pressed')).toBe('true');
    network.unfollow.and.returnValue(of({ ...page, privateCard: true }));
    button().click();
    expect(network.unfollow).toHaveBeenCalledWith('camille');
    expect(fixture.componentInstance.followLabel({ ...page, privateCard: true, followed: true })).toBe('network.page.unfollow');
  });

  it('writes to a member once either follows the other, opening the conversation', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    messages.open.and.returnValue(of({ id: 7 } as Conversation));
    (element.querySelector('[data-message]') as HTMLButtonElement).click();
    expect(messages.open).toHaveBeenCalledWith('camille');
    expect(navigate).toHaveBeenCalledWith(['/network/messages', 7]);
    messages.open.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.message();
    expect(fixture.componentInstance.isOpening).toBeFalse();
    expect(notifications.error).toHaveBeenCalledWith('network.messages.openError');
    // Following them is enough; a request waiting is not.
    fixture.componentInstance.page = { ...page, followsYou: false, followed: true };
    fixture.detectChanges();
    expect(element.querySelector('[data-message]')).not.toBeNull();
    fixture.componentInstance.page = { ...page, followsYou: false, followed: false, requested: true };
    fixture.detectChanges();
    expect(element.querySelector('[data-message]')).toBeNull();
    // The server says when a message request can go to a member neither follows, or when they take none.
    fixture.componentInstance.page = { ...page, followsYou: false, followed: false, messageable: true };
    fixture.detectChanges();
    expect(element.querySelector('[data-message]')).not.toBeNull();
    fixture.componentInstance.page = { ...page, followsYou: true, followed: false, messageable: false };
    fixture.detectChanges();
    expect(element.querySelector('[data-message]')).toBeNull();
  });

  it('reports the member to the moderators', () => {
    const fixture = create();
    const open = spyOn(ReportDialogComponent, 'open').and.returnValue(of(true));
    fixture.componentInstance.report();
    expect(open).toHaveBeenCalledWith(jasmine.anything(), { target: 'members', id: 'camille' });
  });

  it('mutes and blocks the member, the block once confirmed, and unblocks them', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    network.mute.and.returnValue(of({ ...page, muted: true }));
    component.toggleMute();
    expect(network.mute).toHaveBeenCalledWith('camille', true);
    expect(notifications.success).toHaveBeenCalledWith('network.page.mutedDone');
    network.mute.and.returnValue(of(page));
    component.toggleMute();
    expect(network.mute).toHaveBeenCalledWith('camille', false);
    network.mute.and.returnValue(throwError(() => new Error('down')));
    component.toggleMute();
    expect(notifications.error).toHaveBeenCalledWith('network.page.followError');

    confirm.confirm.and.returnValue(of(false));
    component.toggleBlock();
    expect(network.block).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    network.block.and.returnValue(of({ ...page, blocked: true, muted: true, followed: false }));
    component.toggleBlock();
    fixture.detectChanges();
    expect(network.block).toHaveBeenCalledWith('camille', true);
    expect(element.querySelector('[data-blocked]')).not.toBeNull();
    expect(element.querySelector('[data-muted]')).not.toBeNull();
    expect(element.querySelector('[data-follow]')).toBeNull();
    network.block.and.returnValue(of(page));
    component.toggleBlock();
    expect(network.block).toHaveBeenCalledWith('camille', false);
    expect(confirm.confirm).toHaveBeenCalledTimes(2);
    expect(notifications.success).toHaveBeenCalledWith('network.page.unblockedDone');
    network.block.and.returnValue(throwError(() => new Error('down')));
    component.toggleBlock();
    expect(notifications.error).toHaveBeenCalledTimes(2);
  });

  it('refreshes the followers listed once the viewer follows', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    network.followers.and.returnValue(of([card('leo')]));
    component.show('followers');
    network.follow.and.returnValue(of({ ...page, followed: true }));
    component.toggleFollow();
    expect(network.followers).toHaveBeenCalledTimes(2);
  });

  it('shows the member\'s posts, pages through them, and lets the viewer delete their own', () => {
    const post = (id: number) => ({ id, text: `Post ${id}`, tags: [], createdAt: '2026-11-14T10:00:00', own: true, likes: 0, liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, kind: 'POST' as const, details: null, answerCommentId: null, poll: null, taken: null, reactions: [], myReaction: null, preview: null,
      author: card('camille') });
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(network.memberPosts).toHaveBeenCalledWith('camille', 0);
    expect(element.querySelector('[data-member-posts]')?.textContent).toContain('No post yet.');
    network.memberPosts.and.returnValue(of({ posts: [post(2)], page: 0, more: true }));
    component.loadPosts(0);
    fixture.detectChanges();
    expect(element.querySelector('[data-member-posts] [data-post="2"]')).not.toBeNull();
    network.memberPosts.and.returnValue(of({ posts: [post(1)], page: 1, more: false }));
    (element.querySelector('[data-posts-more]') as HTMLButtonElement).click();
    expect(component.posts.map(item => item.id)).toEqual([2, 1]);
    confirm.confirm.and.returnValue(of(true));
    network.deletePost.and.returnValue(of(undefined));
    component.removePost(post(2));
    expect(component.posts.map(item => item.id)).toEqual([1]);
    network.deletePost.and.returnValue(throwError(() => new Error('down')));
    component.removePost(post(1));
    expect(notifications.error).toHaveBeenCalledWith('network.feed.postError');
    network.memberPosts.and.returnValue(throwError(() => new Error('down')));
    component.loadPosts(0);
    expect(notifications.error).toHaveBeenCalledWith('network.feed.loadError');
  });

  for (const [status, problem] of [[409, 'notMember'], [404, 'notFound'], [500, 'error']] as const) {
    it(`says why the page cannot be shown (${status})`, () => {
      const fixture = create(throwError(() => new HttpErrorResponse({ status })));
      expect(fixture.componentInstance.problem).toBe(problem);
      expect((fixture.nativeElement as HTMLElement).querySelector(`[data-member-problem="${problem}"]`)).not.toBeNull();
    });
  }
});
