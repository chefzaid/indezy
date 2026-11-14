import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { SavedPostsComponent } from './saved-posts.component';

describe('SavedPostsComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const post = (id: number, tags: string[] = []): NetworkPost => ({ id, text: `Post ${id}`, tags, createdAt: '2026-11-14T10:00:00', own: false,
    likes: 0, liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: true, author: { handle: 'leo_m', firstName: 'Léo',
      lastName: 'Martin', avatar: null, headline: null, stack: [], city: null, remote: false, availability: null, availabilityDate: null,
      languages: [], verified: false } });

  function create(): ComponentFixture<SavedPostsComponent> {
    network = jasmine.createSpyObj('NetworkService', ['saved']);
    network.saved.and.returnValue(of({ page: { posts: [post(2, ['kafka']), post(1)], page: 0, more: true }, tags: ['kafka', 'spark'] }));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [SavedPostsComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications }]
    });
    const fixture = TestBed.createComponent(SavedPostsComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('lists the saved posts with their tags to filter by, pages through them and drops a post taken off', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelectorAll('[data-post]').length).toBe(2);
    expect(element.querySelector('[data-saved-tag="spark"]')).not.toBeNull();
    network.saved.and.returnValue(of({ page: { posts: [post(0)], page: 1, more: false }, tags: ['kafka', 'spark'] }));
    (element.querySelector('[data-saved-more]') as HTMLButtonElement).click();
    expect(network.saved).toHaveBeenCalledWith(null, 1);
    expect(component.posts.map(item => item.id)).toEqual([2, 1, 0]);
    network.saved.and.returnValue(of({ page: { posts: [post(2, ['kafka'])], page: 0, more: false }, tags: ['kafka', 'spark'] }));
    component.filter('kafka');
    expect(network.saved).toHaveBeenCalledWith('kafka', 0);
    expect(component.posts.map(item => item.id)).toEqual([2]);
    component.changed({ ...post(2), bookmarked: true });
    expect(component.posts.length).toBe(1);
    component.changed({ ...post(2), bookmarked: false });
    fixture.detectChanges();
    expect(element.querySelector('[data-saved-empty]')).not.toBeNull();
    network.saved.and.returnValue(throwError(() => new Error('down')));
    component.filter(null);
    expect(notifications.error).toHaveBeenCalledWith('network.savedPosts.loadError');
  });
});
