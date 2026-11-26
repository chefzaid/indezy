import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';

import { Invoice } from '../../../models';
import { InvoiceService } from '../../../services/invoice/invoice.service';
import { paymentReminder, reminderText } from './payment-reminder';

describe('paymentReminder', () => {
  const invoice = {
    id: 9, kind: 'INVOICE', number: 'F2026-0007', status: 'ISSUED', overdue: true, clientId: 3, clientName: 'Acme', projectId: 42,
    issueDate: '2026-10-01', dueDate: '2026-10-31', language: 'FR', total: 7800, currency: 'EUR',
    lateFees: { daysLate: 25, amountDue: 7800, rate: 10.5, interest: 56.1, indemnity: 40 }
  } as unknown as Invoice;

  it('writes the reminder in the invoice\'s language, with the interest at its rate and the indemnity', () => {
    // Numbers and dates spaced as the locale spaces them (narrow no-break spaces), read here as plain ones.
    const plain = (text: { subject: string; body: string }) => ({ subject: text.subject, body: text.body.replace(/[\u202f\u00a0]/g, ' ') });
    const fr = plain(reminderText(invoice, 'Léa', 'Ada Lovelace'));
    expect(fr.subject).toBe('Facture F2026-0007 : paiement en attente');
    expect(fr.body).toContain('Bonjour Léa,');
    expect(fr.body).toContain('la facture F2026-0007 du 1 octobre 2026');
    expect(fr.body).toContain('7 800,00');
    expect(fr.body).toContain('31 octobre 2026');
    expect(fr.body).toContain('25 jours de retard');
    expect(fr.body).toContain("au taux de 10,5 % l'an, soit 56,10");
    expect(fr.body).toContain('40,00');
    expect(fr.body).toContain('art. L441-10 et D441-5 du Code de commerce');
    expect(fr.body.endsWith('Ada Lovelace')).toBeTrue();

    // In English, in dollars, and with no rate stated: the indemnity only.
    const en = plain(reminderText({ ...invoice, language: 'EN', currency: 'USD', lateFees: { ...invoice.lateFees!, rate: null, interest: null } } as Invoice,
      null, ''));
    expect(en.subject).toBe('Invoice F2026-0007: payment pending');
    expect(en.body).toContain('Hello,');
    expect(en.body).toContain('US$7,800.00');
    expect(en.body).toContain('the fixed €40.00 recovery indemnity');
    expect(en.body).not.toContain('interest');
    expect(en.body.endsWith('Kind regards,')).toBeTrue();

    // Reminded before: the next one says so, with the day.
    const second = plain(reminderText({ ...invoice, remindedOn: '2026-11-10' } as Invoice, 'Léa', 'Ada'));
    expect(second.subject).toBe('Facture F2026-0007 : seconde relance');
    expect(second.body).toContain('après ma relance du 10 novembre 2026');
    const secondEn = plain(reminderText({ ...invoice, language: 'EN', remindedOn: '2026-11-10' } as Invoice, null, 'Ada'));
    expect(secondEn.subject).toBe('Invoice F2026-0007: second reminder');
    expect(secondEn.body).toContain('my reminder of 10 November 2026');
  });

  it('writes to the invoice\'s client, in its language, and records the day once sent', () => {
    const translate = jasmine.createSpyObj<TranslateService>('TranslateService', ['instant']);
    translate.instant.and.returnValue('Relance de paiement de F2026-0007');
    const invoices = jasmine.createSpyObj<InvoiceService>('InvoiceService', ['reminded']);
    invoices.reminded.and.returnValue(of(invoice));
    const data = paymentReminder(invoice, translate, invoices);
    expect(translate.instant).toHaveBeenCalledWith('invoices.reminder.title', { number: 'F2026-0007' });
    expect(data).toEqual(jasmine.objectContaining({ title: 'Relance de paiement de F2026-0007', clientId: 3, projectId: 42, language: 'FR' }));
    expect(data.write('Léa', 'Ada').subject).toBe('Facture F2026-0007 : paiement en attente');
    expect(data.prefer!.test('compta@acme.fr')).toBeTrue();
    expect(data.prefer!.test('Service facturation')).toBeTrue();
    expect(data.prefer!.test('lea.martin@acme.fr')).toBeFalse();
    data.sent!().subscribe();
    expect(invoices.reminded).toHaveBeenCalledWith(9);
  });
});
