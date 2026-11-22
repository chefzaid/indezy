/** A section of a help article, by the id its heading gets in each language (see `headingId`). */
export interface HelpTopic {
  article: string;
  section: { en: string; fr: string };
}

/** The settings explained by a small help link beside them, each opening its section of the help in the panel. */
export const HELP_TOPICS = {
  billingIdentity: { article: 'invoices-quotes', section: { en: 'complete-your-billing-identity', fr: 'completer-votre-identite-de-facturation' } },
  facturX: { article: 'invoices-quotes', section: { en: 'factur-x', fr: 'factur-x' } },
  emailTemplates: { article: 'documents', section: { en: 'email-templates-and-their-variables', fr: 'modeles-d-e-mails-et-leurs-variables' } },
  offerFilters: { article: 'offers', section: { en: 'filter-and-sort', fr: 'filtrer-et-trier' } }
} satisfies Record<string, HelpTopic>;

export type HelpTopicKey = keyof typeof HELP_TOPICS;
