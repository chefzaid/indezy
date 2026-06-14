import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';

import { ClientService } from '../../services/client/client.service';
import { ProjectService } from '../../services/project/project.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ClientDto } from '../../models/client.models';
import { ProjectDto, ProjectStatus } from '../../models/project.models';

export interface QuickAddCardDialogData {
  freelanceId: number;
  status: ProjectStatus;
}

@Component({
  selector: 'app-quick-add-card-dialog',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule
  ],
  templateUrl: './quick-add-card-dialog.component.html',
  styleUrls: ['./quick-add-card-dialog.component.scss']
})
export class QuickAddCardDialogComponent implements OnInit {
  form: FormGroup;
  clients: ClientDto[] = [];
  isLoadingClients = true;
  isSubmitting = false;

  constructor(
    private readonly fb: FormBuilder,
    private readonly dialogRef: MatDialogRef<QuickAddCardDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: QuickAddCardDialogData,
    private readonly clientService: ClientService,
    private readonly projectService: ProjectService,
    private readonly notificationService: NotificationService
  ) {
    this.form = this.fb.group({
      role: ['', [Validators.required, Validators.maxLength(255)]],
      clientId: [null, Validators.required],
      dailyRate: [null, [Validators.required, Validators.min(0)]]
    });
  }

  ngOnInit(): void {
    this.clientService.getByFreelanceId(this.data.freelanceId).subscribe({
      next: (clients) => {
        this.clients = clients;
        this.isLoadingClients = false;
      },
      error: () => {
        this.notificationService.error('errors.loadingClients');
        this.isLoadingClients = false;
      }
    });
  }

  get hasNoClients(): boolean {
    return !this.isLoadingClients && this.clients.length === 0;
  }

  onSubmit(): void {
    if (this.form.invalid || this.isSubmitting) {
      return;
    }
    this.isSubmitting = true;

    const value = this.form.value;
    const project: ProjectDto = {
      role: value.role.trim(),
      dailyRate: value.dailyRate,
      clientId: value.clientId,
      freelanceId: this.data.freelanceId,
      status: this.data.status
    };

    this.projectService.create(project).subscribe({
      next: (created) => {
        this.notificationService.success('kanban.quickAdd.success', 2000);
        this.dialogRef.close(created);
      },
      error: () => {
        this.notificationService.error('kanban.quickAdd.error');
        this.isSubmitting = false;
      }
    });
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}
