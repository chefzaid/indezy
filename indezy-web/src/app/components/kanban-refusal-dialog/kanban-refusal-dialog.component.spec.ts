import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule, FormBuilder } from '@angular/forms';
import { MatDialog, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';

import {
  KanbanRefusalDialogComponent,
  KanbanRefusalDialogData
} from './kanban-refusal-dialog.component';

describe('KanbanRefusalDialogComponent', () => {
  let component: KanbanRefusalDialogComponent;
  let fixture: ComponentFixture<KanbanRefusalDialogComponent>;
  let mockDialogRef: jasmine.SpyObj<MatDialogRef<KanbanRefusalDialogComponent>>;

  const mockData: KanbanRefusalDialogData = { role: 'Backend Developer' };

  beforeEach(async () => {
    mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [
        KanbanRefusalDialogComponent,
        ReactiveFormsModule,
        TranslateModule.forRoot()
      ],
      providers: [
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        FormBuilder,
        { provide: MatDialogRef, useValue: mockDialogRef },
        { provide: MAT_DIALOG_DATA, useValue: mockData }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(KanbanRefusalDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should offer both sides, the recruiter first, and the full list of reasons', () => {
    expect(component.sides).toEqual(['RECRUITER', 'FREELANCE']);
    expect(component.lostReasons.length).toBe(7);
    const options = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-refused-by-option]');
    expect(Array.from(options).map(option => option.getAttribute('data-refused-by-option'))).toEqual(['RECRUITER', 'FREELANCE']);
  });

  it('should not close while nobody is said to have refused', () => {
    component.refusalForm.setValue({ refusedBy: null, lostReason: 'NO_RESPONSE' });

    component.onConfirm();

    expect(mockDialogRef.close).not.toHaveBeenCalled();
  });

  it('should close with who refused and why on confirm', () => {
    component.refusalForm.setValue({ refusedBy: 'RECRUITER', lostReason: 'NO_RESPONSE' });

    component.onConfirm();

    expect(mockDialogRef.close).toHaveBeenCalledWith({ refusedBy: 'RECRUITER', lostReason: 'NO_RESPONSE' });
  });

  it('should close with who refused alone, the reason being optional', () => {
    component.refusalForm.setValue({ refusedBy: 'FREELANCE', lostReason: null });

    component.onConfirm();

    expect(mockDialogRef.close).toHaveBeenCalledWith({ refusedBy: 'FREELANCE' });
  });

  it('should close with no value on cancel', () => {
    component.onCancel();
    expect(mockDialogRef.close).toHaveBeenCalledWith();
  });

  it('opens focused on the dialog itself, so the Enter that opened it cannot open the reason select', () => {
    const dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    const data: KanbanRefusalDialogData = { role: 'Tech Lead' };

    KanbanRefusalDialogComponent.open(dialog, data);

    expect(dialog.open).toHaveBeenCalledWith(KanbanRefusalDialogComponent, { data, autoFocus: 'dialog' });
  });
});
