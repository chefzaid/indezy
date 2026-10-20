import { Directive, ElementRef, EventEmitter, Output, inject } from '@angular/core';

export type OfferKeyAction = 'save' | 'hide' | 'add' | 'details' | 'note' | 'select';

/** Keys of the offer list; they act only while the focus is in the list, so typing elsewhere is never caught. */
const ACTIONS: Record<string, OfferKeyAction> = { s: 'save', h: 'hide', a: 'add', d: 'details', n: 'note', x: 'select' };
const EDITABLE = 'input, textarea, select, [contenteditable]:not([contenteditable="false"]), [role="textbox"], [role="combobox"]';

/**
 * Keyboard use of the offer feed: `j`/`k` (or the arrow keys) move between offers, `o` opens one on its site, and `s`,
 * `h`, `a`, `d`, `n` and `x` save, hide, add to the pipeline, show the details of, write a note on and select the focused
 * offer. Offers are the list's `[data-offer-item]` elements, one tab stop for the list (roving tabindex).
 */
@Directive({
  selector: '[appOfferListKeys]',
  host: { '(keydown)': 'onKeydown($event)', '(focusin)': 'onFocusIn($event)' }
})
export class OfferListKeysDirective {
  /** What to do with the offer at `index` of the list. */
  @Output() readonly offerAction = new EventEmitter<{ action: OfferKeyAction; index: number }>();

  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  onKeydown(event: KeyboardEvent): void {
    if (event.ctrlKey || event.metaKey || event.altKey || event.shiftKey || event.repeat || event.isComposing || event.defaultPrevented) {
      return;
    }
    const target = event.target instanceof Element ? event.target : null;
    const items = this.items();
    const index = items.findIndex(item => item === target?.closest('[data-offer-item]'));
    if (index < 0 || target?.closest(EDITABLE)) {
      return;
    }
    const key = event.key.length === 1 ? event.key.toLowerCase() : event.key;
    if (key === 'j' || key === 'ArrowDown') {
      this.focus(index + 1);
    } else if (key === 'k' || key === 'ArrowUp') {
      this.focus(index - 1);
    } else if (key === 'o') {
      items[index].querySelector<HTMLAnchorElement>('[data-open-offer]')?.click();
    } else if (ACTIONS[key]) {
      this.offerAction.emit({ action: ACTIONS[key], index });
      // An offer that leaves the list (hidden) hands the focus to the one taking its place.
      setTimeout(() => {
        if (!this.host.nativeElement.contains(document.activeElement)) {
          this.focus(index);
        }
      });
    } else {
      return;
    }
    event.preventDefault();
  }

  /** The focused offer becomes the list's tab stop. */
  onFocusIn(event: FocusEvent): void {
    const item = event.target instanceof Element ? event.target.closest('[data-offer-item]') : null;
    if (item) {
      this.items().forEach(other => other.setAttribute('tabindex', other === item ? '0' : '-1'));
    }
  }

  private focus(index: number): void {
    const items = this.items();
    if (items.length) {
      items[Math.max(0, Math.min(index, items.length - 1))].focus();
    }
  }

  private items(): HTMLElement[] {
    return Array.from(this.host.nativeElement.querySelectorAll<HTMLElement>('[data-offer-item]'));
  }
}
