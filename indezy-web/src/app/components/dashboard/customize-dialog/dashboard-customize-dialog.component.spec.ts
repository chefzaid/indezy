import { TestBed } from '@angular/core/testing';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { DashboardWidget } from '../../../models/user-management.models';
import { DashboardCustomizeDialogComponent, USUAL_LAYOUT } from './dashboard-customize-dialog.component';
import en from '../../../../assets/i18n/en.json';

describe('DashboardCustomizeDialogComponent', () => {
  const arranged: DashboardWidget[] = [{ id: 'OFFERS', visible: true }, { id: 'STATS', visible: false }, { id: 'CHARTS', visible: true }];

  function create() {
    TestBed.configureTestingModule({
      imports: [DashboardCustomizeDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MAT_DIALOG_DATA, useValue: arranged }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(DashboardCustomizeDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('lists the blocks in the account\'s order with their names, the first unable to go up', () => {
    const fixture = create();
    const items = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('[data-widget]'));

    expect(items.map(item => item.getAttribute('data-widget'))).toEqual(['OFFERS', 'STATS', 'CHARTS']);
    expect(items[1].textContent).toContain('Key figures');
    expect(items[0].querySelector('button[aria-label="Move New offers up"]')?.hasAttribute('disabled')).toBeTrue();
    expect(items[2].querySelector('button[aria-label="Move Charts down"]')?.hasAttribute('disabled')).toBeTrue();
  });

  it('reorders by dragging or with the arrows, shows and hides, without changing what it was given', () => {
    const component = create().componentInstance;

    component.dropped({ previousIndex: 2, currentIndex: 0 } as CdkDragDrop<DashboardWidget[]>);
    expect(component.widgets.map(widget => widget.id)).toEqual(['CHARTS', 'OFFERS', 'STATS']);
    component.move(2, -1);
    expect(component.widgets.map(widget => widget.id)).toEqual(['CHARTS', 'STATS', 'OFFERS']);
    component.toggle(1, true);
    expect(component.widgets[1]).toEqual({ id: 'STATS', visible: true });
    expect(arranged[1].visible).toBeFalse();

    component.reset();
    expect(component.widgets).toEqual(USUAL_LAYOUT);
  });
});
