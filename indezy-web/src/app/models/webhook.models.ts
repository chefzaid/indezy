/** What a webhook can be told of; PING is the test sent on demand. */
export type WebhookEvent = 'OPPORTUNITY_CREATED' | 'OPPORTUNITY_STATUS_CHANGED' | 'ACTIVITY_REPORT_SUBMITTED' | 'ACTIVITY_REPORT_APPROVED' |
  'ACTIVITY_REPORT_REJECTED' | 'INVOICE_ISSUED' | 'INVOICE_PAID' | 'PING';

export const WEBHOOK_EVENTS: WebhookEvent[] = ['OPPORTUNITY_CREATED', 'OPPORTUNITY_STATUS_CHANGED', 'ACTIVITY_REPORT_SUBMITTED',
  'ACTIVITY_REPORT_APPROVED', 'ACTIVITY_REPORT_REJECTED', 'INVOICE_ISSUED', 'INVOICE_PAID'];

export interface Webhook {
  id: number;
  url: string;
  events: WebhookEvent[];
  active: boolean;
  consecutiveFailures: number;
  lastDeliveryAt: string | null;
  lastStatus: number | null;
  createdAt: string;
}

export interface WebhookWrite {
  url: string;
  events: WebhookEvent[];
  active?: boolean;
}

/** A new webhook and the secret of its signatures, given this once. */
export interface CreatedWebhook {
  webhook: Webhook;
  secret: string;
}

export interface WebhookDelivery {
  id: number;
  event: WebhookEvent;
  status: 'PENDING' | 'DELIVERED' | 'FAILED';
  attempts: number;
  nextAttemptAt: string;
  lastAttemptAt: string | null;
  responseStatus: number | null;
  error: string | null;
  createdAt: string;
}
