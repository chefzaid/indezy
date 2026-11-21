/** A section of the help center. */
export type HelpSection = 'start' | 'work' | 'money' | 'network';

/** An article of the help center: its address, icon and section; its title and summary are in the translations. */
export interface HelpArticle {
  slug: string;
  icon: string;
  section: HelpSection;
}

export const HELP_SECTIONS: readonly HelpSection[] = ['start', 'work', 'money', 'network'];

/** The articles, in their reading order; each has a Markdown file per language in assets/help/<language>/<slug>.md. */
export const HELP_ARTICLES: readonly HelpArticle[] = [
  { slug: 'getting-started', icon: 'rocket_launch', section: 'start' },
  { slug: 'account', icon: 'manage_accounts', section: 'start' },
  { slug: 'shortcuts', icon: 'keyboard', section: 'start' },
  { slug: 'faq', icon: 'quiz', section: 'start' },
  { slug: 'offers', icon: 'travel_explore', section: 'work' },
  { slug: 'pipeline', icon: 'view_kanban', section: 'work' },
  { slug: 'clients-contacts', icon: 'contacts', section: 'work' },
  { slug: 'calendar', icon: 'calendar_month', section: 'work' },
  { slug: 'activity-reports', icon: 'edit_calendar', section: 'money' },
  { slug: 'invoices-quotes', icon: 'receipt_long', section: 'money' },
  { slug: 'documents', icon: 'folder_shared', section: 'money' },
  { slug: 'dashboard', icon: 'insights', section: 'money' },
  { slug: 'network', icon: 'diversity_3', section: 'network' },
  { slug: 'whats-new', icon: 'new_releases', section: 'start' }
];

export function helpArticle(slug: string | null): HelpArticle | undefined {
  return HELP_ARTICLES.find(article => article.slug === slug);
}
