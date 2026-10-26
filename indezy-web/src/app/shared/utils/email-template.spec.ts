import { fillTemplate, mailtoLink } from './email-template';

describe('email templates', () => {
  it('fills the variables it knows and leaves the others as written', () => {
    expect(fillTemplate('Bonjour {{firstName}} {{ lastName }}, {{role}} chez {{company}} à {{rate}}', {
      firstName: 'Léa', lastName: 'Martin', role: 'Dev Java', company: 'Alten', rate: '650 €'
    })).toBe('Bonjour Léa Martin, Dev Java chez Alten à 650 €');
    expect(fillTemplate('Hello {{firstName}}, {{unknown}} {{availability}}', { firstName: ' ', availability: null }))
      .toBe('Hello {{firstName}}, {{unknown}} {{availability}}');
  });

  it('opens the mail app with an encoded subject and body, and no recipient that is not an address', () => {
    expect(mailtoLink('lea@alten.fr', 'Suivi & relance', 'Bonjour,\nÀ bientôt'))
      .toBe('mailto:lea@alten.fr?subject=Suivi%20%26%20relance&body=Bonjour%2C%0A%C3%80%20bient%C3%B4t');
    expect(mailtoLink('x@y.fr?cc=evil@z.fr', 'S', 'B')).toBe('mailto:?subject=S&body=B');
    expect(mailtoLink(undefined, 'S', 'B')).toBe('mailto:?subject=S&body=B');
  });
});
