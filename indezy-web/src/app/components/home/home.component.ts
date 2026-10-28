import { ChangeDetectionStrategy, Component, DOCUMENT, OnDestroy, OnInit, inject } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { environment } from '../../../environments/environment';
import { AuthService } from '../../services/auth/auth.service';
import { AppLanguage, LANGUAGE_STORAGE_KEY } from '../../shared/locale/app-locale';
import { LogoComponent } from '../../shared/components/logo/logo.component';

/** A feature block: its icon and the key of its title, text and points. */
interface Feature {
  key: string;
  icon: string;
}

/** The address of the page in each language: French at the root, English under /en. */
export const HOME_PATHS: Record<AppLanguage, string> = { fr: '/', en: '/en' };

/**
 * The presentation page every visitor lands on (see docs/features.md#presentation-page): what Indezy does for French IT
 * freelancers, in French at `/` and English at `/en`, with **Sign in** or **Open the dashboard**. It never sends the
 * visitor to sign in by itself, and gives search engines and link previews its title, description, languages and card.
 */
@Component({
  selector: 'app-home',
  imports: [RouterLink, MatButtonModule, MatExpansionModule, MatIconModule, TranslateModule, LogoComponent],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class HomeComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly document = inject(DOCUMENT);
  readonly auth = inject(AuthService);

  language: AppLanguage = 'fr';
  readonly year = new Date().getFullYear();
  readonly features: Feature[] = [
    { key: 'feed', icon: 'travel_explore' },
    { key: 'pipeline', icon: 'view_kanban' },
    { key: 'network', icon: 'groups' },
    { key: 'reports', icon: 'event_note' },
    { key: 'invoices', icon: 'receipt_long' },
    { key: 'dashboard', icon: 'insights' }
  ];
  /** The screenshots, taken from a demo workspace (scripts/screenshots), in the page's language and the visitor's theme. */
  readonly screens = ['kanban', 'offers', 'report', 'dashboard'];
  readonly steps = ['profile', 'network', 'opportunities', 'pipeline', 'reports'];
  readonly trust = ['yours', 'export', 'links'];
  readonly questions = ['who', 'sites', 'data', 'languages'];
  /** The Kanban scene of the hero: five columns, a card or two each. */
  readonly board = [
    { column: 'CONTACT', cards: [{ role: 'Lead Java', client: 'ESN · Lyon', rate: 650 }, { role: 'DevOps AWS', client: 'Remote', rate: 600 }] },
    { column: 'INTERVIEW', cards: [{ role: 'Architecte cloud', client: 'Banque · Paris', rate: 750 }] },
    { column: 'OFFER', cards: [{ role: 'Tech lead Angular', client: 'Retail · Lille', rate: 700 }] },
    { column: 'LOST', cards: [{ role: 'Dev Python', client: 'Startup', rate: 500 }] },
    { column: 'WON', cards: [{ role: 'Data engineer', client: 'Énergie · Nantes', rate: 680 }] }
  ];
  /** Sign-up opens to the public before launch (TODO.md, Needs Input): until then it is offered in development only. */
  readonly signUpOpen = !environment.production;

  private readonly added: Element[] = [];
  private previousTitle = '';

  ngOnInit(): void {
    this.language = this.route.snapshot.data['language'] === 'en' ? 'en' : 'fr';
    const saved = this.savedLanguage();
    // At the root, a visitor who never chose follows the browser's language.
    if (this.language === 'fr' && !saved && globalThis.navigator?.language?.toLowerCase().startsWith('en')) {
      this.router.navigate([HOME_PATHS.en], { replaceUrl: true, queryParamsHandling: 'preserve' });
      return;
    }
    this.document.documentElement.lang = this.language;
    this.translate.use(this.language).subscribe(() => this.describe());
  }

  ngOnDestroy(): void {
    this.added.forEach(element => element.remove());
    if (this.previousTitle) {
      this.title.setTitle(this.previousTitle);
    }
    for (const selector of ['name="description"', 'property="og:title"', 'property="og:description"', 'property="og:url"',
      'property="og:image"', 'property="og:type"', 'property="og:locale"', 'name="twitter:card"']) {
      this.meta.removeTag(selector);
    }
  }

  /** Keeps the language chosen and opens the page in it. */
  switchLanguage(language: AppLanguage): void {
    try {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
    } catch {
      // Private browsing: the address keeps the language.
    }
    if (language !== this.language) {
      this.router.navigate([HOME_PATHS[language]]);
    }
  }

  signIn(): void {
    if (environment.production) {
      this.auth.startSsoLogin(`${globalThis.location.origin}/dashboard`);
    } else {
      this.router.navigate(['/login']);
    }
  }

  /** The page's title, description, languages and card, for search engines and link previews. */
  private describe(): void {
    const origin = globalThis.location.origin;
    const url = origin + HOME_PATHS[this.language];
    const title = this.translate.instant('home.meta.title');
    const description = this.translate.instant('home.meta.description');
    const image = `${origin}/assets/brand/og-image.png`;
    // The static page's own tags (scripts/prerender-home.mjs) give way to these.
    this.document.head.querySelectorAll('[data-prerendered]').forEach(element => element.remove());
    this.previousTitle = this.title.getTitle();
    this.title.setTitle(title);
    this.meta.updateTag({ name: 'description', content: description });
    this.meta.updateTag({ property: 'og:type', content: 'website' });
    this.meta.updateTag({ property: 'og:title', content: title });
    this.meta.updateTag({ property: 'og:description', content: description });
    this.meta.updateTag({ property: 'og:url', content: url });
    this.meta.updateTag({ property: 'og:image', content: image });
    this.meta.updateTag({ property: 'og:locale', content: this.language === 'en' ? 'en_GB' : 'fr_FR' });
    this.meta.updateTag({ name: 'twitter:card', content: 'summary_large_image' });
    this.link('canonical', url);
    this.link('alternate', origin + HOME_PATHS.fr, 'fr');
    this.link('alternate', origin + HOME_PATHS.en, 'en');
    this.link('alternate', origin + HOME_PATHS.fr, 'x-default');
    const data = this.document.createElement('script');
    data.type = 'application/ld+json';
    data.text = JSON.stringify({
      '@context': 'https://schema.org', '@type': 'SoftwareApplication', name: 'Indezy', url, description,
      applicationCategory: 'BusinessApplication', operatingSystem: 'Web', inLanguage: ['fr', 'en'], image
    });
    this.document.head.appendChild(data);
    this.added.push(data);
  }

  private link(rel: string, href: string, hreflang?: string): void {
    const link = this.document.createElement('link');
    link.rel = rel;
    link.href = href;
    if (hreflang) {
      link.hreflang = hreflang;
    }
    this.document.head.appendChild(link);
    this.added.push(link);
  }

  private savedLanguage(): string | null {
    try {
      return localStorage.getItem(LANGUAGE_STORAGE_KEY);
    } catch {
      return null;
    }
  }
}
