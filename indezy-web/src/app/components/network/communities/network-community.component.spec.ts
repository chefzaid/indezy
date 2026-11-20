import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';

import { CommunityMember, CommunityPage, MemberCard, NetworkPost } from '../../../models';
import { NetworkCommunitiesService } from '../../../services/network/network-communities.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { NetworkCommunityComponent } from './network-community.component';
import en from '../../../../assets/i18n/en.json';
import { stubNetworkNav } from '../nav/network-nav.stub';
import { stubNetworkColumns } from '../side/network-columns.stub';

describe('NetworkCommunityComponent', () => {
  let communities: jasmine.SpyObj<NetworkCommunitiesService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  const card = (handle: string): MemberCard => ({ handle, firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: [],
    city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false });
  const page = (changes: Partial<CommunityPage['community']> = {}, creator = false): CommunityPage => ({ community: { slug: 'kafka',
    name: 'Kafka', description: 'Les fans', members: 3, joined: true, moderator: true, ...changes }, moderators: [card('alice_d')], creator });
  const post = (id: number, own = false) => ({ id, text: `Post ${id}`, own, author: card('leo_m'), tags: [], createdAt: '2026-11-19T10:00:00',
    likes: 0, liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0,
    repostsOff: false, kind: 'POST', details: null, answerCommentId: null, poll: null, taken: null, reactions: [], myReaction: null,
    preview: null }) as NetworkPost;

  function create(read: Observable<CommunityPage> = of(page())) {
    communities = jasmine.createSpyObj('NetworkCommunitiesService', ['page', 'posts', 'post', 'join', 'leave', 'members', 'removePost',
      'removeMember', 'setModerator', 'update', 'remove']);
    communities.page.and.returnValue(read);
    communities.posts.and.returnValue(of({ posts: [post(2), post(1, true)], page: 0, more: true }));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    confirm.confirm.and.returnValue(of(true));
    TestBed.configureTestingModule({
      imports: [NetworkCommunityComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ slug: 'kafka' })) } },
        { provide: NetworkCommunitiesService, useValue: communities }, { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }]
    });
    stubNetworkNav(NetworkCommunityComponent);
    stubNetworkColumns(NetworkCommunityComponent);
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(NetworkCommunityComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the community and its feed, posts in it, pages through it and removes a post as a moderator', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-community-page="kafka"] h1')?.textContent).toContain('Kafka');
    expect(element.querySelector('.mods')?.textContent).toContain('Léo Martin');
    // A moderator removes others' posts, not their own.
    expect(element.querySelector('[data-community-remove-post="2"]')).not.toBeNull();
    expect(element.querySelector('[data-community-remove-post="1"]')).toBeNull();
    communities.post.and.returnValue(of(post(3, true)));
    component.text.setValue('Bonjour');
    component.publish();
    expect(communities.post).toHaveBeenCalledWith('kafka', 'Bonjour');
    expect(component.posts.map(item => item.id)).toEqual([3, 2, 1]);
    communities.post.and.returnValue(of(null));
    component.text.setValue('Achetez');
    component.publish();
    expect(notifications.success).toHaveBeenCalledWith('network.feed.held');
    communities.post.and.returnValue(throwError(() => new Error('down')));
    component.text.setValue('Encore');
    component.publish();
    expect(notifications.error).toHaveBeenCalledWith('network.feed.postError');
    communities.posts.and.returnValue(of({ posts: [post(0)], page: 1, more: false }));
    (element.querySelector('[data-community-more]') as HTMLButtonElement).click();
    expect(communities.posts).toHaveBeenCalledWith('kafka', 1);
    communities.removePost.and.returnValue(of(undefined));
    component.removePost(post(2));
    expect(communities.removePost).toHaveBeenCalledWith('kafka', 2);
    expect(component.posts.map(item => item.id)).not.toContain(2);
  });

  it('joins and leaves, a member removed being told so', () => {
    const fixture = create(of(page({ joined: false, moderator: false })));
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-community-composer]')).toBeNull();
    expect(element.querySelector('[data-community-join-hint]')).not.toBeNull();
    communities.join.and.returnValue(of(page({ joined: true, moderator: false })));
    (element.querySelector('[data-community-membership]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('[data-community-composer]')).not.toBeNull();
    communities.leave.and.returnValue(of(page({ joined: false, moderator: false })));
    component.toggleMembership();
    expect(communities.leave).toHaveBeenCalledWith('kafka');
    communities.join.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.toggleMembership();
    expect(notifications.error).toHaveBeenCalledWith('network.communities.removedFrom');
  });

  it('lists the members, the creator naming moderators and a moderator removing a member', () => {
    const fixture = create(of(page({}, true)));
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    const members: CommunityMember[] = [{ card: card('alice_d'), moderator: true }, { card: card('leo_m'), moderator: false }];
    communities.members.and.returnValue(of(members));
    (element.querySelector('[data-community-tab-members]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelectorAll('[data-community-member]').length).toBe(2);
    expect(element.querySelector('[data-community-remove-member="alice_d"]')).toBeNull();
    communities.setModerator.and.returnValue(of([{ card: card('alice_d'), moderator: true }, { card: card('leo_m'), moderator: true }]));
    (element.querySelector('[data-community-moderator="leo_m"]') as HTMLButtonElement).click();
    expect(communities.setModerator).toHaveBeenCalledWith('kafka', 'leo_m', true);
    communities.removeMember.and.returnValue(of(undefined));
    component.removeMember({ card: card('ines_b'), moderator: false });
    expect(communities.removeMember).toHaveBeenCalledWith('kafka', 'ines_b');
    expect(component.page?.community.members).toBe(2);
  });

  it('edits and deletes the community, and says when it does not exist', () => {
    let fixture = create(of(page({}, true)));
    const component = fixture.componentInstance;
    component.startEditing();
    expect(component.name.value).toBe('Kafka');
    component.name.setValue('Kafka Lyon');
    communities.update.and.returnValue(of(page({ name: 'Kafka Lyon' }, true)));
    component.saveEdit();
    expect(communities.update).toHaveBeenCalledWith('kafka', 'Kafka Lyon', 'Les fans');
    expect(component.editing).toBeFalse();
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    communities.remove.and.returnValue(of(undefined));
    component.deleteCommunity();
    expect(navigate).toHaveBeenCalledWith(['/network/communities']);
    TestBed.resetTestingModule();
    fixture = create(throwError(() => new Error('404')));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-community-missing]')).not.toBeNull();
  });
});
