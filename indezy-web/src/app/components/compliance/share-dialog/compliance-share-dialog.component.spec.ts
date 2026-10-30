import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';

import { ComplianceDocument } from '../../../models';
import { ComplianceShareDialogComponent } from './compliance-share-dialog.component';

describe('ComplianceShareDialogComponent', () => {
  it('shares the documents still valid at first, for whom and how long', () => {
    const close = jasmine.createSpy('close');
    const documents = [{ id: 1, validity: 'VALID' }, { id: 2, validity: 'EXPIRED' }, { id: 3, validity: 'NO_EXPIRY' }] as ComplianceDocument[];
    TestBed.configureTestingModule({
      imports: [ComplianceShareDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: MAT_DIALOG_DATA, useValue: { documents } }, { provide: MatDialogRef, useValue: { close } }]
    });
    const fixture = TestBed.createComponent(ComplianceShareDialogComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    expect([...component.selected]).toEqual([1, 3]);

    component.share();
    expect(close).not.toHaveBeenCalled();
    component.form.patchValue({ recipient: ' Capgemini ', days: 30 });
    component.toggle(1, false);
    component.toggle(3, false);
    expect(component.noneChosen).toBeTrue();
    component.share();
    expect(close).not.toHaveBeenCalled();
    component.toggle(2, true);
    component.share();
    expect(close).toHaveBeenCalledWith({ recipient: 'Capgemini', documentIds: [2], days: 30 });
  });
});
