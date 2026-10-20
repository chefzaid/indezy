import { TestBed } from '@angular/core/testing';
import { MatSnackBar, MatSnackBarRef, TextOnlySnackBar } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let snackBar: jasmine.SpyObj<MatSnackBar>;
  let service: NotificationService;
  const action = new Subject<void>();

  beforeEach(() => {
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);
    snackBar.open.and.returnValue({ onAction: () => action.asObservable() } as MatSnackBarRef<TextOnlySnackBar>);
    TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()], providers: [{ provide: MatSnackBar, useValue: snackBar }] });
    TestBed.inject(TranslateService).setTranslation('en', { saved: 'Saved {{count}}', undo: 'Undo', close: 'Close', common: { close: 'Close' } });
    TestBed.inject(TranslateService).use('en');
    service = TestBed.inject(NotificationService);
  });

  it('translates the message and styles it by kind', () => {
    service.success('saved', 4000, { count: 2 });
    service.error('saved');
    service.info('saved');
    service.errorText('Server text');

    expect(snackBar.open.calls.argsFor(0)).toEqual(['Saved 2', 'Close', { duration: 4000, panelClass: ['success-snackbar'] }]);
    expect(snackBar.open.calls.argsFor(1)[2]).toEqual({ duration: 3000, panelClass: ['error-snackbar'] });
    expect(snackBar.open.calls.argsFor(2)[2]).toEqual({ duration: 3000, panelClass: ['info-snackbar'] });
    expect(snackBar.open.calls.argsFor(3)[0]).toBe('Server text');
  });

  it('offers an action and tells when it is chosen', () => {
    let chosen = 0;
    service.successWithAction('saved', 'undo', 8000, { count: 3 }).subscribe(() => chosen++);

    expect(snackBar.open).toHaveBeenCalledWith('Saved 3', 'Undo', { duration: 8000, panelClass: ['success-snackbar'] });
    action.next();
    expect(chosen).toBe(1);
  });
});
