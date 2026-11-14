import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { NetworkFeedComponent } from './network-feed.component';
import en from '../../../../assets/i18n/en.json';

describe('NetworkFeedComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  const post = (id: number, own = false): NetworkPost => ({ id, text: `Post ${id}`, tags: [], createdAt: '2026-11-14T10:00:00', own,
    author: { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: [], city: null, remote: false,
      availability: null, availabilityDate: null, languages: [], verified: false } });

  function create(feed = of({ posts: [post(2), post(1, true)], page: 0, more: true })): ComponentFixture<NetworkFeedComponent> {
    network = jasmine.createSpyObj('NetworkService', ['feed', 'post', 'deletePost']);
    network.feed.and.returnValue(feed);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    TestBed.configureTestingModule({
      imports: [NetworkFeedComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }]
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
    expect(network.post).toHaveBeenCalledWith('Bonjour #Kafka');
    expect(component.posts[0].id).toBe(9);
    expect(component.draft.value).toBe('');
    network.post.and.returnValue(throwError(() => new Error('down')));
    component.draft.setValue('Again');
    component.publish();
    expect(notifications.error).toHaveBeenCalledWith('network.feed.postError');

    confirm.confirm.and.returnValue(of(false));
    component.remove(post(1, true));
    expect(network.deletePost).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    network.deletePost.and.returnValue(of(undefined));
    component.remove(post(1, true));
    expect(component.posts.some(item => item.id === 1)).toBeFalse();
    network.deletePost.and.returnValue(throwError(() => new Error('down')));
    component.remove(post(2));
    expect(notifications.error).toHaveBeenCalledTimes(2);
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
});
