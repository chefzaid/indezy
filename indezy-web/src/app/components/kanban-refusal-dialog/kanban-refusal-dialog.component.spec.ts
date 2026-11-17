import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule, FormBuilder } from '@angular/forms';
import { MatDialog, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';

import { ProjectStatus } from '../../models/project.models';

import {
  KanbanRefusalDialogComponent,
  KanbanRefusalDialogData
} from './kanban-refusal-dialog.component';

describe('KanbanRefusalDialogComponent', () => {
  let component: KanbanRefusalDialogComponent;
  let fixture: ComponentFixture<KanbanRefusalDialogComponent>;
  let mockDialogRef: jasmine.SpyObj<MatDialogRef<KanbanRefusalDialogComponent>>;

  let mockData: KanbanRefusalDialogData;

  async function create(status: ProjectStatus.LOST | ProjectStatus.WITHDRAWN): Promise<HTMLElement> {
    mockData = { role: 'Backend Developer', status };
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
    return fixture.nativeElement;
  }

  it('asks only why, the side being the column it was dropped on, with the full list of reasons', async () => {
    const element = await create(ProjectStatus.LOST);
    expect(component.lostReasons.length).toBe(7);
    expect(element.querySelector('h2')?.textContent).toContain('kanban.refusalTitle.LOST');
    expect(element.querySelector('h2 mat-icon')?.textContent).toBe('thumb_down');
    expect(element.querySelector('mat-radio-group')).toBeNull();
    expect(element.querySelector('[data-lost-reason-field]')).not.toBeNull();
  });

  it('titles a withdrawal as one', async () => {
    const element = await create(ProjectStatus.WITHDRAWN);
    expect(element.querySelector('h2')?.textContent).toContain('kanban.refusalTitle.WITHDRAWN');
    expect(element.querySelector('h2 mat-icon')?.textContent).toBe('back_hand');
  });

  it('should close with why on confirm', async () => {
    await create(ProjectStatus.LOST);
    component.refusalForm.setValue({ lostReason: 'NO_RESPONSE' });

    component.onConfirm();

    expect(mockDialogRef.close).toHaveBeenCalledWith({ lostReason: 'NO_RESPONSE' });
  });

  it('should close without a reason, the reason being optional', async () => {
    await create(ProjectStatus.WITHDRAWN);

    component.onConfirm();

    expect(mockDialogRef.close).toHaveBeenCalledWith({});
  });

  it('should close with no value on cancel', async () => {
    await create(ProjectStatus.LOST);
    component.onCancel();
    expect(mockDialogRef.close).toHaveBeenCalledWith();
  });

  it('opens focused on the dialog itself, so the Enter that opened it cannot open the reason select', () => {
    const dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    const data: KanbanRefusalDialogData = { role: 'Tech Lead', status: ProjectStatus.WITHDRAWN };

    KanbanRefusalDialogComponent.open(dialog, data);

    expect(dialog.open).toHaveBeenCalledWith(KanbanRefusalDialogComponent, { data, autoFocus: 'dialog' });
  });
});
