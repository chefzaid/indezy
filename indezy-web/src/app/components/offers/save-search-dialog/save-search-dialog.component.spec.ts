import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { SaveSearchDialogComponent } from './save-search-dialog.component';

describe('SaveSearchDialogComponent', () => {
  let close: jasmine.Spy;

  function create(name: string): SaveSearchDialogComponent {
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [SaveSearchDialogComponent, TranslateModule.forRoot()],
      providers: [
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: { name } }
      ]
    });
    const fixture = TestBed.createComponent(SaveSearchDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('offers a name and closes with the one typed, trimmed', () => {
    const dialog = create('kafka');
    expect(dialog.name).toBe('kafka');

    dialog.name = '  Kafka à Paris ';
    dialog.save();

    expect(close).toHaveBeenCalledWith('Kafka à Paris');
  });

  it('keeps a blank name from being saved and closes with nothing when cancelled', () => {
    const dialog = create('');

    dialog.name = '   ';
    dialog.save();
    expect(close).not.toHaveBeenCalled();
    dialog.cancel();
    expect(close).toHaveBeenCalledWith();
  });
});
