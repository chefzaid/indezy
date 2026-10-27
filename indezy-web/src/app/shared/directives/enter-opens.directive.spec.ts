import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { EnterOpensDirective } from './enter-opens.directive';

@Component({
  imports: [EnterOpensDirective],
  template: `<div appEnterOpens tabindex="0" (click)="opened = opened + 1"><button type="button">Menu</button></div>`
})
class HostComponent {
  opened = 0;
}

describe('EnterOpensDirective', () => {
  it('opens the focused card on Enter, but not from a control inside it', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const card = (fixture.nativeElement as HTMLElement).querySelector('div')!;
    const enter = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true, bubbles: true });

    card.dispatchEvent(enter);
    expect(fixture.componentInstance.opened).toBe(1);
    expect(enter.defaultPrevented).toBeTrue();

    card.querySelector('button')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    expect(fixture.componentInstance.opened).toBe(1);
  });
});
