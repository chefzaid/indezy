import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { PublicCompliancePack } from '../../models';
import { ComplianceService } from '../../services/compliance/compliance.service';
import { NotificationService } from '../../services/notification/notification.service';
import { CompliancePackComponent } from './compliance-pack.component';
import en from '../../../assets/i18n/en.json';

describe('CompliancePackComponent', () => {
  let service: jasmine.SpyObj<ComplianceService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const pack: PublicCompliancePack = {
    sharedBy: 'Alice Martin', recipient: 'Capgemini', expiresAt: '2026-11-03T10:00:00Z',
    documents: [{ id: 4, type: 'URSSAF_VIGILANCE', label: null, fileName: 'attestation.pdf', issuedOn: '2026-10-01', expiresOn: '2027-04-01' }]
  };

  function create(fragment: string | null, answer = of(pack)): HTMLElement {
    service = jasmine.createSpyObj('ComplianceService', ['publicPack', 'publicDocument']);
    service.publicPack.and.returnValue(answer);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [CompliancePackComponent, TranslateModule.forRoot()],
      providers: [provideNoopAnimations(), provideRouter([]), { provide: ComplianceService, useValue: service },
        { provide: NotificationService, useValue: notifications }, { provide: ActivatedRoute, useValue: { snapshot: { fragment } } }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(CompliancePackComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the shared documents to download with the token of the link', () => {
    const element = create(' token-1 ');
    expect(service.publicPack).toHaveBeenCalledWith('token-1');
    expect(element.textContent).toContain('Alice Martin shares these documents with Capgemini.');
    expect(element.querySelector('[data-pack-document="4"]')!.textContent).toContain('URSSAF vigilance certificate');

    const link = document.createElement('a');
    spyOn(document, 'createElement').and.returnValue(link);
    spyOn(link, 'click');
    service.publicDocument.and.returnValue(of(new Blob(['%PDF'])));
    (element.querySelector('[data-pack-document="4"] button') as HTMLButtonElement).click();
    expect(service.publicDocument).toHaveBeenCalledWith('token-1', 4);
    expect(link.download).toBe('attestation.pdf');
    service.publicDocument.and.returnValue(throwError(() => new Error('gone')));
    (element.querySelector('[data-pack-document="4"] button') as HTMLButtonElement).click();
    expect(notifications.error).toHaveBeenCalledWith('compliance.pack.downloadError');
  });

  it('says a link without a token, or one no longer valid, does not work', () => {
    expect(create(null).querySelector('[data-pack-invalid]')).not.toBeNull();
    expect(service.publicPack).not.toHaveBeenCalled();
    TestBed.resetTestingModule();
    expect(create('old', throwError(() => new Error('404'))).querySelector('[data-pack-invalid]')).not.toBeNull();
  });

  it('says when no document is shared any more', () => {
    expect(create('token', of({ ...pack, documents: [] })).textContent).toContain('No document is shared any more.');
  });
});
