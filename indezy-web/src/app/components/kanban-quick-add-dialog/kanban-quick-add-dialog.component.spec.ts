import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule, FormBuilder } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import {
  KanbanQuickAddDialogComponent,
  KanbanQuickAddDialogData
} from './kanban-quick-add-dialog.component';
import { ProjectService } from '../../services/project/project.service';
import { ClientService } from '../../services/client/client.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ProjectStatus, ProjectDto } from '../../models/project.models';
import { ClientDto } from '../../models/client.models';

describe('KanbanQuickAddDialogComponent', () => {
  let component: KanbanQuickAddDialogComponent;
  let fixture: ComponentFixture<KanbanQuickAddDialogComponent>;
  let mockDialogRef: jasmine.SpyObj<MatDialogRef<KanbanQuickAddDialogComponent>>;
  let mockProjectService: jasmine.SpyObj<ProjectService>;
  let mockClientService: jasmine.SpyObj<ClientService>;
  let mockNotificationService: jasmine.SpyObj<NotificationService>;

  const mockClients: ClientDto[] = [
    { id: 1, companyName: 'Acme', isFinal: true, status: 'ACTIVE' } as ClientDto,
    { id: 2, companyName: 'Inactive Co', isFinal: true, status: 'INACTIVE' } as ClientDto
  ];

  const mockData: KanbanQuickAddDialogData = {
    status: ProjectStatus.APPLIED,
    statusLabel: 'Applied',
    freelanceId: 7
  };

  beforeEach(async () => {
    mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    mockProjectService = jasmine.createSpyObj('ProjectService', ['create']);
    mockClientService = jasmine.createSpyObj('ClientService', ['getClients']);
    mockNotificationService = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    mockClientService.getClients.and.returnValue(of(mockClients));

    await TestBed.configureTestingModule({
      imports: [
        KanbanQuickAddDialogComponent,
        ReactiveFormsModule,
        NoopAnimationsModule,
        TranslateModule.forRoot()
      ],
      providers: [
        FormBuilder,
        { provide: MatDialogRef, useValue: mockDialogRef },
        { provide: MAT_DIALOG_DATA, useValue: mockData },
        { provide: ProjectService, useValue: mockProjectService },
        { provide: ClientService, useValue: mockClientService },
        { provide: NotificationService, useValue: mockNotificationService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(KanbanQuickAddDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load only active clients', () => {
    expect(component).toBeTruthy();
    expect(component.clients).toEqual([mockClients[0]]);
  });

  it('should not submit while the form is invalid', () => {
    component.onSubmit();
    expect(mockProjectService.create).not.toHaveBeenCalled();
  });

  it('should create a project with the column status and close on success', () => {
    mockProjectService.create.and.returnValue(of({ id: 99 } as ProjectDto));
    component.quickAddForm.setValue({ role: 'Backend Dev', clientId: 1, dailyRate: 550 });

    component.onSubmit();

    expect(mockProjectService.create).toHaveBeenCalledWith({
      role: 'Backend Dev',
      dailyRate: 550,
      clientId: 1,
      status: ProjectStatus.APPLIED,
      freelanceId: 7
    });
    expect(mockNotificationService.success).toHaveBeenCalledWith('kanban.quickAddSuccess');
    expect(mockDialogRef.close).toHaveBeenCalledWith(true);
  });

  it('should notify and stay open on create error', () => {
    mockProjectService.create.and.returnValue(throwError(() => new Error('boom')));
    component.quickAddForm.setValue({ role: 'Backend Dev', clientId: 1, dailyRate: 550 });

    component.onSubmit();

    expect(mockNotificationService.error).toHaveBeenCalledWith('kanban.quickAddError');
    expect(component.isSubmitting).toBeFalse();
    expect(mockDialogRef.close).not.toHaveBeenCalled();
  });

  it('should close without creating on cancel', () => {
    component.onCancel();
    expect(mockDialogRef.close).toHaveBeenCalledWith();
    expect(mockProjectService.create).not.toHaveBeenCalled();
  });
});
