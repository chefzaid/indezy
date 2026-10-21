import { Component, Input, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { ThemeService } from '../../theme/theme.service';

/** Where the logo sits: an ordinary page surface, or the primary-colored toolbar. */
export type LogoSurface = 'page' | 'primary';

/**
 * The Indezy logo in the variant that stays legible on its background: dark lettering on light
 * pages, white lettering on dark pages, and a white tile on the primary toolbar in both themes.
 */
@Component({
  selector: 'app-logo',
  imports: [AsyncPipe],
  template: `<img [src]="source(themeService.isDark$ | async)" alt="Indezy" [height]="height">`,
  styles: `:host { display: inline-flex; } img { display: block; width: auto; }`
})
export class LogoComponent {
  @Input() height = 32;
  @Input() surface: LogoSurface = 'page';
  protected readonly themeService = inject(ThemeService);

  source(dark: boolean | null): string {
    if (this.surface === 'primary') {
      return 'assets/brand/indezy-logo-on-primary.svg';
    }
    return dark ? 'assets/brand/indezy-logo-on-dark.svg' : 'assets/brand/indezy-logo.svg';
  }
}
