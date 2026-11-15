import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

import { CapabilitiesService, DEFAULT_CAPABILITIES } from '../../../services/admin/capabilities.service';
import { NetworkShare, SHARE_STATE, ShareToNetworkComponent } from './share-to-network.component';

describe('ShareToNetworkComponent', () => {
  const shared: NetworkShare = { text: 'Une mission', kind: 'MISSION', details: null };

  function create(networkOn: boolean) {
    TestBed.configureTestingModule({ imports: [ShareToNetworkComponent, TranslateModule.forRoot()], providers: [provideRouter([])] });
    spyOn(TestBed.inject(CapabilitiesService), 'snapshot').and.returnValue({ admin: false,
      features: { ...DEFAULT_CAPABILITIES.features, SOCIAL_NETWORK: networkOn } });
    const fixture = TestBed.createComponent(ShareToNetworkComponent);
    fixture.componentRef.setInput('build', () => shared);
    fixture.detectChanges();
    return fixture;
  }

  it('opens the feed with the shared draft while the network is on, and shows nothing while it is off', () => {
    const fixture = create(true);
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    (fixture.nativeElement.querySelector('[data-share-to-network]') as HTMLButtonElement).click();
    expect(navigate).toHaveBeenCalledWith(['/network'], { state: { [SHARE_STATE]: shared } });
    TestBed.resetTestingModule();
    expect(create(false).nativeElement.querySelector('[data-share-to-network]')).toBeNull();
  });
});
