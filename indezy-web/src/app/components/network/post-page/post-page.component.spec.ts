import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { PostPageComponent } from './post-page.component';

describe('PostPageComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  const post = { id: 7, text: 'Bonjour', tags: [], createdAt: '2026-11-15T09:00:00', own: true, likes: 0, liked: false, comments: 0,
    commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, kind: 'POST', details: null,
    answerCommentId: null, poll: null, taken: null, useful: null, reactions: [], myReaction: null, preview: null, author: { handle: 'camille', firstName: 'Camille', lastName: 'Durand',
      avatar: null, headline: null, stack: [], city: null, remote: false, availability: null, availabilityDate: null, languages: [],
      verified: false } } as NetworkPost;

  function create(answer = of(post)) {
    network = jasmine.createSpyObj('NetworkService', ['onePost', 'deletePost', 'comments']);
    network.onePost.and.returnValue(answer);
    network.comments.and.returnValue(of([]));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    TestBed.configureTestingModule({
      imports: [PostPageComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: '7' })) } },
        { provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }]
    });
    const fixture = TestBed.createComponent(PostPageComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the post with its comments open, and deletes it once confirmed', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    expect(network.onePost).toHaveBeenCalledWith(7);
    expect(element.querySelector('[data-post="7"]')).not.toBeNull();
    expect(element.querySelector('app-post-comments')).not.toBeNull();
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    confirm.confirm.and.returnValue(of(false));
    fixture.componentInstance.remove(post);
    expect(network.deletePost).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    network.deletePost.and.returnValue(of(undefined));
    fixture.componentInstance.remove(post);
    expect(navigate).toHaveBeenCalledWith(['/network']);
    network.deletePost.and.returnValue(throwError(() => new Error('down')));
    fixture.componentInstance.remove(post);
    expect(notifications.error).toHaveBeenCalledWith('network.feed.postError');
  });

  it('says when the post is gone, or cannot be loaded', () => {
    let fixture = create(throwError(() => new HttpErrorResponse({ status: 404 })));
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-post-missing]')).not.toBeNull();
    TestBed.resetTestingModule();
    fixture = create(throwError(() => new HttpErrorResponse({ status: 500 })));
    expect(notifications.error).toHaveBeenCalledWith('network.feed.loadError');
    expect(fixture.componentInstance.missing).toBeFalse();
  });
});
