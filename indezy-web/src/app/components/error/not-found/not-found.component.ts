import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ErrorDisplayComponent, ErrorDisplayConfig } from '../../../shared/components/error-display/error-display.component';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'app-not-found',
    imports: [
        CommonModule,
        RouterModule,
        ErrorDisplayComponent
    ],
    template: `
    <div class="not-found-container">
      <app-error-display 
        [config]="errorConfig"
        (retry)="goHome()">
      </app-error-display>
    </div>
  `,
    styles: [`
    .not-found-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
    }
  `]
})
export class NotFoundComponent {
  errorConfig: ErrorDisplayConfig;

  constructor(private readonly translate: TranslateService) {
    this.errorConfig = {
      type: 'not-found',
      title: this.translate.instant('errors.notFoundTitle'),
      message: this.translate.instant('errors.notFoundMessage'),
      showRetry: false,
      showHome: true,
      showLogo: true
    };
  }

  goHome(): void {
    // Navigation handled by error-display component
  }
}
