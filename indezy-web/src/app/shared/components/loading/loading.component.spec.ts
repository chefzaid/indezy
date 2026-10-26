import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LoadingComponent } from './loading.component';

describe('LoadingComponent', () => {
  let fixture: ComponentFixture<LoadingComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [LoadingComponent] });
    fixture = TestBed.createComponent(LoadingComponent);
  });

  function render(inputs: Partial<LoadingComponent> = {}): HTMLElement {
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the logo, a spinner and the default message', () => {
    const element = render();

    expect(element.querySelector('app-logo')).not.toBeNull();
    expect(element.querySelector('mat-spinner')).not.toBeNull();
    expect(element.querySelector('.loading-message')!.textContent).toBe('Chargement...');
    expect(element.querySelector('.loading-subtitle')).toBeNull();
    expect(element.querySelector('.loading-container')!.classList).not.toContain('fullscreen');
  });

  it('shows a custom message and subtitle in fullscreen', () => {
    const element = render({ message: 'Loading clients', subtitle: 'Please wait', fullscreen: true, spinnerSize: 32, color: 'accent' });

    expect(element.querySelector('.loading-message')!.textContent).toBe('Loading clients');
    expect(element.querySelector('.loading-subtitle')!.textContent).toBe('Please wait');
    expect(element.querySelector('.loading-container')!.classList).toContain('fullscreen');
    expect(element.querySelector('mat-spinner')!.classList).toContain('mat-accent');
    expect((element.querySelector('mat-spinner') as HTMLElement).style.width).toBe('32px');
  });

  it('shows only the spinner without logo or message', () => {
    const element = render({ showLogo: false, message: '', subtitle: 'ignored' });

    expect(element.querySelector('app-logo')).toBeNull();
    expect(element.querySelector('.loading-text')).toBeNull();
    expect(element.querySelector('mat-spinner')).not.toBeNull();
  });
});
