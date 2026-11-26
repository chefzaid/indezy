import { TranslateService } from '@ngx-translate/core';

import { Invoice } from '../../../models';
import { InvoiceService } from '../../../services/invoice/invoice.service';
import { ClientEmailDialogData } from '../../../shared/components/client-email-dialog/client-email-dialog.component';

/** What a payment reminder says, in the invoice's language; a later one recalls the day of the last. */
export function reminderText(invoice: Invoice, firstName: string | null | undefined, signature: string): { subject: string; body: string } {
  const fees = invoice.lateFees;
  const en = invoice.language === 'EN';
  const locale = en ? 'en-GB' : 'fr-FR';
  const money = (amount: number): string => new Intl.NumberFormat(locale, { style: 'currency', currency: invoice.currency ?? 'EUR' }).format(amount);
  const euros = (amount: number): string => new Intl.NumberFormat(locale, { style: 'currency', currency: 'EUR' }).format(amount);
  const day = (iso: string | null): string => iso ? new Intl.DateTimeFormat(locale, { dateStyle: 'long' }).format(new Date(`${iso}T12:00:00`)) : '';
  const number = invoice.number ?? '';
  const due = money(fees?.amountDue ?? invoice.total);
  const late = fees?.daysLate ?? 0;
  const greeting = en ? `Hello${firstName ? ' ' + firstName : ''},` : `Bonjour${firstName ? ' ' + firstName : ''},`;
  const penalties = fees && fees.rate !== null && fees.interest !== null
    ? (en ? `late payment interest at ${fees.rate}% a year, ${money(fees.interest)} to date, and the fixed ${euros(fees.indemnity)} recovery indemnity`
      : `des pénalités au taux de ${String(fees.rate).replace('.', ',')} % l'an, soit ${money(fees.interest)} à ce jour, et l'indemnité forfaitaire de ${euros(fees.indemnity)} pour frais de recouvrement`)
    : (en ? `the fixed ${euros(fees?.indemnity ?? 40)} recovery indemnity` : `l'indemnité forfaitaire de ${euros(fees?.indemnity ?? 40)} pour frais de recouvrement`);
  const again = invoice.remindedOn ?? null;
  if (en) {
    return {
      subject: again ? `Invoice ${number}: second reminder` : `Invoice ${number}: payment pending`,
      body: `${greeting}\n\n` + (again ? `I am following up on my reminder of ${day(again)}: invoice ${number} of ${day(invoice.issueDate)}, for ${due}, ` +
        `due on ${day(invoice.dueDate)}, is still unpaid (${late} days late).` : `Unless I am mistaken, invoice ${number} of ${day(invoice.issueDate)}, ` +
        `for ${due}, was due on ${day(invoice.dueDate)} and is still unpaid (${late} days late).`) +
        `\n\nCould you tell me when it will be paid?\n\nAs a reminder, late payment brings ${penalties} ` +
        `(French Commercial Code, articles L441-10 and D441-5).\n\nKind regards,\n${signature}`.trimEnd()
    };
  }
  return {
    subject: again ? `Facture ${number} : seconde relance` : `Facture ${number} : paiement en attente`,
    body: `${greeting}\n\n` + (again ? `Je reviens vers vous après ma relance du ${day(again)} : la facture ${number} du ${day(invoice.issueDate)}, ` +
      `d'un montant de ${due}, arrivée à échéance le ${day(invoice.dueDate)}, reste impayée (${late} jours de retard).` :
      `Sauf erreur de ma part, la facture ${number} du ${day(invoice.issueDate)}, d'un montant de ${due}, arrivée à échéance ` +
      `le ${day(invoice.dueDate)}, reste impayée (${late} jours de retard).`) + `\n\nPourriez-vous m'indiquer la date prévue de son règlement ?\n\n` +
      `Pour rappel, tout retard de paiement entraîne ${penalties} (art. L441-10 et D441-5 du Code de commerce).\n\nBien cordialement,\n${signature}`.trimEnd()
  };
}

/**
 * The payment reminder of an overdue invoice (see docs/features.md#invoices), for the client email dialog: written in the
 * invoice's language with the amount left to pay, the days late, the interest at its rate and the €40 indemnity; once
 * sent from Indezy, the invoice notes the day (the dialog closing with the invoice so).
 */
export function paymentReminder(invoice: Invoice, translate: TranslateService, invoices: InvoiceService): ClientEmailDialogData {
  return {
    title: translate.instant('invoices.reminder.title', { number: invoice.number }),
    clientId: invoice.clientId,
    projectId: invoice.projectId,
    language: invoice.language,
    write: (firstName, signature) => reminderText(invoice, firstName, signature),
    sent: () => invoices.reminded(invoice.id)
  };
}
