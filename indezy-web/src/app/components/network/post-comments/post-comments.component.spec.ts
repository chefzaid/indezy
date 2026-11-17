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
import { ConfidentialityGuardService } from '../confidentiality/confidentiality-guard.service';
import { PostCommentsComponent } from './post-comments.component';
import { ReportDialogComponent } from '../report-dialog/report-dialog.component';

describe('PostCommentsComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let guard: jasmine.SpyObj<ConfidentialityGuardService>;
  const member = (handle: string): MemberCard => ({ handle, firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: [],
    city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false });
  const comment = (id: number, changes: Partial<NetworkComment> = {}): NetworkComment => ({ id, author: member('leo_m'), text: `Comment ${id}`,
    createdAt: '2026-11-14T10:00:00', own: false, hidden: false, pinned: false, likes: 0, liked: false, replies: [], ...changes });
  const post: NetworkPost = { id: 9, author: member('camille'), text: 'Post', tags: [], createdAt: '2026-11-14T09:00:00', own: true,
    likes: 0, liked: false, comments: 3, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, kind: 'POST' as const, details: null, answerCommentId: null, poll: null, taken: null, reactions: [], myReaction: null, preview: null };
  const read = [comment(1, { pinned: true, replies: [comment(3, { own: true })] }), comment(2, { hidden: true })];

  function create(postShown = post): ComponentFixture<PostCommentsComponent> {
    network = jasmine.createSpyObj('NetworkService', ['comments', 'comment', 'deleteComment', 'hideComment', 'pinComment', 'likeComment', 'answer']);
    network.comments.and.returnValue(of(read));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    guard = jasmine.createSpyObj('ConfidentialityGuardService', ['review']);
    guard.review.and.callFake((text: string) => of({ action: 'publish' as const, text }));
    TestBed.configureTestingModule({
      imports: [PostCommentsComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }, { provide: ConfidentialityGuardService, useValue: guard }]
    });
    const fixture = TestBed.createComponent(PostCommentsComponent);
    fixture.componentRef.setInput('post', postShown);
    fixture.detectChanges();
    return fixture;
  }

  it('likes a comment, or no longer, in place, and says when it cannot', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    component.load();
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    const target = component.comments[0];
    network.likeComment.and.returnValue(of({ ...target, likes: 3, liked: true }));
    (element.querySelector(`[data-comment="${target.id}"] [data-comment-like]`) as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(network.likeComment).toHaveBeenCalledWith(target.id, true);
    const button = element.querySelector(`[data-comment="${target.id}"] [data-comment-like]`)!;
    expect(button.getAttribute('aria-pressed')).toBe('true');
    expect(button.textContent).toContain('3');
    expect(component.liking).toBeNull();
    network.likeComment.and.returnValue(throwError(() => new Error('down')));
    component.toggleLike(target);
    expect(network.likeComment).toHaveBeenCalledWith(target.id, false);
    expect(notifications.error).toHaveBeenCalledWith('network.post.likeError');
    expect(target.liked).toBeTrue();
  });

  it('reports another member\'s comment to the moderators', () => {
    const fixture = create();
    fixture.componentInstance.load();
    const open = spyOn(ReportDialogComponent, 'open').and.returnValue(of(true));
    fixture.componentInstance.report(comment(2));
    expect(open).toHaveBeenCalledWith(jasmine.anything(), { target: 'comments', id: 2 });
  });

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
    guard.review.and.returnValue(of({ action: 'edit' as const, text: 'Avec un contact' }));
    component.draft.setValue('Avec Hélène Durand');
    component.send();
    expect(component.draft.value).toBe('Avec un contact');
    expect(component.isSending).toBeFalse();
    guard.review.and.returnValue(of(null));
    component.send();
    expect(component.isSending).toBeFalse();
    guard.review.and.callFake((text: string) => of({ action: 'publish' as const, text }));
    network.comments.calls.reset();
    network.comment.and.returnValue(of({ held: true }));
    component.draft.setValue('Achetez');
    component.send();
    expect(notifications.success).toHaveBeenCalledWith('network.feed.held');
    expect(network.comments).not.toHaveBeenCalled();
    expect(component.draft.value).toBe('');
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

  it('lets a question\'s author mark a reply as its answer, or no longer', () => {
    const question = { ...post, kind: 'QUESTION' as const, answerCommentId: null, poll: null, taken: null, reactions: [], myReaction: null, preview: null };
    const fixture = create(question);
    const component = fixture.componentInstance;
    component.load();
    fixture.detectChanges();
    network.answer.and.returnValue(of(undefined));
    component.markAnswer(comment(1));
    expect(network.answer).toHaveBeenCalledWith(9, 1);
    expect(component.post.answerCommentId).toBe(1);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-comment="1"] [data-comment-answer]')).not.toBeNull();
    component.markAnswer(comment(1));
    expect(network.answer).toHaveBeenCalledWith(9, null);
    expect(component.post.answerCommentId).toBeNull();
    network.answer.and.returnValue(throwError(() => new Error('down')));
    component.markAnswer(comment(2));
    expect(notifications.error).toHaveBeenCalledWith('network.post.commentError');
  });
});
