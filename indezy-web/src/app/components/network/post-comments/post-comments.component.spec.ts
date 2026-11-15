import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { MemberCard, NetworkComment, NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { PostCommentsComponent } from './post-comments.component';

describe('PostCommentsComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  const member = (handle: string): MemberCard => ({ handle, firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: [],
    city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false });
  const comment = (id: number, changes: Partial<NetworkComment> = {}): NetworkComment => ({ id, author: member('leo_m'), text: `Comment ${id}`,
    createdAt: '2026-11-14T10:00:00', own: false, hidden: false, pinned: false, replies: [], ...changes });
  const post: NetworkPost = { id: 9, author: member('camille'), text: 'Post', tags: [], createdAt: '2026-11-14T09:00:00', own: true,
    likes: 0, liked: false, comments: 3, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false };
  const read = [comment(1, { pinned: true, replies: [comment(3, { own: true })] }), comment(2, { hidden: true })];

  function create(postShown = post): ComponentFixture<PostCommentsComponent> {
    network = jasmine.createSpyObj('NetworkService', ['comments', 'comment', 'deleteComment', 'hideComment', 'pinComment']);
    network.comments.and.returnValue(of(read));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    TestBed.configureTestingModule({
      imports: [PostCommentsComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }]
    });
    const fixture = TestBed.createComponent(PostCommentsComponent);
    fixture.componentRef.setInput('post', postShown);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the comments with their replies and counts them, then comments and replies', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    const counted = jasmine.createSpy('counted');
    component.counted.subscribe(counted);
    component.load();
    expect(counted).toHaveBeenCalledWith(3);
    expect(element.querySelector('[data-comment="1"] [data-comment-pinned]')).not.toBeNull();
    expect(element.querySelector('[data-comment="2"] [data-comment-hidden]')).not.toBeNull();
    expect(element.querySelector('.replies [data-comment="3"]')).not.toBeNull();
    expect(element.querySelector('.replies [data-comment="3"] [data-comment-reply]')).toBeNull();

    network.comment.and.returnValue(of(comment(4)));
    component.send();
    expect(network.comment).not.toHaveBeenCalled();
    (element.querySelector('[data-comment="1"] [data-comment-reply]') as HTMLButtonElement).click();
    component.draft.setValue(' Merci ');
    component.send();
    expect(network.comment).toHaveBeenCalledWith(9, 'Merci', 1);
    expect(component.replyTo).toBeNull();
    component.draft.setValue('Sous le post');
    component.send();
    expect(network.comment).toHaveBeenCalledWith(9, 'Sous le post', null);
    network.comment.and.returnValue(throwError(() => new Error('down')));
    component.draft.setValue('Encore');
    component.send();
    expect(notifications.error).toHaveBeenCalledWith('network.post.commentError');
    network.comment.and.returnValue(throwError(() => new HttpErrorResponse({ status: 429 })));
    component.send();
    expect(notifications.error).toHaveBeenCalledWith('network.post.tooManyComments');
  });

  it('pins, hides and deletes as the post\'s author and the comment\'s writer', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    network.pinComment.and.returnValue(of(undefined));
    component.pin(read[0]);
    expect(network.pinComment).toHaveBeenCalledWith(9, null);
    component.pin(read[1]);
    expect(network.pinComment).toHaveBeenCalledWith(9, 2);
    network.hideComment.and.returnValue(of(undefined));
    component.hide(read[1]);
    expect(network.hideComment).toHaveBeenCalledWith(2, false);
    confirm.confirm.and.returnValue(of(false));
    component.remove(comment(3));
    expect(network.deleteComment).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    network.deleteComment.and.returnValue(of(undefined));
    component.remove(comment(3));
    expect(network.deleteComment).toHaveBeenCalledWith(3);
    expect(network.comments).toHaveBeenCalledTimes(5);
    for (const failing of [network.pinComment, network.hideComment, network.deleteComment]) {
      failing.and.returnValue(throwError(() => new Error('down')));
    }
    component.pin(read[0]);
    component.hide(read[0]);
    component.remove(read[0]);
    expect(notifications.error).toHaveBeenCalledTimes(3);
    network.comments.and.returnValue(throwError(() => new Error('down')));
    component.load();
    expect(notifications.error).toHaveBeenCalledTimes(4);
  });

  it('says the comments are closed instead of offering to write one', () => {
    const fixture = create({ ...post, commentsClosed: true });
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-comments-closed]')).not.toBeNull();
    expect(element.querySelector('[data-comment-form]')).toBeNull();
    expect(element.querySelector('[data-comment-reply]')).toBeNull();
  });
});
