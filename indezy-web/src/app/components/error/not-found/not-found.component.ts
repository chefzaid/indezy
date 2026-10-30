import { Component, ChangeDetectionStrategy } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { LogoComponent } from '../../../shared/components/logo/logo.component';

@Component({
    selector: 'app-not-found',
    imports: [MatButtonModule, MatCardModule, MatIconModule, RouterModule, TranslateModule, LogoComponent],
    template: `
    <div class="not-found-container">
      <div class="error-container">
        <mat-card class="error-card">
          <mat-card-content>
            <div class="error-content">
              <div class="error-logo">
                <app-logo class="logo-image" [height]="40"></app-logo>
              </div>
              <div class="error-icon">
                <mat-icon>search_off</mat-icon>
              </div>
              <div class="error-text">
                <h2 class="error-title">{{ 'errors.notFoundTitle' | translate }}</h2>
                <p class="error-message">{{ 'errors.notFoundMessage' | translate }}</p>
              </div>
              <div class="error-actions">
                <button mat-button routerLink="/dashboard">
                  <mat-icon>home</mat-icon>
                  {{ 'common.backToHome' | translate }}
                </button>
              </div>
            </div>
          </mat-card-content>
        </mat-card>
      </div>
    </div>
  `,
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['./not-found.component.scss']
})
export class NotFoundComponent {}
