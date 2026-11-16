import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, of } from 'rxjs';

import { NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { HOLD_MS, ReactionButtonComponent } from './reaction-button.component';

describe('ReactionButtonComponent', () => {
  let network: jasmine.SpyObj<NetworkService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let fixture: ComponentFixture<ReactionButtonComponent>;
  let emitted: NetworkPost[];
  const post: NetworkPost = { id: 4, text: 'Mission signée', tags: [], createdAt: '2026-11-14T10:00:00', own: false, likes: 3, liked: true,
    comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, kind: 'POST',
    details: null, answerCommentId: null, poll: null, taken: null, useful: null, myReaction: 'LIKE', preview: null,
    reactions: [{ reaction: 'LIKE', count: 2 }, { reaction: 'BRAVO', count: 1 }], author: { handle: 'leo_m', firstName: 'Léo',
      lastName: 'Martin', avatar: null, headline: null, stack: [], city: null, remote: false, availability: null, availabilityDate: null,
      languages: [], verified: false } };

  function create(given: NetworkPost = post): HTMLButtonElement {
    network = jasmine.createSpyObj('NetworkService', ['react']);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({ imports: [ReactionButtonComponent, TranslateModule.forRoot()],
      providers: [{ provide: NetworkService, useValue: network }, { provide: NotificationService, useValue: notifications },
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }] });
    fixture = TestBed.createComponent(ReactionButtonComponent);
    fixture.componentRef.setInput('post', given);
    emitted = [];
    fixture.componentInstance.changed.subscribe(changed => emitted.push(changed));
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('[data-like]');
  }

  const menuItems = (): HTMLElement[] => Array.from(document.querySelectorAll('[data-react]'));

  it('counts the reactions as the reader reacts, changes or takes theirs back', () => {
    expect(ReactionButtonComponent.applied(post, 'BRAVO')).toEqual(jasmine.objectContaining({ likes: 3, liked: true, myReaction: 'BRAVO',
      reactions: [{ reaction: 'BRAVO', count: 2 }, { reaction: 'LIKE', count: 1 }] }));
    expect(ReactionButtonComponent.applied(post, null)).toEqual(jasmine.objectContaining({ likes: 2, liked: false, myReaction: null, preview: null,
      reactions: [{ reaction: 'LIKE', count: 1 }, { reaction: 'BRAVO', count: 1 }] }));
    const none = { ...post, likes: 0, liked: false, myReaction: null, preview: null, reactions: [] };
    expect(ReactionButtonComponent.applied(none, 'WOW')).toEqual(jasmine.objectContaining({ likes: 1,
      reactions: [{ reaction: 'WOW', count: 1 }] }));
  });

  it('takes the reaction back at once, and puts it back when the server refuses', () => {
    const button = create();
    expect(button.getAttribute('aria-pressed')).toBe('true');
    const answer = new Subject<NetworkPost>();
    network.react.and.returnValue(answer);
    button.click();
    fixture.detectChanges();
    expect(network.react).toHaveBeenCalledWith(4, null);
    expect(button.getAttribute('aria-pressed')).toBe('false');
    expect(emitted[0].likes).toBe(2);
    answer.error(new Error('down'));
    fixture.detectChanges();
    expect(button.getAttribute('aria-pressed')).toBe('true');
    expect(emitted.at(-1)).toBe(post);
    expect(notifications.error).toHaveBeenCalledWith('network.post.likeError');
  });

  it('likes with a press, popping, and shows the reaction the server kept', fakeAsync(() => {
    const button = create({ ...post, likes: 0, liked: false, myReaction: null, preview: null, reactions: [] });
    network.react.and.returnValue(of({ ...post, myReaction: 'LIKE' }));
    button.click();
    fixture.detectChanges();
    expect(network.react).toHaveBeenCalledWith(4, 'LIKE');
    expect(fixture.componentInstance.popping).toBeTrue();
    tick(400);
    expect(fixture.componentInstance.popping).toBeFalse();
    expect(emitted.length).toBe(2);
  }));

  it('offers the other reactions on a hover, a long press or the up arrow, and reacts with the one chosen', fakeAsync(() => {
    const button = create({ ...post, myReaction: 'BRAVO' });
    expect(button.textContent).toContain('👏');
    button.dispatchEvent(new Event('mouseenter'));
    tick(HOLD_MS - 100);
    button.dispatchEvent(new Event('mouseleave'));
    tick(HOLD_MS);
    expect(menuItems().length).toBe(0);
    button.dispatchEvent(new Event('mouseenter'));
    tick(HOLD_MS);
    fixture.detectChanges();
    expect(menuItems().map(item => item.getAttribute('data-react'))).toEqual(['LIKE', 'BRAVO', 'FUNNY', 'SUPPORT', 'WOW']);
    expect(document.querySelector('[data-react="BRAVO"]')?.getAttribute('aria-current')).toBe('true');
    network.react.and.returnValue(of({ ...post, myReaction: 'WOW' }));
    (document.querySelector('[data-react="WOW"]') as HTMLButtonElement).click();
    tick(400);
    fixture.detectChanges();
    expect(network.react).toHaveBeenCalledWith(4, 'WOW');
    expect(button.getAttribute('data-reaction')).toBe('WOW');

    // A long press opens them too, and the click ending it does not take the reaction back.
    button.dispatchEvent(new Event('touchstart'));
    tick(HOLD_MS);
    button.dispatchEvent(new Event('touchend'));
    button.click();
    fixture.detectChanges();
    expect(network.react).toHaveBeenCalledTimes(1);
    expect(menuItems().length).toBe(5);
    fixture.componentInstance.trigger!.closeMenu();
    tick(100);
    expect(document.activeElement).toBe(button);

    button.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp' }));
    fixture.detectChanges();
    tick(100);
    expect(menuItems().length).toBe(5);
    fixture.componentInstance.trigger!.closeMenu();
    tick(100);
    fixture.destroy();
  }));
});
