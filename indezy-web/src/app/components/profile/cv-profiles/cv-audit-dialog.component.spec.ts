import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { CvProfile } from '../../../models';
import { CvProfileService } from '../../../services/cv-profile/cv-profile.service';
import { CvAuditDialogComponent } from './cv-audit-dialog.component';

describe('CvAuditDialogComponent', () => {
  let service: jasmine.SpyObj<CvProfileService>;

  function create(): { component: CvAuditDialogComponent; element: () => HTMLElement } {
    service = jasmine.createSpyObj('CvProfileService', ['audit']);
    service.audit.and.returnValue(of({ pages: 2, findings: [{ check: 'LEAK', severity: 'ERROR', block: 'Lead Kafka', detail: 'Alice Corp' },
      { check: 'NO_FIGURE', severity: 'INFO', block: 'Lead Kafka', detail: null }] }));
    TestBed.configureTestingModule({
      imports: [CvAuditDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: CvProfileService, useValue: service }, { provide: MAT_DIALOG_DATA, useValue: { profile: { id: 3, name: 'Backend' } as CvProfile } }]
    });
    const fixture = TestBed.createComponent(CvAuditDialogComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, element: () => {
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
    } };
  }

  it('checks the anonymized dossier first, the most serious finding first, then the named one when asked', () => {
    const { component, element } = create();
    expect(service.audit).toHaveBeenCalledWith(3, 'ANONYMOUS');
    expect(element().querySelectorAll('[data-cv-audit-finding]').length).toBe(2);
    expect(element().querySelector('[data-cv-audit-finding="LEAK"]')).not.toBeNull();
    expect(component.icon(component.audit!.findings[0])).toBe('error');
    expect(component.icon(component.audit!.findings[1])).toBe('info');

    service.audit.and.returnValue(of({ pages: 1, findings: [] }));
    component.layout = 'NAMED';
    component.load();
    expect(service.audit).toHaveBeenCalledWith(3, 'NAMED');
    expect(element().querySelector('[data-cv-audit-clean]')).not.toBeNull();
    service.audit.and.returnValue(throwError(() => new Error('down')));
    component.load();
    expect(component.audit).toEqual({ pages: 0, findings: [] });
  });
});
