import { renderMarkdown } from '../../shared/components/markdown/markdown';
import { HELP_ARTICLES } from './help-articles';
import { HELP_TOPICS } from './help-topics';

describe('HELP_TOPICS', () => {
  it('points every help link at a section its article has, in both languages', async () => {
    for (const [key, topic] of Object.entries(HELP_TOPICS)) {
      expect(HELP_ARTICLES.some(article => article.slug === topic.article)).withContext(key).toBeTrue();
      for (const language of ['en', 'fr'] as const) {
        const source = await (await fetch(`/assets/help/${language}/${topic.article}.md`)).text();
        const ids = renderMarkdown(source).headings.map(heading => heading.id);
        expect(ids).withContext(`${key} (${language})`).toContain(topic.section[language]);
      }
    }
  });
});
