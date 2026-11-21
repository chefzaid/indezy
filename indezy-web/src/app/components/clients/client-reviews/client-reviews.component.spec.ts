import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import en from '../../../../assets/i18n/en.json';
import { CompanyReviews } from '../../../models';
import { CapabilitiesService } from '../../../services/admin/capabilities.service';
import { NetworkReviewService } from '../../../services/network-review/network-review.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ClientReviewsComponent } from './client-reviews.component';

describe('ClientReviewsComponent', () => {
  let reviews: jasmine.SpyObj<NetworkReviewService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let network = true;
  const company: CompanyReviews = { companyName: 'Capgemini', kind: 'ESN', count: 1, paymentAverage: 2, processAverage: 4, rateAverage: 3,
    reviews: [{ id: 5, kind: 'ESN', paymentRating: 2, processRating: 4, rateRating: 3, text: 'Paid late, but a quick process.',
      updatedAt: '2026-11-20T10:00:00', mine: false }], mine: null, canReview: true };

  function create(answer = of(company)): ComponentFixture<ClientReviewsComponent> {
    reviews = jasmine.createSpyObj('NetworkReviewService', ['company', 'write', 'delete']);
    reviews.company.and.returnValue(answer);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    TestBed.configureTestingModule({
      imports: [ClientReviewsComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: NetworkReviewService, useValue: reviews }, { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm }, { provide: MatDialog, useValue: dialog },
        { provide: CapabilitiesService, useValue: { snapshot: () => ({ features: { SOCIAL_NETWORK: network } }) } }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ClientReviewsComponent);
    fixture.componentInstance.clientId = 3;
    fixture.componentInstance.ngOnChanges();
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => network = true);

  it('shows the averages and the reviews without their authors, each reportable', () => {
    const fixture = create();
    const element: HTMLElement = fixture.nativeElement;
    expect(reviews.company).toHaveBeenCalledWith(3);
    expect(element.querySelector('[data-reviews-averages]')?.textContent).toContain('2.0 / 5');
    expect(element.querySelector('[data-review="5"]')?.textContent).toContain('A member');
    dialog.open.and.returnValue({ afterClosed: () => of(undefined) } as never);
    (element.querySelector('[data-report-review]') as HTMLButtonElement).click();
    expect(dialog.open).toHaveBeenCalled();
  });

  it('writes a review with its three ratings, and says when the moderators hold it', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    component.write();
    component.save();
    expect(reviews.write).not.toHaveBeenCalled();
    component.setRating('paymentRating', 2);
    component.setRating('processRating', 4);
    component.setRating('rateRating', 3);
    component.form.controls.text.setValue(' Paid late, but a quick process and honest rates. ');
    reviews.write.and.returnValue(of({ ...company, mine: { id: 6, status: 'HELD', paymentRating: 2, processRating: 4, rateRating: 3,
      text: 'x', heldReasons: ['PHONE'], updatedAt: '2026-11-21T10:00:00' } }));
    component.save();
    expect(reviews.write).toHaveBeenCalledWith(3, { paymentRating: 2, processRating: 4, rateRating: 3,
      text: 'Paid late, but a quick process and honest rates.' });
    expect(notifications.success).toHaveBeenCalledWith('clients.reviews.held');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-my-review-status="HELD"]')).not.toBeNull();
    expect(component.writing).toBeFalse();

    // Rewritten from the one held, then deleted.
    component.write();
    expect(component.form.value.paymentRating).toBe(2);
    confirm.confirm.and.returnValue(of(true));
    reviews.delete.and.returnValue(of(undefined));
    component.remove();
    expect(reviews.delete).toHaveBeenCalledWith(3);
    expect(notifications.success).toHaveBeenCalledWith('clients.reviews.deleted');
  });

  it('asks a non-member to join, and shows nothing without the network', () => {
    const fixture = create(throwError(() => new HttpErrorResponse({ status: 409 })));
    expect(fixture.componentInstance.notMember).toBeTrue();
    expect(fixture.nativeElement.querySelector('[data-reviews-join]')).not.toBeNull();
    TestBed.resetTestingModule();
    network = false;
    const off = create();
    expect(reviews.company).not.toHaveBeenCalled();
    expect(off.nativeElement.querySelector('[data-client-reviews]')).toBeNull();
  });

  it('tells a failed save', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    component.form.setValue({ paymentRating: 1, processRating: 1, rateRating: 1, text: 'Long enough text for a review here.' });
    reviews.write.and.returnValue(throwError(() => new Error('down')));
    component.save();
    expect(notifications.error).toHaveBeenCalledWith('clients.reviews.saveError');
    expect(component.saving).toBeFalse();
  });
});
