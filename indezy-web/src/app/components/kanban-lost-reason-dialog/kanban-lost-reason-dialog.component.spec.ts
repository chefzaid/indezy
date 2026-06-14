import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule, FormBuilder } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';

import {
  KanbanLostReasonDialogComponent,
  KanbanLostReasonDialogData
} from './kanban-lost-reason-dialog.component';

describe('KanbanLostReasonDialogComponent', () => {
  let component: KanbanLostReasonDialogComponent;
  let fixture: ComponentFixture<KanbanLostReasonDialogComponent>;
  let mockDialogRef: jasmine.SpyObj<MatDialogRef<KanbanLostReasonDialogComponent>>;

  const mockData: KanbanLostReasonDialogData = { role: 'Backend Developer' };

  beforeEach(async () => {
    mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [
        KanbanLostReasonDialogComponent,
        ReactiveFormsModule,
        NoopAnimationsModule,
        TranslateModule.forRoot()
      ],
      providers: [
        FormBuilder,
        { provide: MatDialogRef, useValue: mockDialogRef },
        { provide: MAT_DIALOG_DATA, useValue: mockData }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(KanbanLostReasonDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create with the full list of reasons', () => {
    expect(component).toBeTruthy();
    expect(component.lostReasons.length).toBe(7);
  });

  it('should not close with a reason while no reason is selected', () => {
    component.onConfirm();
    expect(mockDialogRef.close).not.toHaveBeenCalled();
  });

  it('should close with the selected reason on confirm', () => {
    component.reasonForm.setValue({ lostReason: 'NO_RESPONSE' });

    component.onConfirm();

    expect(mockDialogRef.close).toHaveBeenCalledWith('NO_RESPONSE');
  });

  it('should close with no value on cancel', () => {
    component.onCancel();
    expect(mockDialogRef.close).toHaveBeenCalledWith();
  });
});
