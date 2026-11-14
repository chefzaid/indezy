import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { PostCardComponent, segments } from './post-card.component';

describe('PostCardComponent', () => {
  const post: NetworkPost = { id: 4, text: 'Merci @leo_m pour #Kafka !\n<b>pas du HTML</b> mail@example.com', tags: ['kafka'],
    createdAt: '2026-11-14T10:00:00', own: true, likes: 2, liked: false, comments: 3, commentsClosed: false, author: { handle: 'camille', firstName: 'Camille', lastName: 'Durand', avatar: null,
      headline: null, stack: [], city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false } };

  it('splits the text into plain text, mentions and tags, leaving an address or a word alone', () => {
    expect(segments('Merci @leo_m pour #Kafka-2 !')).toEqual([{ text: 'Merci ' }, { handle: 'leo_m' }, { text: ' pour ' }, { tag: 'Kafka-2' },
      { text: ' !' }]);
    expect(segments('mail@example.com et C#sharp')).toEqual([{ text: 'mail@example.com et C#sharp' }]);
    expect(segments('#lyon')).toEqual([{ tag: 'lyon' }]);
  });

  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  function configure(): void {
    network = jasmine.createSpyObj('NetworkService', ['like', 'likers', 'closeComments', 'comments']);
    network.comments.and.returnValue(of([]));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({ imports: [PostCardComponent, TranslateModule.forRoot()], providers: [provideRouter([]),
      { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications }] });
  }

  it('likes the post, or no longer, and lists who likes it', () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', post);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    network.likers.and.returnValue(of([post.author]));
    (element.querySelector('[data-likes]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('[data-likers] a')?.getAttribute('href')).toBe('/m/camille');
    network.like.and.returnValue(of({ ...post, likes: 3, liked: true }));
    (element.querySelector('[data-like]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(network.like).toHaveBeenCalledWith(4, true);
    expect(network.likers).toHaveBeenCalledTimes(2);
    expect(element.querySelector('[data-like]')?.getAttribute('aria-pressed')).toBe('true');
    (element.querySelector('[data-likes]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('[data-likers]')).toBeNull();
    network.like.and.returnValue(throwError(() => new Error('down')));
    component.toggleLike();
    expect(component.isLiking).toBeFalse();
    network.likers.and.returnValue(throwError(() => new Error('down')));
    component.showLikers();
    expect(notifications.error).toHaveBeenCalledWith('network.post.likeError');
    expect(notifications.error).toHaveBeenCalledTimes(2);
  });

  it('opens the comments under the post, and lets its author close them', () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', post);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-show-comments]')?.textContent).toContain('network.post.comments');
    (element.querySelector('[data-show-comments]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('app-post-comments')).not.toBeNull();
    expect(component.post.comments).toBe(3);
    network.closeComments.and.returnValue(of(undefined));
    component.toggleClosed();
    expect(network.closeComments).toHaveBeenCalledWith(4, true);
    expect(component.post.commentsClosed).toBeTrue();
    network.closeComments.and.returnValue(throwError(() => new Error('down')));
    component.toggleClosed();
    expect(notifications.error).toHaveBeenCalledWith('network.post.commentError');
  });

  it('links the author and the members mentioned, shows the text as text, and lets its author delete it', () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', post);
    const deleted = jasmine.createSpy('deleted');
    fixture.componentInstance.deleted.subscribe(deleted);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('.author')?.getAttribute('href')).toBe('/m/camille');
    expect(element.querySelector('.mention')?.getAttribute('href')).toBe('/m/leo_m');
    expect(element.querySelector('.tag')?.textContent).toBe('#Kafka');
    expect(element.querySelector('.text b')).toBeNull();
    expect(element.querySelector('.text')?.textContent).toContain('<b>pas du HTML</b>');
    expect(element.querySelector('[data-post-actions]')).not.toBeNull();
    fixture.componentInstance.deleted.emit(post);
    expect(deleted).toHaveBeenCalledWith(post);
    fixture.componentRef.setInput('post', { ...post, own: false });
    fixture.detectChanges();
    expect(element.querySelector('[data-post-actions]')).toBeNull();
  });
});
