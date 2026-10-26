import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { ProjectEmailButtonComponent } from './project-email-button.component';
import { ProjectEmailDialogComponent } from './project-email-dialog.component';

describe('ProjectEmailButtonComponent', () => {
  it('opens the composer for the opportunity', () => {
    const dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    TestBed.configureTestingModule({
      imports: [ProjectEmailButtonComponent, TranslateModule.forRoot()],
      providers: [{ provide: MatDialog, useValue: dialog }]
    });
    const fixture = TestBed.createComponent(ProjectEmailButtonComponent);
    const project = { id: 8, role: 'Dev', dailyRate: 600 };
    fixture.componentRef.setInput('project', project);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();

    expect(dialog.open).toHaveBeenCalledWith(ProjectEmailDialogComponent, jasmine.objectContaining({ data: project }));
  });
});
