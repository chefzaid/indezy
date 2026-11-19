import { Injectable, OnDestroy, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { NavigationEnd, Router } from '@angular/router';
import { Subscription, filter } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SILENT_HTTP_ERRORS } from '../../interceptors/error.interceptor';

/** How long the posts shown wait before going together. */
export const IMPRESSIONS_FLUSH_MS = 3000;
/** How many go at most in one request, as the server takes. */
export const IMPRESSIONS_BATCH = 50;

/**
 * The posts a page shows the reader, for their authors' analytics: each counted once per page read (a new page, or the
 * same one opened again, counts again), sent together a few seconds later, in the background, without a word on failure.
 */
@Injectable({ providedIn: 'root' })
export class PostImpressionsService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/network/posts/impressions`;
  private readonly seenOnPage = new Set<number>();
  private readonly waiting = new Set<number>();
  private timer: ReturnType<typeof setTimeout> | null = null;
  private readonly navigation: Subscription;

  constructor() {
    this.navigation = inject(Router).events.pipe(filter(event => event instanceof NavigationEnd)).subscribe(() => {
      this.flush();
      this.seenOnPage.clear();
    });
  }

  /** A post came into view. */
  seen(postId: number): void {
    if (this.seenOnPage.has(postId)) {
      return;
    }
    this.seenOnPage.add(postId);
    this.waiting.add(postId);
    if (this.waiting.size >= IMPRESSIONS_BATCH) {
      this.flush();
    } else if (this.timer === null) {
      this.timer = setTimeout(() => this.flush(), IMPRESSIONS_FLUSH_MS);
    }
  }

  /** Sends the posts waiting. */
  flush(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    const ids = [...this.waiting].slice(0, IMPRESSIONS_BATCH);
    ids.forEach(id => this.waiting.delete(id));
    if (ids.length) {
      this.http.post<void>(this.url, { ids }, { context: new HttpContext().set(SILENT_HTTP_ERRORS, true) }).subscribe({ error: () => undefined });
    }
    if (this.waiting.size) {
      this.flush();
    }
  }

  ngOnDestroy(): void {
    this.navigation.unsubscribe();
    this.flush();
  }
}
