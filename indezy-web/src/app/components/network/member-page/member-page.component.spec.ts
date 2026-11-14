import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { MemberCard, MemberPage } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { MemberPageComponent } from './member-page.component';
import en from '../../../../assets/i18n/en.json';

describe('MemberPageComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const card = (handle: string, changes: Partial<MemberCard> = {}): MemberCard => ({ handle, firstName: 'Camille', lastName: 'Durand',
    avatar: null, headline: 'Architecte Java', stack: ['Java'], city: 'Lyon', remote: false, availability: 'ON_MISSION',
    availabilityDate: '2027-03-31', languages: [], verified: false, ...changes });
  const page: MemberPage = { card: card('camille'), followers: 2, following: 1, sharedSkills: ['Java'], followed: false, followsYou: true,
    own: false, muted: false, blocked: false };
  let confirm: jasmine.SpyObj<ConfirmDialogService>;

  function create(read = of(page)): ComponentFixture<MemberPageComponent> {
    network = jasmine.createSpyObj('NetworkService', ['page', 'follow', 'unfollow', 'followers', 'following', 'removeFollower', 'mute',
      'block', 'memberPosts', 'deletePost']);
    network.memberPosts.and.returnValue(of({ posts: [], page: 0, more: false }));
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    network.page.and.returnValue(read);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [MemberPageComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ handle: 'camille' })) } },
        { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }]
    });
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
    const post = (id: number) => ({ id, text: `Post ${id}`, tags: [], createdAt: '2026-11-14T10:00:00', own: true, likes: 0, liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: false,
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
