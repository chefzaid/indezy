import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { InViewDirective } from './in-view.directive';

@Component({
  imports: [InViewDirective],
  template: '<div (appInView)="seen = seen + 1">end</div>'
})
class HostComponent {
  seen = 0;
}

describe('InViewDirective', () => {
  let callback: IntersectionObserverCallback;
  let disconnect: jasmine.Spy;
  const original = window.IntersectionObserver;

  afterEach(() => (window as { IntersectionObserver?: typeof IntersectionObserver }).IntersectionObserver = original);

  function observe(): void {
    disconnect = jasmine.createSpy('disconnect');
    (window as { IntersectionObserver?: unknown }).IntersectionObserver = class {
      constructor(given: IntersectionObserverCallback) {
        callback = given;
      }

      observe(): void {
        // Observed: the test calls back itself.
      }

      disconnect(): void {
        disconnect();
      }
    };
  }

  it('tells when the element comes into view, and stops watching once destroyed', () => {
    observe();
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    callback([{ isIntersecting: false } as IntersectionObserverEntry], {} as IntersectionObserver);
    expect(fixture.componentInstance.seen).toBe(0);
    callback([{ isIntersecting: true } as IntersectionObserverEntry], {} as IntersectionObserver);
    expect(fixture.componentInstance.seen).toBe(1);
    fixture.destroy();
    expect(disconnect).toHaveBeenCalled();
  });

  it('tells nothing without IntersectionObserver', () => {
    (window as { IntersectionObserver?: unknown }).IntersectionObserver = undefined;
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    fixture.destroy();
    expect(fixture.componentInstance.seen).toBe(0);
  });
});
