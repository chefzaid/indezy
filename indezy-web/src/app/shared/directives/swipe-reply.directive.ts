import { Directive, EventEmitter, Output } from '@angular/core';

/** How far the finger drags the element before letting go replies, and the farthest it follows, in pixels. */
export const SWIPE_REPLY_AT = 56;
const SWIPE_REPLY_MAX = 80;

/**
 * Swiping an element to the right on a touch screen (`(appSwipeReply)`), as messaging apps reply to a message: the
 * element follows the finger up to 80 pixels and springs back; let go past 56 and it tells. A vertical move scrolls as
 * usual, and a mouse never swipes: the reply button stays for it and the keyboard.
 */
@Directive({
  selector: '[appSwipeReply]',
  host: {
    '[style.touch-action]': '"pan-y"',
    '[style.transform]': 'offset ? "translateX(" + offset + "px)" : null',
    '[style.transition]': 'start ? "none" : null',
    '(pointerdown)': 'down($event)',
    '(pointermove)': 'move($event)',
    '(pointerup)': 'up()',
    '(pointercancel)': 'reset()'
  }
})
export class SwipeReplyDirective {
  @Output() readonly appSwipeReply = new EventEmitter<void>();

  /** Where the finger touched; none while no swipe goes on. */
  start: { x: number; y: number } | null = null;
  /** How far the element follows the finger. */
  offset = 0;

  down(event: PointerEvent): void {
    if (event.pointerType !== 'mouse' && event.isPrimary) {
      this.start = { x: event.clientX, y: event.clientY };
      this.offset = 0;
    }
  }

  move(event: PointerEvent): void {
    if (!this.start) {
      return;
    }
    const dx = event.clientX - this.start.x;
    const dy = event.clientY - this.start.y;
    if (this.offset === 0 && Math.abs(dy) > Math.abs(dx)) {
      this.reset();
      return;
    }
    this.offset = Math.round(Math.min(SWIPE_REPLY_MAX, Math.max(0, dx)));
  }

  up(): void {
    const replies = this.start !== null && this.offset >= SWIPE_REPLY_AT;
    this.reset();
    if (replies) {
      this.appSwipeReply.emit();
    }
  }

  reset(): void {
    this.start = null;
    this.offset = 0;
  }
}
