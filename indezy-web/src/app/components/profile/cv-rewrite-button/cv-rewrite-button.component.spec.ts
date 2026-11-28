import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { AiCvRewriteDialogComponent } from '../../../shared/components/ai-cv-rewrite-dialog/ai-cv-rewrite-dialog.component';
import { CvRewriteButtonComponent } from './cv-rewrite-button.component';

describe('CvRewriteButtonComponent', () => {
  function create(closedWith: unknown): { component: CvRewriteButtonComponent; dialog: jasmine.SpyObj<MatDialog> } {
    const dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    dialog.open.and.returnValue({ afterClosed: () => of(closedWith) } as never);
    TestBed.configureTestingModule({ imports: [CvRewriteButtonComponent, TranslateModule.forRoot()] });
    TestBed.overrideProvider(MatDialog, { useValue: dialog });
    const fixture = TestBed.createComponent(CvRewriteButtonComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, dialog };
  }

  it('opens the rewrite and tells the profile once wording was applied', () => {
    const { component, dialog } = create(true);
    const approved = jasmine.createSpy('approved');
    component.approved.subscribe(approved);
    component.open();
    expect(dialog.open).toHaveBeenCalledWith(AiCvRewriteDialogComponent, jasmine.any(Object));
    expect(approved).toHaveBeenCalled();
  });

  it('tells nothing when the rewrite was closed without applying it', () => {
    const { component } = create(undefined);
    const approved = jasmine.createSpy('approved');
    component.approved.subscribe(approved);
    component.open();
    expect(approved).not.toHaveBeenCalled();
  });
});
