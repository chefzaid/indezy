import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ThemeService } from '../../theme/theme.service';
import { LogoComponent } from './logo.component';

describe('LogoComponent', () => {
  let theme: ThemeService;
  let fixture: ComponentFixture<LogoComponent>;

  const source = (): string | null => fixture.nativeElement.querySelector('img').getAttribute('src');

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [LogoComponent] });
    theme = TestBed.inject(ThemeService);
    theme.apply('light');
    fixture = TestBed.createComponent(LogoComponent);
  });

  afterEach(() => {
    document.documentElement.classList.remove('dark-theme');
    localStorage.removeItem('indezy-theme');
  });

  it('shows dark lettering on light pages at the requested height', () => {
    fixture.componentRef.setInput('height', 48);
    fixture.detectChanges();
    const img: HTMLImageElement = fixture.nativeElement.querySelector('img');
    expect(source()).toBe('assets/brand/indezy-logo.svg');
    expect(img.getAttribute('height')).toBe('48');
    expect(img.alt).toBe('Indezy');
  });

  it('switches to white lettering when the dark theme is applied', () => {
    fixture.detectChanges();
    theme.apply('dark');
    fixture.detectChanges();
    expect(source()).toBe('assets/brand/indezy-logo-on-dark.svg');
  });

  it('keeps the toolbar variant in both themes', () => {
    fixture.componentRef.setInput('surface', 'primary');
    fixture.detectChanges();
    expect(source()).toBe('assets/brand/indezy-logo-on-primary.svg');
    theme.apply('dark');
    fixture.detectChanges();
    expect(source()).toBe('assets/brand/indezy-logo-on-primary.svg');
  });
});
