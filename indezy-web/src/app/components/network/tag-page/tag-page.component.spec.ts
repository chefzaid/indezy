import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';

import { MemberCard, NetworkPost, Offer, OfferPage, TagPage } from '../../../models';
import { OfferService } from '../../../services/offer/offer.service';
import { CapabilitiesService, DEFAULT_CAPABILITIES } from '../../../services/admin/capabilities.service';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { TagPageComponent } from './tag-page.component';
import { stubNetworkNav } from '../nav/network-nav.stub';

describe('TagPageComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const leo: MemberCard = { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: ['Kafka'], city: null,
    remote: false, availability: null, availabilityDate: null, languages: [], verified: false };
  const post = (id: number): NetworkPost => ({ id, text: `#kafka ${id}`, tags: ['kafka'], createdAt: '2026-11-14T10:00:00', own: false,
    likes: 0, liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, kind: 'POST' as const, details: null, answerCommentId: null, poll: null, taken: null, reactions: [], myReaction: null, preview: null, author: leo });
  const page: TagPage = { tag: 'kafka', followed: false, posts: { posts: [post(2)], page: 0, more: true }, members: [leo] };

  let offers: jasmine.SpyObj<OfferService>;
  const offer = (id: number, changes: Partial<Offer> = {}): Offer => ({ id, site: 'FREEWORK', siteName: 'Free-Work',
    url: `https://example.test/${id}`, title: `Lead Kafka ${id}`, city: 'Lyon', rateMin: 600, rateMax: 700, ...changes } as Offer);
  const found = (content: Offer[]): OfferPage => ({ content, page: 0, size: 5, totalElements: content.length, totalPages: 1 } as OfferPage);

  function create(answer = of(page), offerFeed = true,
                  missions: Observable<OfferPage> = of(found([offer(1), offer(2, { rateMin: undefined, rateMax: 650, city: undefined })])))
    : ComponentFixture<TagPageComponent> {
    offers = jasmine.createSpyObj('OfferService', ['searchQuietly']);
    offers.searchQuietly.and.returnValue(missions);
    network = jasmine.createSpyObj('NetworkService', ['tagPage', 'followTag']);
    network.tagPage.and.returnValue(answer);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [TagPageComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ tag: 'kafka' })) } },
        { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications },
        { provide: OfferService, useValue: offers }, { provide: CapabilitiesService, useValue: { snapshot: () => ({ admin: false,
          features: { ...DEFAULT_CAPABILITIES.features, OFFER_FEED: offerFeed } }) } }]
    });
    stubNetworkNav(TagPageComponent);
    const fixture = TestBed.createComponent(TagPageComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('lists the offer feed\'s newest missions naming the skill, with a link to all of them', () => {
    const element: HTMLElement = create().nativeElement;
    expect(offers.searchQuietly).toHaveBeenCalledWith(jasmine.objectContaining({ skills: ['kafka'], sort: 'NEWEST', size: 5 }));
    const first = element.querySelector('[data-tag-mission="1"]')!;
    expect(first.getAttribute('href')).toBe('/offers/1');
    expect(first.textContent).toContain('600–700 €');
    expect(element.querySelector('[data-tag-mission="2"]')?.textContent).toContain('650 €');
    expect(element.querySelector('[data-tag-missions-all]')?.getAttribute('href')).toContain('skills=kafka');
  });

  it('leaves the missions out while the offer feed is off, or when they cannot be read', () => {
    let element: HTMLElement = create(of(page), false).nativeElement;
    expect(offers.searchQuietly).not.toHaveBeenCalled();
    expect(element.querySelector('[data-tag-missions]')).toBeNull();
    TestBed.resetTestingModule();
    element = create(of(page), true, throwError(() => new Error('down'))).nativeElement;
    expect(offers.searchQuietly).toHaveBeenCalled();
    expect(element.querySelector('[data-tag-missions]')).toBeNull();
    TestBed.resetTestingModule();
    element = create(of(page), true, of(found([]))).nativeElement;
    expect(element.querySelector('[data-tag-missions]')?.textContent).toContain('network.tags.noMissions');
  });

  it('shows the tag\'s members and posts, pages through the posts and follows the tag', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(network.tagPage).toHaveBeenCalledWith('kafka');
    expect(element.querySelector('[data-tag-members] a')?.getAttribute('href')).toBe('/m/leo_m');
    expect(element.querySelector('[data-tag-posts] [data-post="2"]')).not.toBeNull();
    network.tagPage.and.returnValue(of({ ...page, posts: { posts: [post(1)], page: 1, more: false } }));
    (element.querySelector('[data-tag-more]') as HTMLButtonElement).click();
    expect(network.tagPage).toHaveBeenCalledWith('kafka', 1, 'recent');
    expect(component.posts.map(item => item.id)).toEqual([2, 1]);
    network.followTag.and.returnValue(of(['kafka']));
    component.toggleFollow();
    expect(network.followTag).toHaveBeenCalledWith('kafka', true);
    expect(component.page?.followed).toBeTrue();
    network.followTag.and.returnValue(throwError(() => new Error('down')));
    component.toggleFollow();
    network.tagPage.and.returnValue(throwError(() => new Error('down')));
    component.more();
    expect(notifications.error).toHaveBeenCalledWith('network.tags.followError');
    expect(notifications.error).toHaveBeenCalledWith('network.tags.loadError');
  });

  it('says when the tag cannot be loaded', () => {
    create(throwError(() => new Error('down')));
    expect(notifications.error).toHaveBeenCalledWith('network.tags.loadError');
  });

  it('lists the posts the most liked first, or the newest first again', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    network.tagPage.and.returnValue(of({ ...page, posts: { posts: [post(7)], page: 0, more: false } }));
    component.order('liked');
    expect(network.tagPage).toHaveBeenCalledWith('kafka', 0, 'liked');
    expect(component.posts.map(found => found.id)).toEqual([7]);
    network.tagPage.calls.reset();
    component.order('liked');
    expect(network.tagPage).not.toHaveBeenCalled();
    network.tagPage.and.returnValue(throwError(() => new Error('down')));
    component.order('recent');
    expect(notifications.error).toHaveBeenCalledWith('network.tags.loadError');
  });
});
