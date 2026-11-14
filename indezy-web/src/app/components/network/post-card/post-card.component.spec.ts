import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { PostCardComponent, segments } from './post-card.component';

describe('PostCardComponent', () => {
  const post: NetworkPost = { id: 4, text: 'Merci @leo_m pour #Kafka !\n<b>pas du HTML</b> mail@example.com', tags: ['kafka'],
    createdAt: '2026-11-14T10:00:00', own: true, likes: 2, liked: false, comments: 3, commentsClosed: false, edited: false, bookmarked: false, author: { handle: 'camille', firstName: 'Camille', lastName: 'Durand', avatar: null,
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
    network = jasmine.createSpyObj('NetworkService', ['like', 'likers', 'closeComments', 'comments', 'editPost', 'postVersions', 'bookmark']);
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

  it('lets its author edit it for 15 minutes, then shows its earlier texts', () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', { ...post, createdAt: new Date(Date.now() - 60_000).toISOString() });
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(component.editable).toBeTrue();
    component.startEditing();
    fixture.detectChanges();
    expect((element.querySelector('[data-post-edit-text]') as HTMLTextAreaElement).value).toContain('Merci');
    component.editing!.setValue('  ');
    component.saveEdit();
    expect(network.editPost).not.toHaveBeenCalled();
    network.editPost.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.editing!.setValue('Revu');
    component.saveEdit();
    expect(notifications.error).toHaveBeenCalledWith('network.post.editTooLate');
    network.editPost.and.returnValue(of({ ...post, text: 'Revu', edited: true }));
    component.saveEdit();
    fixture.detectChanges();
    expect(component.editing).toBeNull();
    expect(element.querySelector('.text')?.textContent).toContain('Revu');
    network.postVersions.and.returnValue(of([{ text: 'Merci', replacedAt: '2026-11-14T10:05:00' }]));
    (element.querySelector('[data-post-edited]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelector('[data-post-versions]')?.textContent).toContain('Merci');
    component.toggleVersions();
    expect(component.versions).toBeNull();
    network.postVersions.and.returnValue(throwError(() => new Error('down')));
    component.toggleVersions();
    network.editPost.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.startEditing();
    component.saveEdit();
    expect(notifications.error).toHaveBeenCalledWith('network.post.editError');
    fixture.componentRef.setInput('post', { ...post, createdAt: '2020-01-01T00:00:00' });
    expect(component.editable).toBeFalse();
  });

  it('saves the post to the reader\'s list, or takes it off, telling who shows it', () => {
    configure();
    const fixture = TestBed.createComponent(PostCardComponent);
    fixture.componentRef.setInput('post', post);
    fixture.detectChanges();
    const unsaved = jasmine.createSpy('unsaved');
    fixture.componentInstance.unsaved.subscribe(unsaved);
    network.bookmark.and.returnValue(of({ ...post, bookmarked: true }));
    (fixture.nativeElement.querySelector('[data-bookmark]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(network.bookmark).toHaveBeenCalledWith(4, true);
    expect(unsaved).toHaveBeenCalledWith(jasmine.objectContaining({ bookmarked: true }));
    expect(fixture.nativeElement.querySelector('[data-bookmark]').getAttribute('aria-pressed')).toBe('true');
    network.bookmark.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.toggleBookmark();
    expect(notifications.error).toHaveBeenCalledWith('network.post.likeError');
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
    expect(element.querySelector('.tag')?.getAttribute('href')).toBe('/network/tags/kafka');
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
