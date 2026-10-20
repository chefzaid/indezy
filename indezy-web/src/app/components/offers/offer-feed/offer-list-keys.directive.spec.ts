import { Component } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { OfferKeyAction, OfferListKeysDirective } from './offer-list-keys.directive';

@Component({
  imports: [OfferListKeysDirective],
  template: `
    <ol appOfferListKeys (offerAction)="actions.push($event)">
      @for (id of ids; track id; let first = $first) {
        <li data-offer-item [attr.tabindex]="first ? 0 : -1" [attr.data-id]="id">
          <a href="#" data-open-offer (click)="opened.push(id); $event.preventDefault()">Offer {{ id }}</a>
          <button type="button">Save</button>
          <input aria-label="note">
        </li>
      }
    </ol>`
})
class HostComponent {
  ids = [1, 2, 3];
  actions: { action: OfferKeyAction; index: number }[] = [];
  opened: number[] = [];
}

describe('OfferListKeysDirective', () => {
  let fixture: ComponentFixture<HostComponent>;

  function item(index: number): HTMLElement {
    return fixture.nativeElement.querySelectorAll('[data-offer-item]')[index] as HTMLElement;
  }

  function press(target: HTMLElement, key: string, init: KeyboardEventInit = {}): KeyboardEvent {
    const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...init });
    target.dispatchEvent(event);
    return event;
  }

  beforeEach(() => {
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    document.body.appendChild(fixture.nativeElement);
  });

  afterEach(() => fixture.nativeElement.remove());

  it('moves between offers with j, k and the arrow keys and keeps one tab stop', () => {
    item(0).focus();
    expect(press(item(0), 'j').defaultPrevented).toBeTrue();
    expect(document.activeElement).toBe(item(1));
    expect(item(1).getAttribute('tabindex')).toBe('0');
    expect(item(0).getAttribute('tabindex')).toBe('-1');
    press(item(1), 'ArrowDown');
    press(item(2), 'ArrowDown');
    expect(document.activeElement).toBe(item(2));
    press(item(2), 'k');
    press(item(1), 'ArrowUp');
    press(item(0), 'ArrowUp');
    expect(document.activeElement).toBe(item(0));
  });

  it('opens, saves, hides, adds, folds and notes the focused offer, even from one of its buttons', () => {
    press(item(1), 'o');
    press(item(1).querySelector('button') as HTMLElement, 's');
    press(item(2), 'A');
    press(item(0), 'd');
    press(item(2), 'n');
    press(item(1), 'x');

    expect(fixture.componentInstance.opened).toEqual([2]);
    expect(fixture.componentInstance.actions).toEqual([{ action: 'save', index: 1 }, { action: 'add', index: 2 }, { action: 'details', index: 0 },
      { action: 'note', index: 2 }, { action: 'select', index: 1 }]);
  });

  it('leaves typing, modified keys and other keys alone', () => {
    const typed = press(item(0).querySelector('input') as HTMLElement, 's');
    const modified = press(item(0), 's', { ctrlKey: true });
    const shifted = press(item(0), 'S', { shiftKey: true });
    const other = press(item(0), 'z');
    const outside = new KeyboardEvent('keydown', { key: 's', bubbles: true, cancelable: true });
    fixture.nativeElement.querySelector('ol').dispatchEvent(outside);

    expect([typed, modified, shifted, other, outside].some(event => event.defaultPrevented)).toBeFalse();
    expect(fixture.componentInstance.actions).toEqual([]);
  });

  it('hands the focus to the offer taking the place of a hidden one', fakeAsync(() => {
    item(1).focus();
    press(item(1), 'h');
    fixture.componentInstance.ids = [1, 3];
    fixture.detectChanges();
    tick();

    expect(fixture.componentInstance.actions).toEqual([{ action: 'hide', index: 1 }]);
    expect(document.activeElement?.getAttribute('data-id')).toBe('3');
  }));
});
