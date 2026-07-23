import { TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { ConfirmDialogService } from './confirm-dialog.service';
import { ConfirmDialogComponent } from '../components/confirm-dialog/confirm-dialog.component';

describe('ConfirmDialogService', () => {
  let service: ConfirmDialogService;
  let dialogSpy: jasmine.SpyObj<MatDialog>;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<ConfirmDialogComponent, boolean>>;

  beforeEach(() => {
    dialogSpy = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    dialogRefSpy = jasmine.createSpyObj<MatDialogRef<ConfirmDialogComponent, boolean>>('MatDialogRef', ['afterClosed']);
    dialogSpy.open.and.returnValue(dialogRefSpy as unknown as MatDialogRef<ConfirmDialogComponent>);

    TestBed.configureTestingModule({
      providers: [
        ConfirmDialogService,
        { provide: MatDialog, useValue: dialogSpy }
      ]
    });
    service = TestBed.inject(ConfirmDialogService);
  });

  it('should open the confirm dialog with the provided data', () => {
    dialogRefSpy.afterClosed.and.returnValue(of(true));

    service.confirm({ messageKey: 'clients.confirmDelete' }).subscribe();

    expect(dialogSpy.open).toHaveBeenCalledWith(
      ConfirmDialogComponent,
      jasmine.objectContaining({ data: { messageKey: 'clients.confirmDelete' } })
    );
  });

  it('should emit true when the dialog is confirmed', (done) => {
    dialogRefSpy.afterClosed.and.returnValue(of(true));

    service.confirm({ messageKey: 'x' }).subscribe(result => {
      expect(result).toBeTrue();
      done();
    });
  });

  it('should emit false when the dialog is dismissed (undefined result)', (done) => {
    dialogRefSpy.afterClosed.and.returnValue(of(undefined));

    service.confirm({ messageKey: 'x' }).subscribe(result => {
      expect(result).toBeFalse();
      done();
    });
  });
});
