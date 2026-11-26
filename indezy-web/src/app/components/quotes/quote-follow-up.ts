import { TranslateService } from '@ngx-translate/core';

import { Quote } from '../../models';
import { ClientEmailDialogData } from '../../shared/components/client-email-dialog/client-email-dialog.component';

/** What a sent quote's follow-up says, in the quote's language: still open until its day, or past it and offered again. */
export function followUpText(quote: Quote, firstName: string | null | undefined, signature: string): { subject: string; body: string } {
  const en = quote.language === 'EN';
  const locale = en ? 'en-GB' : 'fr-FR';
  const money = (amount: number): string => new Intl.NumberFormat(locale, { style: 'currency', currency: 'EUR' }).format(amount);
  const day = (iso: string | null): string => iso ? new Intl.DateTimeFormat(locale, { dateStyle: 'long' }).format(new Date(`${iso}T12:00:00`)) : '';
  const number = quote.number ?? '';
  const greeting = en ? `Hello${firstName ? ' ' + firstName : ''},` : `Bonjour${firstName ? ' ' + firstName : ''},`;
  if (en) {
    const when = quote.expired ? `It expired on ${day(quote.validUntil)}: if the project still stands, I can send you an updated one.`
      : `It holds until ${day(quote.validUntil)}.`;
    return {
      subject: `Quote ${number}: your feedback`,
      body: `${greeting}\n\nI am following up on quote ${number} of ${day(quote.issueDate)}, for ${money(quote.totalExcludingVat)} excluding VAT. ${when}` +
        `\n\nHave you had a chance to look at it? I am happy to discuss it or adjust it if needed.\n\nKind regards,\n${signature}`.trimEnd()
    };
  }
  const when = quote.expired ? `Il est arrivé à échéance le ${day(quote.validUntil)} : si le projet tient toujours, je peux vous en adresser un nouveau.`
    : `Il reste valable jusqu'au ${day(quote.validUntil)}.`;
  return {
    subject: `Devis ${number} : votre retour`,
    body: `${greeting}\n\nJe me permets de revenir vers vous au sujet du devis ${number} du ${day(quote.issueDate)}, d'un montant de ` +
      `${money(quote.totalExcludingVat)} HT. ${when}\n\nAvez-vous pu en prendre connaissance ? Je reste disponible pour en parler ou l'ajuster ` +
      `si besoin.\n\nBien cordialement,\n${signature}`.trimEnd()
  };
}

/** A sent quote's follow-up (see docs/features.md#quotes), for the client email dialog. */
export function quoteFollowUp(quote: Quote, translate: TranslateService): ClientEmailDialogData {
  return {
    title: translate.instant('quotes.followUp.title', { number: quote.number }),
    clientId: quote.clientId,
    projectId: quote.projectId,
    language: quote.language,
    write: (firstName, signature) => followUpText(quote, firstName, signature)
  };
}
