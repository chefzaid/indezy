import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { AuthService } from '../../services/auth/auth.service';
import { KeyboardShortcutsService } from './keyboard-shortcuts.service';
import { ShortcutHelpComponent } from './shortcut-help.component';

describe('workspace keyboard shortcuts', () => {
  let service: KeyboardShortcutsService;
  let router: jasmine.SpyObj<Router>;
  let auth: jasmine.SpyObj<AuthService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let surface: HTMLDivElement;

  beforeEach(() => {
    router = jasmine.createSpyObj('Router', ['navigate']);
    router.navigate.and.resolveTo(true);
    auth = jasmine.createSpyObj('AuthService', ['isAuthenticated']);
    auth.isAuthenticated.and.returnValue(true);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    TestBed.configureTestingModule({ providers: [
      { provide: Router, useValue: router }, { provide: AuthService, useValue: auth },
      { provide: MatDialog, useValue: dialog }
    ] });
    service = TestBed.inject(KeyboardShortcutsService);
    surface = document.createElement('div');
    document.body.append(surface);
  });
  afterEach(() => surface.remove());

  function press(code: string, target: Element = surface, options: KeyboardEventInit = {}): KeyboardEvent {
    const event = new KeyboardEvent('keydown', { code, altKey: true, shiftKey: true, cancelable: true, ...options });
    Object.defineProperty(event, 'target', { value: target });
    service.handle(event);
    return event;
  }

  it('creates a project and focuses search only after navigation finishes', async () => {
    expect(press('KeyN').defaultPrevented).toBeTrue();
    expect(router.navigate).toHaveBeenCalledWith(['/projects/new']);
    const focus = jasmine.createSpy('focus');
    const subscription = service.focusProjectSearch$.subscribe(focus);
    press('KeyF');
    expect(focus).not.toHaveBeenCalled();
    await Promise.resolve();
    expect(router.navigate).toHaveBeenCalledWith(['/projects']);
    expect(focus).toHaveBeenCalledTimes(1);
    subscription.unsubscribe();
  });

  it('opens help from its shortcut and leaves unrelated keys alone', () => {
    expect(press('KeyH').defaultPrevented).toBeTrue();
    expect(dialog.open).toHaveBeenCalledWith(ShortcutHelpComponent, { width: '560px', maxWidth: '95vw' });
    expect(press('KeyX').defaultPrevented).toBeFalse();
  });

  it('keeps a focus request until the destination mounts, then consumes it once', async () => {
    press('KeyF');
    await Promise.resolve();
    const focus = jasmine.createSpy('focus').and.callFake(() => service.acknowledgeSearchFocus());
    const subscription = service.focusProjectSearch$.subscribe(focus);
    expect(focus).toHaveBeenCalledTimes(1);
    subscription.unsubscribe();
    const nextVisit = service.focusProjectSearch$.subscribe(focus);
    expect(focus).toHaveBeenCalledTimes(1);
    nextVisit.unsubscribe();
  });

  it('opens the current project stage menu without choosing a stage', () => {
    const button = document.createElement('button');
    button.setAttribute('data-project-status', '');
    surface.append(button);
    const clicked = jasmine.createSpy('click');
    button.addEventListener('click', clicked);
    press('KeyM');
    expect(document.activeElement).toBe(button);
    expect(clicked).toHaveBeenCalledTimes(1);
    expect(router.navigate).not.toHaveBeenCalled();
    button.disabled = true;
    expect(press('KeyM').defaultPrevented).toBeFalse();
    expect(clicked).toHaveBeenCalledTimes(1);
  });

  it('opens the focused card with its stage menu and ignores unfocused cards', () => {
    surface.setAttribute('data-project-id', '42');
    const child = document.createElement('button');
    surface.append(child);
    press('KeyM', child);
    expect(router.navigate).toHaveBeenCalledWith(['/projects', '42'], { queryParams: { focus: 'status' } });
    router.navigate.calls.reset();
    expect(press('KeyM', document.body).defaultPrevented).toBeFalse();
    surface.setAttribute('data-project-id', 'invalid');
    expect(press('KeyM', child).defaultPrevented).toBeFalse();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('does not interrupt editable fields or their nested content', () => {
    for (const html of ['<input>', '<textarea></textarea>', '<select></select>',
      '<div contenteditable="true"><span>Typing</span></div>', '<div role="combobox"></div>']) {
      surface.innerHTML = html;
      press('KeyN', surface.querySelector('span') ?? surface.firstElementChild!);
    }
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('does not interrupt open menus, selection panels or dialogs', () => {
    const overlay = document.createElement('div');
    overlay.className = 'cdk-overlay-container';
    const panel = document.createElement('div');
    overlay.append(panel);
    document.body.append(overlay);
    for (const role of ['dialog', 'menu', 'listbox']) {
      panel.setAttribute('role', role);
      press('KeyN');
    }
    overlay.remove();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('still works beside a list of options shown in the page, such as filter chips', () => {
    const chips = document.createElement('div');
    chips.setAttribute('role', 'listbox');
    surface.append(chips);

    expect(press('KeyN').defaultPrevented).toBeTrue();
    expect(router.navigate).toHaveBeenCalledWith(['/projects/new']);
  });

  it('ignores signed-out sessions, repeats, composition and other modifier combinations', () => {
    for (const options of [{ repeat: true }, { isComposing: true }, { altKey: false },
      { shiftKey: false }, { ctrlKey: true }, { metaKey: true }]) {
      expect(press('KeyN', surface, options).defaultPrevented).toBeFalse();
    }
    auth.isAuthenticated.and.returnValue(false);
    press('KeyN');
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
