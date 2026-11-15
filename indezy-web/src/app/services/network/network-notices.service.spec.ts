import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { NetworkService } from './network.service';
import { NETWORK_NOTICES_REFRESH_MS, NetworkNoticesService } from './network-notices.service';

describe('NetworkNoticesService', () => {
  it('counts the unread notifications now and every minute, none on a failure or once stopped', fakeAsync(() => {
    const network = jasmine.createSpyObj<NetworkService>('NetworkService', ['unreadNotifications']);
    network.unreadNotifications.and.returnValue(of({ unread: 3 }));
    TestBed.configureTestingModule({ providers: [{ provide: NetworkService, useValue: network }] });
    const service = TestBed.inject(NetworkNoticesService);
    const counts: number[] = [];
    service.unread$.subscribe(count => counts.push(count));
    service.start();
    service.start();
    expect(counts.at(-1)).toBe(3);
    network.unreadNotifications.and.returnValue(throwError(() => new Error('not a member')));
    tick(NETWORK_NOTICES_REFRESH_MS);
    expect(counts.at(-1)).toBe(0);
    expect(network.unreadNotifications).toHaveBeenCalledTimes(3);
    service.stop();
    service.stop();
    tick(NETWORK_NOTICES_REFRESH_MS);
    expect(network.unreadNotifications).toHaveBeenCalledTimes(3);
  }));
});
