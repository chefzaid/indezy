import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject, of } from 'rxjs';

import { NetworkService } from '../../../services/network/network.service';
import { NetworkNoticesService } from '../../../services/network/network-notices.service';
import { NetworkNavComponent } from './network-nav.component';

@Component({ template: '' })
class BlankComponent {}

describe('NetworkNavComponent', () => {
  const unread = new BehaviorSubject(3);
  const messages = new BehaviorSubject(120);

  async function create(back = false, url = '/network') {
    TestBed.configureTestingModule({
      imports: [NetworkNavComponent, TranslateModule.forRoot()],
      providers: [provideRouter([{ path: '**', component: BlankComponent }]),
        { provide: NetworkService, useValue: { mentions: () => of([]), suggestTags: () => of([]) } },
        { provide: NetworkNoticesService, useValue: { unread$: unread, messages$: messages } }]
    });
    await TestBed.inject(Router).navigateByUrl(url);
    const fixture = TestBed.createComponent(NetworkNavComponent);
    fixture.componentRef.setInput('back', back);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('links every section after the search, marks the one open, and counts what is unread', async () => {
    const element = await create(false, '/network/members');
    const links = Array.from(element.querySelectorAll('a.nav-link')).map(link => link.getAttribute('href'));
    expect(links).toEqual(['/network', '/network/messages', '/network/members', '/network/notifications']);
    expect(element.querySelector('app-network-search')).not.toBeNull();
    expect(element.querySelector('[data-feed-members]')?.getAttribute('aria-current')).toBe('page');
    // Home is open on the feed only, not under it.
    expect(element.querySelector('[data-feed-home]')?.getAttribute('aria-current')).toBeNull();
    expect(element.querySelector('[data-feed-unread]')?.textContent?.trim()).toBe('3');
    expect(element.querySelector('[data-feed-unread-messages]')?.textContent?.trim()).toBe('99+');
    expect(element.querySelector('[data-network-back]')).toBeNull();
    expect(element.querySelector('[data-feed-saved], [data-feed-drafts], [data-feed-me]')).toBeNull();
  });

  it('opens with Back on a page reached from another', async () => {
    const element = await create(true, '/network');
    expect(element.querySelector('[data-network-back]')).not.toBeNull();
    expect(element.querySelector('[data-feed-home]')?.getAttribute('aria-current')).toBe('page');
  });
});
