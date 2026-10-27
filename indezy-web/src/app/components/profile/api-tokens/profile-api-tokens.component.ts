import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Clipboard } from '@angular/cdk/clipboard';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';

import { API_TOKEN_SCOPES, ApiToken, ApiTokenScope, CreatedApiToken } from '../../../models';
import { environment } from '../../../../environments/environment';
import { AccountSecurityService } from '../../../services/account-security/account-security.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';

/** Read-only tokens for another tool, each shown once when created and revoked on request. */
@Component({
  selector: 'app-profile-api-tokens',
  imports: [CommonModule, ReactiveFormsModule, MatButtonModule, MatCardModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatSelectModule, TranslateModule],
  templateUrl: './profile-api-tokens.component.html',
  styleUrls: ['./profile-api-tokens.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProfileApiTokensComponent implements OnInit, OnDestroy {
  /** The most an account holds, as the server allows. */
  static readonly MAX_TOKENS = 10;
  /** Days a token lasts; 0 for one that does not expire (a null option would clear the select). */
  readonly lifetimes = [30, 90, 365, 0];
  readonly apiUrl = new URL(environment.apiUrl, window.location.origin).href.replace(/\/$/, '');

  private readonly security = inject(AccountSecurityService);
  private readonly clipboard = inject(Clipboard);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly destroy$ = new Subject<void>();

  readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(60)]],
    expiresInDays: [90]
  });
  readonly scopes = API_TOKEN_SCOPES;
  /** The whole workspace, or only the parts chosen. */
  everything = true;
  readonly chosen = new Set<ApiTokenScope>();
  noScope = false;
  tokens: ApiToken[] = [];
  created?: CreatedApiToken;
  creating = false;

  get full(): boolean {
    return this.tokens.length >= ProfileApiTokensComponent.MAX_TOKENS;
  }

  ngOnInit(): void {
    this.security.apiTokens().pipe(takeUntil(this.destroy$)).subscribe({
      next: tokens => this.tokens = tokens,
      error: () => this.notifications.error('profile.apiTokens.loadError')
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  setEverything(everything: boolean): void {
    this.everything = everything;
    this.noScope = false;
  }

  toggleScope(scope: ApiTokenScope, checked: boolean): void {
    if (checked) {
      this.chosen.add(scope);
    } else {
      this.chosen.delete(scope);
    }
    this.noScope = !this.everything && this.chosen.size === 0;
  }

  create(): void {
    const name = this.form.controls.name.value.trim();
    this.noScope = !this.everything && this.chosen.size === 0;
    if (!name || this.form.invalid || this.creating || this.noScope) {
      this.form.markAllAsTouched();
      return;
    }
    this.creating = true;
    const scopes = this.everything ? [] : API_TOKEN_SCOPES.filter(scope => this.chosen.has(scope));
    this.security.createApiToken(name, this.form.controls.expiresInDays.value || null, scopes).pipe(takeUntil(this.destroy$)).subscribe({
      next: created => {
        this.created = created;
        this.tokens = [created.token, ...this.tokens];
        this.form.reset({ name: '', expiresInDays: 90 });
        this.everything = true;
        this.chosen.clear();
        this.creating = false;
      },
      error: () => {
        this.creating = false;
        this.notifications.error('profile.apiTokens.createError');
      }
    });
  }

  copy(): void {
    if (this.created && this.clipboard.copy(this.created.secret)) {
      this.notifications.success('profile.apiTokens.copied');
    }
  }

  /** Forgets the token shown once: it cannot be shown again. */
  done(): void {
    this.created = undefined;
  }

  revoke(token: ApiToken): void {
    this.confirm.confirm({
      titleKey: 'profile.apiTokens.revokeTitle',
      messageKey: 'profile.apiTokens.revokeMessage',
      messageParams: { name: token.name },
      confirmKey: 'profile.apiTokens.revoke',
      danger: true
    }).pipe(takeUntil(this.destroy$)).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.security.revokeApiToken(token.id).pipe(takeUntil(this.destroy$)).subscribe({
        next: () => {
          this.tokens = this.tokens.filter(item => item.id !== token.id);
          if (this.created?.token.id === token.id) {
            this.created = undefined;
          }
          this.notifications.success('profile.apiTokens.revoked');
        },
        error: () => this.notifications.error('profile.apiTokens.revokeError')
      });
    });
  }
}
