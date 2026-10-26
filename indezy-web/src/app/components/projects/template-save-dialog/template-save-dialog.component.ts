import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

import { PipelineTemplate, ProjectDto } from '../../../models';
import { PipelineTemplateService } from '../../../services/pipeline-template/pipeline-template.service';
import { NotificationService } from '../../../services/notification/notification.service';

/** The client or ESN a template can be kept for: the opportunity's own ones, or anyone. */
interface ClientChoice {
  id: number;
  name: string;
}

/**
 * Saves an opportunity's steps as a pipeline template: a name, and the client or ESN whose process it is (the ESN by
 * default, since ESNs keep the same process across their clients) or anyone.
 */
@Component({
  selector: 'app-template-save-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, TranslateModule],
  templateUrl: './template-save-dialog.component.html',
  styles: ['.template-form { display: flex; flex-direction: column; min-width: min(400px, 80vw); padding-top: 4px; }'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class TemplateSaveDialogComponent {
  readonly choices: ClientChoice[];
  readonly form: FormGroup;
  saving = false;

  constructor(
    fb: FormBuilder,
    @Inject(MAT_DIALOG_DATA) private readonly project: ProjectDto,
    private readonly dialogRef: MatDialogRef<TemplateSaveDialogComponent, PipelineTemplate>,
    private readonly templateService: PipelineTemplateService,
    private readonly notificationService: NotificationService
  ) {
    this.choices = [
      { id: project.middlemanId, name: project.middlemanName },
      { id: project.clientId, name: project.clientName }
    ].filter((choice): choice is ClientChoice => !!choice.id && !!choice.name);
    this.form = fb.group({
      name: [project.middlemanName ?? project.clientName ?? '', [Validators.required, Validators.maxLength(100)]],
      clientId: [this.choices[0]?.id ?? null]
    });
  }

  save(): void {
    if (this.form.invalid || this.saving) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving = true;
    const { name, clientId } = this.form.value;
    this.templateService.create(name.trim(), this.project.id!, clientId ?? undefined).subscribe({
      next: template => {
        this.notificationService.success('steps.templates.saved');
        this.dialogRef.close(template);
      },
      error: (error: HttpErrorResponse) => {
        this.saving = false;
        if (error.status === 409 && String(error.error?.message).includes('already named')) {
          this.form.get('name')!.setErrors({ taken: true });
        } else {
          this.notificationService.error(error.status === 409 ? 'steps.templates.limit' : 'steps.templates.saveError');
        }
      }
    });
  }
}
