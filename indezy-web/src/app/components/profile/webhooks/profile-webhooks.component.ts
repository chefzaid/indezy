import { ChangeDetectionStrategy, Component, OnInit, ViewChild, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Clipboard } from '@angular/cdk/clipboard';
import { FormBuilder, FormGroupDirective, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

import { CreatedWebhook, WEBHOOK_EVENTS, Webhook, WebhookDelivery, WebhookEvent } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';
import { WebhookService } from '../../../services/webhook/webhook.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';

/**
 * Webhooks: HTTPS addresses of the freelancer's own tools, told of the chosen events with a signature made with a secret
 * shown once; each can be tested, turned off and on, and shows its latest deliveries.
 */
@Component({
  selector: 'app-profile-webhooks',
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatCardModule, MatCheckboxModule, MatFormFieldModule, MatIconModule,
    MatInputModule, TranslateModule],
  templateUrl: './profile-webhooks.component.html',
  styleUrls: ['./profile-webhooks.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProfileWebhooksComponent implements OnInit {
  static readonly MAX_WEBHOOKS = 5;
  readonly events = WEBHOOK_EVENTS;

  private readonly webhooks = inject(WebhookService);
  private readonly clipboard = inject(Clipboard);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);

  readonly form = inject(FormBuilder).nonNullable.group({
    url: ['', [Validators.required, Validators.maxLength(500), Validators.pattern(/^\s*https:\/\/\S+\s*$/i)]]
  });
  /** Reset through its directive, which also forgets the submission: an emptied address shows no error. */
  @ViewChild(FormGroupDirective) private readonly formDirective?: FormGroupDirective;
  readonly chosen = new Set<WebhookEvent>(['OPPORTUNITY_STATUS_CHANGED', 'INVOICE_PAID']);
  list: Webhook[] = [];
  created?: CreatedWebhook;
  deliveries = new Map<number, WebhookDelivery[]>();
  noEvent = false;

  get full(): boolean {
    return this.list.length >= ProfileWebhooksComponent.MAX_WEBHOOKS;
  }

  ngOnInit(): void {
    this.webhooks.list().subscribe({
      next: list => this.list = list,
      error: () => this.notifications.error('profile.webhooks.loadError')
    });
  }

  toggle(event: WebhookEvent, checked: boolean): void {
    if (checked) {
      this.chosen.add(event);
    } else {
      this.chosen.delete(event);
    }
    this.noEvent = this.chosen.size === 0;
  }

  create(): void {
    this.noEvent = this.chosen.size === 0;
    if (this.form.invalid || this.noEvent) {
      this.form.markAllAsTouched();
      return;
    }
    this.webhooks.create({ url: this.form.controls.url.value.trim(), events: [...this.chosen] }).subscribe({
      next: created => {
        this.created = created;
        this.list = [...this.list, created.webhook];
        this.formDirective?.resetForm({ url: '' });
      },
      error: (error: HttpErrorResponse) => this.notifications.error(error.status === 400 ? 'profile.webhooks.refused' :
        'profile.webhooks.saveError')
    });
  }

  copy(): void {
    if (this.created && this.clipboard.copy(this.created.secret)) {
      this.notifications.success('profile.webhooks.copied');
    }
  }

  /** Forgets the secret shown once: it cannot be shown again. */
  done(): void {
    this.created = undefined;
  }

  setActive(webhook: Webhook, active: boolean): void {
    this.webhooks.update(webhook.id, { url: webhook.url, events: webhook.events, active }).subscribe({
      next: saved => this.list = this.list.map(item => item.id === saved.id ? saved : item),
      error: () => this.notifications.error('profile.webhooks.saveError')
    });
  }

  ping(webhook: Webhook): void {
    this.webhooks.ping(webhook.id).subscribe({
      next: () => this.notifications.success('profile.webhooks.pinged'),
      error: () => this.notifications.error('profile.webhooks.saveError')
    });
  }

  /** Shows the latest deliveries, or hides them. */
  showDeliveries(webhook: Webhook): void {
    if (this.deliveries.has(webhook.id)) {
      this.deliveries.delete(webhook.id);
      return;
    }
    this.webhooks.deliveries(webhook.id).subscribe({
      next: deliveries => this.deliveries.set(webhook.id, deliveries),
      error: () => this.notifications.error('profile.webhooks.loadError')
    });
  }

  remove(webhook: Webhook): void {
    this.confirm.confirm({ titleKey: 'profile.webhooks.deleteTitle', messageKey: 'profile.webhooks.deleteMessage',
      messageParams: { url: webhook.url }, confirmKey: 'common.delete', danger: true }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.webhooks.delete(webhook.id).subscribe({
        next: () => {
          this.list = this.list.filter(item => item.id !== webhook.id);
          this.deliveries.delete(webhook.id);
          this.notifications.success('profile.webhooks.deleted');
        },
        error: () => this.notifications.error('profile.webhooks.saveError')
      });
    });
  }
}
