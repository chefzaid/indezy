import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule, FormBuilder } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { QuickAddCardDialogComponent, QuickAddCardDialogData } from './quick-add-card-dialog.component';
import { ClientService } from '../../services/client/client.service';
import { ProjectService } from '../../services/project/project.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ClientDto } from '../../models/client.models';
import { ProjectDto, ProjectStatus } from '../../models/project.models';

describe('QuickAddCardDialogComponent', () => {
  let component: QuickAddCardDialogComponent;
  let fixture: ComponentFixture<QuickAddCardDialogComponent>;
  let mockDialogRef: jasmine.SpyObj<MatDialogRef<QuickAddCardDialogComponent>>;
  let mockClientService: jasmine.SpyObj<ClientService>;
  let mockProjectService: jasmine.SpyObj<ProjectService>;
  let mockNotificationService: jasmine.SpyObj<NotificationService>;

  const mockClients: ClientDto[] = [
    { id: 1, companyName: 'Acme', city: 'Paris', isFinal: true },
    { id: 2, companyName: 'Globex', city: 'Lyon', isFinal: false }
  ];

  const dialogData: QuickAddCardDialogData = {
    freelanceId: 7,
    status: ProjectStatus.APPLIED
  };

  async function setup(clientsResponse = of(mockClients)): Promise<void> {
    mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    mockClientService = jasmine.createSpyObj('ClientService', ['getByFreelanceId']);
    mockProjectService = jasmine.createSpyObj('ProjectService', ['create']);
    mockNotificationService = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    mockClientService.getByFreelanceId.and.returnValue(clientsResponse);

    await TestBed.configureTestingModule({
      imports: [
        QuickAddCardDialogComponent,
        ReactiveFormsModule,
        NoopAnimationsModule,
        TranslateModule.forRoot()
      ],
      providers: [
        FormBuilder,
        { provide: MatDialogRef, useValue: mockDialogRef },
        { provide: MAT_DIALOG_DATA, useValue: dialogData },
        { provide: ClientService, useValue: mockClientService },
        { provide: ProjectService, useValue: mockProjectService },
        { provide: NotificationService, useValue: mockNotificationService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(QuickAddCardDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('should create and load clients for the freelance', async () => {
    await setup();
    expect(component).toBeTruthy();
    expect(mockClientService.getByFreelanceId).toHaveBeenCalledWith(7);
    expect(component.clients).toEqual(mockClients);
    expect(component.isLoadingClients).toBeFalse();
  });

  it('should flag the no-clients state when none exist', async () => {
    await setup(of([]));
    expect(component.hasNoClients).toBeTrue();
  });

  it('should surface an error when client loading fails', async () => {
    await setup(throwError(() => new Error('boom')));
    expect(mockNotificationService.error).toHaveBeenCalledWith('errors.loadingClients');
    expect(component.isLoadingClients).toBeFalse();
  });

  it('should be invalid until role, client and daily rate are provided', async () => {
    await setup();
    expect(component.form.valid).toBeFalse();
    component.form.setValue({ role: 'Dev', clientId: 1, dailyRate: 500 });
    expect(component.form.valid).toBeTrue();
  });

  it('should reject a negative daily rate', async () => {
    await setup();
    component.form.setValue({ role: 'Dev', clientId: 1, dailyRate: -1 });
    expect(component.form.get('dailyRate')?.hasError('min')).toBeTrue();
  });

  it('should create the project with the column status and close with the result', async () => {
    await setup();
    const created: ProjectDto = { id: 99, role: 'Dev', dailyRate: 500, status: ProjectStatus.APPLIED };
    mockProjectService.create.and.returnValue(of(created));

    component.form.setValue({ role: '  Dev  ', clientId: 2, dailyRate: 500 });
    component.onSubmit();

    expect(mockProjectService.create).toHaveBeenCalledWith({
      role: 'Dev',
      dailyRate: 500,
      clientId: 2,
      freelanceId: 7,
      status: ProjectStatus.APPLIED
    });
    expect(mockNotificationService.success).toHaveBeenCalledWith('kanban.quickAdd.success', 2000);
    expect(mockDialogRef.close).toHaveBeenCalledWith(created);
  });

  it('should not submit an invalid form', async () => {
    await setup();
    component.onSubmit();
    expect(mockProjectService.create).not.toHaveBeenCalled();
  });

  it('should keep the dialog open and notify on create failure', async () => {
    await setup();
    mockProjectService.create.and.returnValue(throwError(() => new Error('fail')));

    component.form.setValue({ role: 'Dev', clientId: 1, dailyRate: 500 });
    component.onSubmit();

    expect(mockNotificationService.error).toHaveBeenCalledWith('kanban.quickAdd.error');
    expect(component.isSubmitting).toBeFalse();
    expect(mockDialogRef.close).not.toHaveBeenCalled();
  });

  it('should close without creating on cancel', async () => {
    await setup();
    component.onCancel();
    expect(mockDialogRef.close).toHaveBeenCalledWith();
    expect(mockProjectService.create).not.toHaveBeenCalled();
  });
});
