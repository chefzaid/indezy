import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { RenameTagDialogComponent } from './rename-tag-dialog.component';

describe('RenameTagDialogComponent', () => {
  let dialogRef: jasmine.SpyObj<MatDialogRef<RenameTagDialogComponent>>;

  function create(tags: string[] = ['Java', 'Spring', 'Kafka']): ComponentFixture<RenameTagDialogComponent> {
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    TestBed.configureTestingModule({
      imports: [RenameTagDialogComponent, TranslateModule.forRoot()],
      providers: [
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { tags } }
      ]
    });
    const fixture = TestBed.createComponent(RenameTagDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  function buttons(fixture: ComponentFixture<RenameTagDialogComponent>): { cancel: HTMLButtonElement; rename: HTMLButtonElement } {
    fixture.detectChanges();
    const [cancel, rename] = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('mat-dialog-actions button'));
    return { cancel, rename };
  }

  async function typeNewName(fixture: ComponentFixture<RenameTagDialogComponent>, value: string): Promise<void> {
    await fixture.whenStable();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('proposes to rename the first tag of the opportunity', async () => {
    const fixture = create();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.from).toBe('Java');
    expect(fixture.nativeElement.querySelector('mat-select').textContent).toContain('Java');
    expect(fixture.nativeElement.textContent).toContain('projects.skills.renameTitle');
    expect(buttons(fixture).rename.disabled).toBeTrue();
  });

  it('renames the chosen tag to the trimmed new name', async () => {
    const fixture = create();
    fixture.componentInstance.from = 'Kafka';
    await typeNewName(fixture, '  Apache Kafka  ');

    const { rename } = buttons(fixture);
    expect(rename.disabled).toBeFalse();
    rename.click();

    expect(dialogRef.close).toHaveBeenCalledWith({ from: 'Kafka', to: 'Apache Kafka' });
  });

  it('renames nothing to a blank name', async () => {
    const fixture = create();
    await typeNewName(fixture, '   ');

    expect(buttons(fixture).rename.disabled).toBeTrue();
    fixture.componentInstance.onConfirm();

    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('renames nothing without a tag to rename', () => {
    const fixture = create([]);
    fixture.componentInstance.to = 'Kotlin';

    expect(fixture.componentInstance.from).toBe('');
    fixture.componentInstance.onConfirm();

    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('closes without a result when cancelled', () => {
    const fixture = create();

    buttons(fixture).cancel.click();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
