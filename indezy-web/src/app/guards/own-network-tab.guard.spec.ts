import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom, of, throwError } from 'rxjs';

import { NetworkMembership } from '../models';
import { NetworkService } from '../services/network/network.service';
import { ownNetworkTab } from './own-network-tab.guard';

describe('ownNetworkTab', () => {
  function run(tab: 'saved' | 'drafts', membership: Observable<NetworkMembership>): Promise<string> {
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: NetworkService, useValue: { membership: () => membership } }] });
    const result = TestBed.runInInjectionContext(() => ownNetworkTab(tab)({} as never, {} as never)) as Observable<UrlTree>;
    return firstValueFrom(result).then(tree => TestBed.inject(Router).serializeUrl(tree));
  }

  it('leads to the tab of the reader\'s own page', async () => {
    expect(await run('saved', of({ joined: true, handle: 'camille' } as NetworkMembership))).toBe('/m/camille?tab=saved');
    TestBed.resetTestingModule();
    expect(await run('drafts', of({ joined: true, handle: 'camille' } as NetworkMembership))).toBe('/m/camille?tab=drafts');
  });

  it('leads a visitor, or a failure, to the feed', async () => {
    expect(await run('saved', of({ joined: false, handle: 'suggested' } as NetworkMembership))).toBe('/network');
    TestBed.resetTestingModule();
    expect(await run('drafts', throwError(() => new Error('down')))).toBe('/network');
  });
});
