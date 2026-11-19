import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { MemberCard, MemberDirectory, MemberPage, NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { NetworkSearchResultsComponent, SEARCH_PREVIEW } from './network-search-results.component';
import en from '../../../../assets/i18n/en.json';
import { stubNetworkNav } from '../nav/network-nav.stub';
import { stubNetworkColumns } from '../side/network-columns.stub';

describe('NetworkSearchResultsComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let query: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  const member = (handle: string): MemberCard => ({ handle, firstName: 'Léo', lastName: 'Martin', avatar: null, headline: 'Lead Kafka',
    stack: ['Kafka', 'Java'], city: 'Lyon', remote: false, availability: null, availabilityDate: null, languages: [], verified: false });
  const directory = (handles: string[], total = handles.length, page = 0, changes: Partial<MemberDirectory> = {}): MemberDirectory => ({
    members: handles.map(member), total, page, size: 20, followed: [], requested: [], privateCards: [], ...changes });
  const post = (id: number): NetworkPost => ({ id, text: `Kafka ${id}`, tags: [], createdAt: '2026-11-14T10:00:00', own: false, likes: 0,
    liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false,
    kind: 'POST', details: null, answerCommentId: null, poll: null, taken: null, reactions: [], myReaction: null, preview: null,
    author: member('leo_m') });

  function create(params: Record<string, string> = { q: 'kafka' }): ComponentFixture<NetworkSearchResultsComponent> {
    network = jasmine.createSpyObj('NetworkService', ['searchMembers', 'searchPosts', 'suggestTags', 'follow', 'unfollow']);
    network.searchMembers.and.returnValue(of(directory(['leo_m', 'ines_b', 'paul_d', 'zoe_l'], 24, 0,
      { followed: ['ines_b'], privateCards: ['paul_d'] })));
    network.searchPosts.and.returnValue(of({ posts: [post(1), post(2), post(3), post(4)], page: 0, more: true }));
    network.suggestTags.and.returnValue(of(['kafka', 'kafka-streams']));
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    query = new BehaviorSubject(convertToParamMap(params));
    TestBed.configureTestingModule({
      imports: [NetworkSearchResultsComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: ActivatedRoute, useValue: { queryParamMap: query } },
        { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications }]
    });
    stubNetworkNav(NetworkSearchResultsComponent);
    stubNetworkColumns(NetworkSearchResultsComponent);
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(NetworkSearchResultsComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the first members, tags and posts found under All, each with See all', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    expect(network.searchMembers).toHaveBeenCalledWith('kafka', 0);
    expect(network.searchPosts).toHaveBeenCalledWith('kafka', 0);
    expect(element.querySelector('[data-search-title]')?.textContent).toContain('Results for “kafka”');
    expect(element.querySelectorAll('[data-search-result-member]').length).toBe(SEARCH_PREVIEW);
    expect(element.querySelector('[data-search-members] h2')?.textContent).toContain('24');
    expect(element.querySelector('[data-search-result-member="leo_m"] .facts')?.textContent).toContain('Lyon');
    expect(element.querySelector('[data-search-result-tag="kafka-streams"]')?.getAttribute('href')).toBe('/network/tags/kafka-streams');
    expect(element.querySelectorAll('[data-search-posts] app-post-card').length).toBe(SEARCH_PREVIEW);
    expect(element.querySelector('[data-search-follow="ines_b"]')?.textContent).toContain('Following');
    expect(element.querySelector('[data-search-follow="paul_d"]')?.textContent).toContain('Ask to follow');
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    (element.querySelector('[data-search-all-members]') as HTMLButtonElement).click();
    expect(navigate).toHaveBeenCalledWith([], jasmine.objectContaining({ queryParams: { tab: 'members' }, queryParamsHandling: 'merge' }));
  });

  it('shows a tab alone, pages through it, and reads the search again when it changes', () => {
    const fixture = create({ q: 'kafka', tab: 'members' });
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelectorAll('[data-search-result-member]').length).toBe(4);
    expect(element.querySelector('[data-search-posts]')).toBeNull();
    network.searchMembers.and.returnValue(of(directory(['jade_k'], 24, 1)));
    (element.querySelector('[data-search-more-members]') as HTMLButtonElement).click();
    expect(network.searchMembers).toHaveBeenCalledWith('kafka', 1);
    expect(component.members.length).toBe(5);

    query.next(convertToParamMap({ q: 'kafka', tab: 'posts' }));
    fixture.detectChanges();
    expect(network.searchPosts).toHaveBeenCalledTimes(1);
    expect(element.querySelectorAll('[data-search-posts] app-post-card').length).toBe(4);
    network.searchPosts.and.returnValue(of({ posts: [post(5)], page: 1, more: false }));
    (element.querySelector('[data-search-more-posts]') as HTMLButtonElement).click();
    expect(component.posts.map(found => found.id)).toEqual([1, 2, 3, 4, 5]);
    component.removePost(post(5));
    expect(component.posts.length).toBe(4);

    network.searchMembers.and.returnValue(of(directory([])));
    network.searchPosts.and.returnValue(of({ posts: [], page: 0, more: false }));
    network.suggestTags.and.returnValue(of([]));
    query.next(convertToParamMap({ q: 'nothing at all' }));
    fixture.detectChanges();
    expect(network.suggestTags).toHaveBeenCalledTimes(1);
    expect(element.querySelector('[data-search-none]')?.textContent).toContain('nothing at all');
  });

  it('follows a member found, asks to follow a private card, and undoes it', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    network.follow.and.returnValue(of({ followed: true, requested: false } as MemberPage));
    component.toggleFollow('leo_m');
    expect(component.followLabel('leo_m')).toBe('network.directory.following');
    network.follow.and.returnValue(of({ followed: false, requested: true } as MemberPage));
    component.toggleFollow('paul_d');
    expect(component.followLabel('paul_d')).toBe('network.page.requested');
    network.unfollow.and.returnValue(of({ followed: false, requested: false } as MemberPage));
    component.toggleFollow('paul_d');
    expect(network.unfollow).toHaveBeenCalledWith('paul_d');
    expect(component.followLabel('paul_d')).toBe('network.page.askToFollow');
    network.follow.and.returnValue(throwError(() => new HttpErrorResponse({ status: 429 })));
    component.toggleFollow('zoe_l');
    expect(notifications.error).toHaveBeenCalledWith('network.page.tooManyFollows');
    expect(component.pending.has('zoe_l')).toBeFalse();
  });

  it('asks for two characters, and says when the search fails', () => {
    let fixture = create({ q: 'k' });
    expect(network.searchMembers).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[data-search-short]')).not.toBeNull();
    TestBed.resetTestingModule();
    fixture = create({ q: 'kafka lyon' });
    expect(network.suggestTags).not.toHaveBeenCalled();
    TestBed.resetTestingModule();
    network = undefined as never;
    fixture = create({ q: 'kafka', tab: 'tags' });
    network.searchMembers.and.returnValue(throwError(() => new Error('down')));
    query.next(convertToParamMap({ q: 'spark', tab: 'tags' }));
    fixture.detectChanges();
    expect(notifications.error).toHaveBeenCalledWith('network.search.error');
  });
});
