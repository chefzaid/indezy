import { Component, Input, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { ThemeService } from '../../theme/theme.service';

/** The Indezy logo in the variant that stays legible on the page: dark lettering in the light theme, white in the dark. */
@Component({
  selector: 'app-logo',
  imports: [AsyncPipe],
  template: `<img [src]="source(themeService.isDark$ | async)" alt="Indezy" [height]="height">`,
  styles: `:host { display: inline-flex; } img { display: block; width: auto; }`
})
export class LogoComponent {
  @Input() height = 32;
  protected readonly themeService = inject(ThemeService);

  source(dark: boolean | null): string {
    return dark ? 'assets/brand/indezy-logo-on-dark.svg' : 'assets/brand/indezy-logo.svg';
  }
}
