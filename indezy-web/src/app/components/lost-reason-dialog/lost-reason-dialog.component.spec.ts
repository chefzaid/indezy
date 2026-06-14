import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';

import { LostReasonDialogComponent, LostReasonDialogData } from './lost-reason-dialog.component';

describe('LostReasonDialogComponent', () => {
  let component: LostReasonDialogComponent;
  let fixture: ComponentFixture<LostReasonDialogComponent>;
  let mockDialogRef: jasmine.SpyObj<MatDialogRef<LostReasonDialogComponent>>;

  const dialogData: LostReasonDialogData = { role: 'Full Stack Developer' };

  beforeEach(async () => {
    mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [
        LostReasonDialogComponent,
        FormsModule,
        NoopAnimationsModule,
        TranslateModule.forRoot()
      ],
      providers: [
        { provide: MatDialogRef, useValue: mockDialogRef },
        { provide: MAT_DIALOG_DATA, useValue: dialogData }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LostReasonDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and expose the available reasons', () => {
    expect(component).toBeTruthy();
    expect(component.reasons.length).toBe(6);
  });

  it('should close with the selected reason on confirm', () => {
    component.selectedReason = 'RATE_TOO_LOW';

    component.confirm();

    expect(mockDialogRef.close).toHaveBeenCalledWith({ reason: 'RATE_TOO_LOW' });
  });

  it('should close with an undefined reason when none is selected (still confirmed)', () => {
    component.confirm();

    expect(mockDialogRef.close).toHaveBeenCalledWith({ reason: undefined });
  });

  it('should close with no result on cancel', () => {
    component.cancel();

    expect(mockDialogRef.close).toHaveBeenCalledWith();
  });
});
