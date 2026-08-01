import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ContactImportDialogComponent } from './contact-import-dialog.component';

describe('ContactImportDialogComponent', () => {
  let fixture: ComponentFixture<ContactImportDialogComponent>;
  let component: ContactImportDialogComponent;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<ContactImportDialogComponent, string>>;

  beforeEach(async () => {
    dialogRefSpy = jasmine.createSpyObj<MatDialogRef<ContactImportDialogComponent, string>>('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [ContactImportDialogComponent, TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [{ provide: MatDialogRef, useValue: dialogRefSpy }]
    }).compileComponents();

    fixture = TestBed.createComponent(ContactImportDialogComponent);
    component = fixture.componentInstance;
  });

  it('closes with the pasted content on import', () => {
    component.content = 'First Name,Last Name\nJane,Doe';
    component.onImport();
    expect(dialogRefSpy.close).toHaveBeenCalledWith('First Name,Last Name\nJane,Doe');
  });

  it('does not close when the content is blank', () => {
    component.content = '   ';
    component.onImport();
    expect(dialogRefSpy.close).not.toHaveBeenCalled();
  });

  it('closes with no result on cancel', () => {
    component.onCancel();
    expect(dialogRefSpy.close).toHaveBeenCalledWith();
  });
});
