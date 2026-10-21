import { TestBed } from '@angular/core/testing';
import { Clipboard } from '@angular/cdk/clipboard';
import { provideRouter } from '@angular/router';
import { NotificationService } from '../notification/notification.service';
import { OfferShareService } from './offer-share.service';

describe('OfferShareService', () => {
  let clipboard: jasmine.SpyObj<Clipboard>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let service: OfferShareService;

  beforeEach(() => {
    clipboard = jasmine.createSpyObj('Clipboard', ['copy']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: Clipboard, useValue: clipboard }, { provide: NotificationService, useValue: notifications }]
    });
    service = TestBed.inject(OfferShareService);
  });

  it('copies the full address of an offer\'s page and says so', () => {
    clipboard.copy.and.returnValue(true);

    service.copyLink(7);

    expect(clipboard.copy).toHaveBeenCalledWith(new URL('/offers/7', document.baseURI).href);
    expect(notifications.success).toHaveBeenCalledWith('offers.share.copied');
  });

  it('says when the browser refuses to copy', () => {
    clipboard.copy.and.returnValue(false);

    service.copyLink(7);

    expect(notifications.error).toHaveBeenCalledWith('offers.share.failed');
  });
});
