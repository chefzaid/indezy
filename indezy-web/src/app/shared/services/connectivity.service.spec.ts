import { TestBed } from '@angular/core/testing';
import { ConnectivityService } from './connectivity.service';

describe('ConnectivityService', () => {
  it('follows the browser going offline and back online', () => {
    const service = TestBed.inject(ConnectivityService);
    window.dispatchEvent(new Event('offline'));
    expect(service.online()).toBeFalse();
    window.dispatchEvent(new Event('online'));
    expect(service.online()).toBeTrue();
  });
});
