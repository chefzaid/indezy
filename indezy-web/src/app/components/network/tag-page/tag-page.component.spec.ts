import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { MemberCard, NetworkPost, TagPage } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { TagPageComponent } from './tag-page.component';

describe('TagPageComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const leo: MemberCard = { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: ['Kafka'], city: null,
    remote: false, availability: null, availabilityDate: null, languages: [], verified: false };
  const post = (id: number): NetworkPost => ({ id, text: `#kafka ${id}`, tags: ['kafka'], createdAt: '2026-11-14T10:00:00', own: false,
    likes: 0, liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, kind: 'POST' as const, details: null, answerCommentId: null, author: leo });
  const page: TagPage = { tag: 'kafka', followed: false, posts: { posts: [post(2)], page: 0, more: true }, members: [leo] };

  function create(answer = of(page)): ComponentFixture<TagPageComponent> {
    network = jasmine.createSpyObj('NetworkService', ['tagPage', 'followTag']);
    network.tagPage.and.returnValue(answer);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [TagPageComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ tag: 'kafka' })) } },
        { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications }]
    });
    const fixture = TestBed.createComponent(TagPageComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the tag\'s members and posts, pages through the posts and follows the tag', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(network.tagPage).toHaveBeenCalledWith('kafka');
    expect(element.querySelector('[data-tag-members] a')?.getAttribute('href')).toBe('/m/leo_m');
    expect(element.querySelector('[data-tag-posts] [data-post="2"]')).not.toBeNull();
    network.tagPage.and.returnValue(of({ ...page, posts: { posts: [post(1)], page: 1, more: false } }));
    (element.querySelector('[data-tag-more]') as HTMLButtonElement).click();
    expect(network.tagPage).toHaveBeenCalledWith('kafka', 1);
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
});
