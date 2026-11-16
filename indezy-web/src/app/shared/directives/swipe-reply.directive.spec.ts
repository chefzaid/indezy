import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SWIPE_REPLY_AT, SwipeReplyDirective } from './swipe-reply.directive';

@Component({
  imports: [SwipeReplyDirective],
  template: '<div appSwipeReply (appSwipeReply)="replies = replies + 1" data-swiped>Bonjour</div>'
})
class HostComponent {
  replies = 0;
}

describe('SwipeReplyDirective', () => {
  let fixture: ComponentFixture<HostComponent>;
  let element: HTMLElement;

  const pointer = (type: string, x: number, y = 0, pointerType = 'touch'): void => {
    element.dispatchEvent(new PointerEvent(type, { clientX: x, clientY: y, pointerType, isPrimary: true }));
    fixture.detectChanges();
  };

  beforeEach(() => {
    fixture = TestBed.configureTestingModule({ imports: [HostComponent] }).createComponent(HostComponent);
    fixture.detectChanges();
    element = fixture.nativeElement.querySelector('[data-swiped]');
  });

  it('follows a finger to the right, up to 80 pixels, and replies once let go past the threshold', () => {
    expect(element.style.touchAction).toBe('pan-y');
    pointer('pointerdown', 10);
    pointer('pointermove', 40);
    expect(element.style.transform).toBe('translateX(30px)');
    expect(element.style.transition).toBe('none');
    pointer('pointermove', 200);
    expect(element.style.transform).toBe('translateX(80px)');
    pointer('pointerup', 200);
    expect(fixture.componentInstance.replies).toBe(1);
    expect(element.style.transform).toBe('');
    expect(element.style.transition).toBe('');
  });

  it('springs back without replying when let go too soon, moved up or down, cancelled, or swiped with a mouse', () => {
    pointer('pointerdown', 10);
    pointer('pointermove', 10 + SWIPE_REPLY_AT - 1);
    pointer('pointerup', 10 + SWIPE_REPLY_AT - 1);
    pointer('pointerdown', 10);
    pointer('pointermove', 20, 60);
    pointer('pointermove', 200, 60);
    expect(element.style.transform).toBe('');
    pointer('pointerup', 200);
    pointer('pointerdown', 10);
    pointer('pointermove', 100);
    pointer('pointercancel', 100);
    pointer('pointerup', 100);
    pointer('pointerdown', 10, 0, 'mouse');
    pointer('pointermove', 200, 0, 'mouse');
    pointer('pointerup', 200, 0, 'mouse');
    pointer('pointermove', 200);
    expect(fixture.componentInstance.replies).toBe(0);
  });
});
