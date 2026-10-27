import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

import { EmailTemplate } from '../../../models';
import { EmailTemplateService } from '../../../services/email-template/email-template.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { EMAIL_VARIABLES } from '../../../shared/utils/email-template';

/** Profile → Emails: the workspace's own email templates, written with variables, added, changed and deleted. */
@Component({
  selector: 'app-email-templates-settings',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, TranslateModule],
  templateUrl: './email-templates-settings.component.html',
  styleUrls: ['./email-templates-settings.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class EmailTemplatesSettingsComponent implements OnInit {
  readonly variables = EMAIL_VARIABLES;
  readonly form: FormGroup;
  templates: EmailTemplate[] = [];
  /** The template being written: a saved one's id, 'new', or none while the form is closed. */
  editing: number | 'new' | null = null;
  saving = false;

  constructor(
    fb: FormBuilder,
    private readonly templateService: EmailTemplateService,
    private readonly notificationService: NotificationService,
    private readonly confirmDialog: ConfirmDialogService,
    private readonly changes: ChangeDetectorRef
  ) {
    this.form = fb.group({
      name: ['', [Validators.required, Validators.maxLength(100)]],
      subject: ['', [Validators.required, Validators.maxLength(200)]],
      body: ['', [Validators.required, Validators.maxLength(5000)]]
    });
  }

  ngOnInit(): void {
    this.templateService.list().subscribe({
      next: templates => this.show(templates),
      error: () => this.notificationService.error('emails.settings.loadError')
    });
  }

  add(): void {
    this.editing = 'new';
    this.form.reset({ name: '', subject: '', body: '' });
  }

  edit(template: EmailTemplate): void {
    this.editing = template.id!;
    this.form.reset({ name: template.name, subject: template.subject, body: template.body });
  }

  cancel(): void {
    this.editing = null;
  }

  /** A variable as a template writes it: {{name}}. */
  placeholder(variable: string): string {
    return `{{${variable}}}`;
  }

  /** Writes the variable at the end of the message. */
  insert(variable: string): void {
    const body = this.form.get('body')!;
    body.setValue(`${body.value ?? ''}${this.placeholder(variable)}`);
  }

  save(): void {
    if (this.form.invalid || this.saving || this.editing === null) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving = true;
    const template: EmailTemplate = this.form.value;
    const request = this.editing === 'new' ? this.templateService.create(template) : this.templateService.update(this.editing, template);
    request.subscribe({
      next: saved => {
        this.saving = false;
        this.editing = null;
        this.show([...this.templates.filter(item => item.id !== saved.id), saved]);
        this.notificationService.success('emails.settings.saved');
      },
      error: (error: HttpErrorResponse) => {
        this.saving = false;
        if (error.status === 409 && String(error.error?.message).includes('already named')) {
          this.form.get('name')!.setErrors({ taken: true });
        } else {
          this.notificationService.error(error.status === 409 ? 'emails.settings.limit' : 'emails.settings.saveError');
        }
        this.changes.markForCheck();
      }
    });
  }

  remove(template: EmailTemplate): void {
    this.confirmDialog.confirm({
      messageKey: 'emails.settings.deleteConfirm', messageParams: { name: template.name }, danger: true
    }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.templateService.delete(template.id!).subscribe({
        next: () => {
          this.show(this.templates.filter(item => item.id !== template.id));
          this.notificationService.success('emails.settings.deleted');
        },
        error: () => this.notificationService.error('emails.settings.deleteError')
      });
    });
  }

  private show(templates: EmailTemplate[]): void {
    this.templates = [...templates].sort((a, b) => a.name.localeCompare(b.name));
    this.changes.markForCheck();
  }
}
