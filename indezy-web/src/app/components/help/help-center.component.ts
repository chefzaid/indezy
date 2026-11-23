import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { useAddressLanguage } from './help-language';
import { debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';

import { AuthService } from '../../services/auth/auth.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { PublicPageHeaderComponent } from '../../shared/components/public-page-header/public-page-header.component';
import { HELP_ARTICLES, HELP_SECTIONS, HelpArticle, HelpSection } from './help-articles';
import { HelpHit, HelpSearchService } from './help-search.service';

/**
 * The help center (`/help`, open without an account): how Indezy works, area by area, each article with screenshots; a
 * search over their titles, summaries and text; and the legal pages.
 */
@Component({
  selector: 'app-help-center',
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatIconModule, MatInputModule, TranslateModule, PageHeaderComponent,
    PublicPageHeaderComponent],
  templateUrl: './help-center.component.html',
  styleUrls: ['./help-center.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class HelpCenterComponent implements OnInit {
  private readonly searcher = inject(HelpSearchService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly translate = inject(TranslateService);

  readonly sections = HELP_SECTIONS;
  readonly query = new FormControl('', { nonNullable: true });
  /** The articles found while a search is typed (two characters or more); none otherwise. */
  hits: HelpHit[] | null = null;

  articles(section: HelpSection): HelpArticle[] {
    return HELP_ARTICLES.filter(article => article.section === section);
  }

  ngOnInit(): void {
    useAddressLanguage(this.route, this.auth, this.translate);
    this.query.valueChanges.pipe(debounceTime(200), map(typed => typed.trim()), distinctUntilChanged(),
      switchMap(typed => typed.length < 2 ? of(null) : this.searcher.search(typed)), takeUntilDestroyed(this.destroyRef))
      .subscribe(hits => {
        this.hits = hits;
        this.changes.markForCheck();
      });
  }
}
