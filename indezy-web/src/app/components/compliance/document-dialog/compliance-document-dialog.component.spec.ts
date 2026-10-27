import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';

import { ComplianceDocument } from '../../../models';
import { ComplianceDocumentDialogComponent, ComplianceDocumentDialogData } from './compliance-document-dialog.component';

describe('ComplianceDocumentDialogComponent', () => {
  let close: jasmine.Spy;

  function create(data: ComplianceDocumentDialogData): ComplianceDocumentDialogComponent {
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [ComplianceDocumentDialogComponent, TranslateModule.forRoot()],
      providers: [provideNoopAnimations(), { provide: MAT_DIALOG_DATA, useValue: data }, { provide: MatDialogRef, useValue: { close } }]
    });
    const fixture = TestBed.createComponent(ComplianceDocumentDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const picked = (file: File | null): Event => ({ target: { files: file ? [file] : [] } }) as unknown as Event;

  it('stores a new document only with a PDF or image file of 10 MB at most', () => {
    const component = create({});
    expect(component.validityMonths).toBe(6);
    component.save();
    expect(component.fileError).toBe('required');
    expect(close).not.toHaveBeenCalled();

    component.pick(picked(new File(['x'], 'page.html')));
    expect(component.fileError).toBe('type');
    const big = new File(['x'], 'big.pdf');
    Object.defineProperty(big, 'size', { value: 11 * 1024 * 1024 });
    component.pick(picked(big));
    expect(component.fileError).toBe('tooLarge');
    component.pick(picked(null));
    expect(component.fileError).toBe('required');

    const file = new File(['%PDF'], 'attestation.PDF');
    component.pick(picked(file));
    expect(component.fileError).toBeNull();
    component.form.patchValue({ issuedOn: '2026-10-01', label: '  ' });
    component.save();
    expect(close).toHaveBeenCalledWith({ file, details: { type: 'URSSAF_VIGILANCE', label: null, issuedOn: '2026-10-01', expiresOn: null } });
  });

  it('asks a name for another document, and an end after the issue', () => {
    const component = create({});
    component.pick(picked(new File(['%PDF'], 'x.jpeg')));
    component.form.patchValue({ type: 'OTHER' });
    expect(component.validityMonths).toBeUndefined();
    expect(component.form.hasError('labelRequired')).toBeTrue();
    component.form.patchValue({ label: 'Diploma', issuedOn: '2026-10-01', expiresOn: '2026-09-30' });
    expect(component.form.hasError('expiresBeforeIssue')).toBeTrue();
    component.save();
    expect(close).not.toHaveBeenCalled();
  });

  it('changes a stored document without its file', () => {
    const document = { id: 4, type: 'IDENTITY', label: 'Passport', issuedOn: '2020-01-01', expiresOn: '2030-01-01' } as ComplianceDocument;
    const component = create({ document });
    component.save();
    expect(close).toHaveBeenCalledWith({ file: null, details: { type: 'IDENTITY', label: 'Passport', issuedOn: '2020-01-01',
      expiresOn: '2030-01-01' } });
  });
});
