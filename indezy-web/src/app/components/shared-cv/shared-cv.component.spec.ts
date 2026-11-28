import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { CvShareService } from '../../services/cv-profile/cv-share.service';
import { NotificationService } from '../../services/notification/notification.service';
import { SharedCvComponent } from './shared-cv.component';

describe('SharedCvComponent', () => {
  let service: jasmine.SpyObj<CvShareService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  function create(fragment: string | null): { component: SharedCvComponent; element: HTMLElement } {
    service = jasmine.createSpyObj('CvShareService', ['open', 'dossier']);
    service.open.and.returnValue(of({ name: 'A. M.', headline: 'Lead Kafka', language: 'FR', layout: 'ANONYMOUS', expiresAt: '2026-12-05T08:00:00Z' }));
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [SharedCvComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: ActivatedRoute, useValue: { snapshot: { fragment } } },
        { provide: CvShareService, useValue: service }, { provide: NotificationService, useValue: notifications }]
    });
    const fixture = TestBed.createComponent(SharedCvComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, element: fixture.nativeElement as HTMLElement };
  }

  it('opens the CV the link shares, and downloads it named after whose it is', () => {
    const { component, element } = create(' secret ');
    expect(service.open).toHaveBeenCalledWith('secret');
    expect(element.querySelector('[data-shared-cv-name]')?.textContent).toContain('A. M.');
    const link = document.createElement('a');
    spyOn(document, 'createElement').and.returnValue(link);
    spyOn(link, 'click');
    service.dossier.and.returnValue(of(new Blob(['%PDF'])));
    component.download('DOCX');
    expect(service.dossier).toHaveBeenCalledWith('secret', 'DOCX');
    expect(link.download).toBe('cv-a-m.docx');
    service.dossier.and.returnValue(throwError(() => new Error('gone')));
    component.download('PDF');
    expect(notifications.error).toHaveBeenCalledWith('cvShares.page.downloadError');
  });

  it('says the link no longer opens, without a token or when it is refused', () => {
    expect(create(null).element.querySelector('[data-shared-cv-invalid]')).not.toBeNull();
    TestBed.resetTestingModule();
    service = jasmine.createSpyObj('CvShareService', ['open']);
    service.open.and.returnValue(throwError(() => new Error('404')));
    TestBed.configureTestingModule({
      imports: [SharedCvComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: ActivatedRoute, useValue: { snapshot: { fragment: 'old' } } }, { provide: CvShareService, useValue: service }]
    });
    const fixture = TestBed.createComponent(SharedCvComponent);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-shared-cv-invalid]')).not.toBeNull();
  });
});
