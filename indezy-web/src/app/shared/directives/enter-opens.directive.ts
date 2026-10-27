import { Directive, ElementRef, inject } from '@angular/core';

/**
 * Opens a card or row that `routerLink` makes focusable on Enter as a click would: the router link itself only answers
 * clicks on elements other than links and buttons. Enter on a control inside it is left to that control.
 */
@Directive({
  selector: '[appEnterOpens]',
  host: { '(keydown.enter)': 'open($event)' }
})
export class EnterOpensDirective {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  open(event: Event): void {
    if (event.target !== this.host.nativeElement) {
      return;
    }
    event.preventDefault();
    this.host.nativeElement.click();
  }
}
