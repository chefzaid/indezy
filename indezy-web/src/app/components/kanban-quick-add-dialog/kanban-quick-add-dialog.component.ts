import { Component, Inject, OnInit } from '@angular/core';

import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { ProjectService } from '../../services/project/project.service';
import { ClientService } from '../../services/client/client.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ClientDto } from '../../models/client.models';
import { CreateProjectDto, ProjectStatus } from '../../models/project.models';

export interface KanbanQuickAddDialogData {
  status: ProjectStatus;
  statusLabel: string;
  freelanceId: number;
}

@Component({
  selector: 'app-kanban-quick-add-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    TranslateModule
],
  templateUrl: './kanban-quick-add-dialog.component.html',
  styleUrls: ['./kanban-quick-add-dialog.component.scss']
})
export class KanbanQuickAddDialogComponent implements OnInit {
  quickAddForm: FormGroup;
  clients: ClientDto[] = [];
  isSubmitting = false;

  constructor(
    private readonly fb: FormBuilder,
    private readonly dialogRef: MatDialogRef<KanbanQuickAddDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: KanbanQuickAddDialogData,
    private readonly projectService: ProjectService,
    private readonly clientService: ClientService,
    private readonly notificationService: NotificationService
  ) {
    this.quickAddForm = this.fb.group({
      role: ['', [Validators.required, Validators.minLength(2)]],
      clientId: ['', Validators.required],
      dailyRate: ['', [Validators.required, Validators.min(1)]]
    });
  }

  ngOnInit(): void {
    this.clientService.getClients().subscribe({
      next: (clients) => {
        this.clients = clients.filter(client => client.status === 'ACTIVE');
      },
      error: () => this.notificationService.error('errors.loadingClients')
    });
  }

  onSubmit(): void {
    if (this.quickAddForm.valid && !this.isSubmitting) {
      this.isSubmitting = true;
      const formValue = this.quickAddForm.value;
      const projectData: CreateProjectDto = {
        role: formValue.role,
        dailyRate: formValue.dailyRate,
        clientId: formValue.clientId,
        status: this.data.status,
        freelanceId: this.data.freelanceId
      };

      this.projectService.create(projectData).subscribe({
        next: () => {
          this.notificationService.success('kanban.quickAddSuccess');
          this.dialogRef.close(true);
        },
        error: () => {
          this.notificationService.error('kanban.quickAddError');
          this.isSubmitting = false;
        }
      });
    }
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}
