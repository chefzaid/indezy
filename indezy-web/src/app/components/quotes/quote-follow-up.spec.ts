import { TranslateService } from '@ngx-translate/core';

import { Quote } from '../../models';
import { followUpText, quoteFollowUp } from './quote-follow-up';

describe('quoteFollowUp', () => {
  const quote = { id: 4, number: 'D2026-0002', status: 'SENT', expired: false, clientId: 3, clientName: 'Acme', projectId: 42,
    issueDate: '2026-11-02', validUntil: '2026-12-02', language: 'FR', totalExcludingVat: 12500 } as unknown as Quote;
  const plain = (text: { subject: string; body: string }) => ({ subject: text.subject, body: text.body.replace(/[  ]/g, ' ') });

  it('follows up a sent quote in its language, still open or past its day', () => {
    const fr = plain(followUpText(quote, 'Léa', 'Ada Lovelace'));
    expect(fr.subject).toBe('Devis D2026-0002 : votre retour');
    expect(fr.body).toContain('Bonjour Léa,');
    expect(fr.body).toContain('du devis D2026-0002 du 2 novembre 2026');
    expect(fr.body).toContain('12 500,00 € HT');
    expect(fr.body).toContain("valable jusqu'au 2 décembre 2026");
    expect(fr.body.endsWith('Ada Lovelace')).toBeTrue();

    const expired = plain(followUpText({ ...quote, expired: true, language: 'EN' } as Quote, null, ''));
    expect(expired.subject).toBe('Quote D2026-0002: your feedback');
    expect(expired.body).toContain('Hello,');
    expect(expired.body).toContain('€12,500.00 excluding VAT');
    expect(expired.body).toContain('It expired on 2 December 2026');
    expect(expired.body.endsWith('Kind regards,')).toBeTrue();
  });

  it('writes to the quote\'s client in its language, nothing recorded once sent', () => {
    const translate = jasmine.createSpyObj<TranslateService>('TranslateService', ['instant']);
    translate.instant.and.returnValue('Relance du devis D2026-0002');
    const data = quoteFollowUp(quote, translate);
    expect(translate.instant).toHaveBeenCalledWith('quotes.followUp.title', { number: 'D2026-0002' });
    expect(data).toEqual(jasmine.objectContaining({ clientId: 3, projectId: 42, language: 'FR' }));
    expect(data.sent).toBeUndefined();
    expect(data.write('Léa', 'Ada').subject).toBe('Devis D2026-0002 : votre retour');
  });
});
