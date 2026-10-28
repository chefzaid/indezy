import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { PageHeaderComponent } from './page-header.component';

@Component({
  imports: [PageHeaderComponent],
  template: `
    <app-page-header icon="work" title="Opportunities" [subtitle]="subtitle">
      <p pageHeaderBelow class="page-subtitle" data-below>12 open</p>
      <button type="button">Add</button>
    </app-page-header>
  `
})
class HostComponent {
  subtitle: string | null = 'Every mission you follow';
}

describe('PageHeaderComponent', () => {
  function render(subtitle: string | null = 'Every mission you follow'): HTMLElement {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.subtitle = subtitle;
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the icon and title as the level 1 heading, the subtitle, what goes below and the actions', () => {
    const page = render();

    const title = page.querySelector('h1.page-title')!;
    expect(title.textContent).toContain('Opportunities');
    expect(title.querySelector('mat-icon')?.getAttribute('aria-hidden')).toBe('true');
    expect(page.querySelector('.page-heading > .page-subtitle')?.textContent).toBe('Every mission you follow');
    expect(page.querySelector('.page-heading [data-below]')?.textContent).toBe('12 open');
    expect(page.querySelector('.page-actions button')?.textContent).toBe('Add');
  });

  it('leaves the subtitle out when there is none', () => {
    const page = render(null);

    expect(page.querySelectorAll('.page-subtitle').length).toBe(1);
  });
});
