import { Directive, ElementRef, EventEmitter, OnDestroy, OnInit, Output, inject } from '@angular/core';

/**
 * Tells when its element comes near the viewport (`(appInView)`), to load the next page before the reader reaches the
 * end of a list. Without IntersectionObserver nothing is told, and the list's **More** button still works.
 */
@Directive({ selector: '[appInView]' })
export class InViewDirective implements OnInit, OnDestroy {
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private observer?: IntersectionObserver;

  @Output() readonly appInView = new EventEmitter<void>();

  ngOnInit(): void {
    if (typeof IntersectionObserver === 'undefined') {
      return;
    }
    this.observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) {
        this.appInView.emit();
      }
    }, { rootMargin: '0px 0px 400px 0px' });
    this.observer.observe(this.element.nativeElement);
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }
}
