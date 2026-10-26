import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import { Season } from '../../../models/season.models';
import { NotificationService } from '../../../services/notification/notification.service';
import { ALL_SEASONS, SeasonService } from '../../../services/season/season.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { toIsoDate } from '../../../shared/locale/app-locale';
import { SeasonDialogComponent } from '../season-dialog/season-dialog.component';
import { SeasonPickerComponent } from './season-picker.component';

describe('SeasonPickerComponent', () => {
  let seasons: jasmine.SpyObj<SeasonService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let confirmDialog: jasmine.SpyObj<ConfirmDialogService>;
  let fixture: ComponentFixture<SeasonPickerComponent>;
  let component: SeasonPickerComponent;
  let emitted: (Season | null)[];
  let savedSelection: string | null;

  const autumn: Season = { id: 3, name: 'Autumn search', startDate: '2026-09-12', endDate: null, active: true, freelanceId: 7 };
  const spring: Season = { id: 2, name: 'Spring search', startDate: '2026-03-01', endDate: '2026-06-30', active: false, freelanceId: 7 };

  beforeEach(() => {
    savedSelection = localStorage.getItem(SeasonService.SELECTION_KEY);
    localStorage.removeItem(SeasonService.SELECTION_KEY);
  });

  afterEach(() => {
    if (savedSelection === null) {
      localStorage.removeItem(SeasonService.SELECTION_KEY);
    } else {
      localStorage.setItem(SeasonService.SELECTION_KEY, savedSelection);
    }
  });

  function create(loaded: Observable<Season[]> = of([autumn, spring])): void {
    seasons = jasmine.createSpyObj('SeasonService', ['getByFreelanceId', 'update', 'delete']);
    seasons.getByFreelanceId.and.returnValue(loaded);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    confirmDialog = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    TestBed.configureTestingModule({
      imports: [SeasonPickerComponent, TranslateModule.forRoot()],
      providers: [
        { provide: SeasonService, useValue: seasons },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirmDialog }
      ]
    });
    TestBed.overrideProvider(MatDialog, { useValue: dialog });
    fixture = TestBed.createComponent(SeasonPickerComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('freelanceId', 7);
    fixture.componentRef.setInput('defaultTargetDailyRate', 650);
    emitted = [];
    component.selectionChange.subscribe(season => emitted.push(season));
    fixture.detectChanges();
  }

  function element(): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function dialogCloses(result?: Season): void {
    dialog.open.and.returnValue({ afterClosed: () => of(result) } as never);
  }

  /** Overlays (select panels, menus) may render next to their trigger or in the overlay container. */
  function overlay(): Document {
    return document;
  }

  async function triggerText(): Promise<string> {
    fixture.detectChanges();
    await fixture.whenStable();
    return element().querySelector('.mat-mdc-select-value')!.textContent!.trim();
  }

  describe('loading', () => {
    it('selects the running season by default', async () => {
      create();

      expect(seasons.getByFreelanceId).toHaveBeenCalledWith(7);
      expect(component.loaded).toBeTrue();
      expect(component.selection).toBe(3);
      expect(component.selectedSeason).toEqual(autumn);
      expect(emitted).toEqual([autumn]);
      expect(localStorage.getItem(SeasonService.SELECTION_KEY)).toBe('3');
      expect(await triggerText()).toBe('Autumn search');
    });

    it('reopens the season chosen last time', () => {
      localStorage.setItem(SeasonService.SELECTION_KEY, '2');
      create();

      expect(emitted).toEqual([spring]);
    });

    it('reopens every season when that was the last choice', async () => {
      localStorage.setItem(SeasonService.SELECTION_KEY, ALL_SEASONS);
      create();

      expect(component.selection).toBe(ALL_SEASONS);
      expect(emitted).toEqual([null]);
      expect(await triggerText()).toBe('seasons.all');
      expect(element().querySelector('button[mat-icon-button]')).toBeNull();
    });

    it('falls back to every season when the seasons cannot be loaded', () => {
      create(throwError(() => new Error('offline')));

      expect(component.seasons).toEqual([]);
      expect(component.loaded).toBeTrue();
      expect(notifications.error).toHaveBeenCalledWith('seasons.loadError');
      expect(emitted).toEqual([null]);
    });
  });

  describe('selection', () => {
    it('remembers and announces the chosen season', () => {
      create();

      component.select(2);
      component.select(ALL_SEASONS);

      expect(emitted).toEqual([autumn, spring, null]);
      expect(localStorage.getItem(SeasonService.SELECTION_KEY)).toBe(ALL_SEASONS);
    });

    it('lists the seasons with their dates and running badge', async () => {
      create();
      element().querySelector<HTMLElement>('.mat-mdc-select-trigger')!.click();
      fixture.detectChanges();
      await fixture.whenStable();

      const options = Array.from(overlay().querySelectorAll<HTMLElement>('mat-option'));
      expect(options.map(option => option.querySelector('.option-name')!.textContent!.trim()))
        .toEqual(['seasons.all', 'Autumn search', 'Spring search']);
      expect(options[1].querySelector('.season-badge.running')).not.toBeNull();
      expect(options[2].querySelector('.season-badge')).toBeNull();
      expect(options[2].querySelector('.option-dates')!.textContent).toBe('Mar 1, 2026 – Jun 30, 2026');

      options[2].click();
      expect(emitted).toEqual([autumn, spring]);
    });

    it('formats the dates of a season', () => {
      create();

      expect(component.formatRange(autumn)).toBe('Sep 12, 2026');
      expect(component.formatRange(spring)).toBe('Mar 1, 2026 – Jun 30, 2026');
    });
  });

  describe('new season', () => {
    it('opens the season dialog with the profile target, then selects the saved season', () => {
      create();
      const created: Season = { id: 4, name: 'Winter search', startDate: '2026-12-01', freelanceId: 7 };
      dialogCloses(created);
      seasons.getByFreelanceId.and.returnValue(of([created, autumn, spring]));

      element().querySelector<HTMLButtonElement>('.new-season-button')!.click();

      expect(dialog.open).toHaveBeenCalledWith(SeasonDialogComponent, {
        data: { freelanceId: 7, defaultTargetDailyRate: 650 }, autoFocus: 'first-tabbable'
      });
      expect(seasons.getByFreelanceId).toHaveBeenCalledTimes(2);
      expect(emitted).toEqual([autumn, created]);
    });

    it('keeps the selection when the dialog is cancelled', () => {
      create();
      dialogCloses(undefined);

      component.openNewSeason();

      expect(seasons.getByFreelanceId).toHaveBeenCalledTimes(1);
      expect(emitted).toEqual([autumn]);
    });
  });

  describe('season actions', () => {
    async function openMenu(): Promise<HTMLButtonElement[]> {
      element().querySelector<HTMLButtonElement>('button[mat-icon-button]')!.click();
      fixture.detectChanges();
      await fixture.whenStable();
      return Array.from(overlay().querySelectorAll<HTMLButtonElement>('[mat-menu-item]'));
    }

    it('offers to edit, close and delete a running season', async () => {
      create();

      const items = await openMenu();

      expect(items.map(item => item.textContent!.trim())).toEqual([
        'editseasons.edit', 'sports_scoreseasons.close', 'deleteseasons.delete'
      ]);
    });

    it('does not offer to close a season that already ended', async () => {
      localStorage.setItem(SeasonService.SELECTION_KEY, '2');
      create();

      const items = await openMenu();

      expect(items.map(item => item.textContent!.trim())).toEqual(['editseasons.edit', 'deleteseasons.delete']);
    });

    it('edits the selected season', async () => {
      create();
      dialogCloses({ ...autumn, name: 'Autumn 2026' });
      seasons.getByFreelanceId.and.returnValue(of([{ ...autumn, name: 'Autumn 2026' }, spring]));

      (await openMenu())[0].click();

      expect(dialog.open).toHaveBeenCalledWith(SeasonDialogComponent, {
        data: { freelanceId: 7, season: autumn }, autoFocus: 'first-tabbable'
      });
      expect(emitted.at(-1)!.name).toBe('Autumn 2026');
    });

    it('has nothing to edit, close or delete when every season is shown', () => {
      create();
      component.select(ALL_SEASONS);

      component.editSelected();
      component.closeSelected();
      component.deleteSelected();

      expect(dialog.open).not.toHaveBeenCalled();
      expect(seasons.update).not.toHaveBeenCalled();
      expect(confirmDialog.confirm).not.toHaveBeenCalled();
    });

    it('closes a running season on the eve of today', async () => {
      create();
      seasons.update.and.returnValue(of({ ...autumn, endDate: 'yesterday' }));
      const yesterday = new Date();
      yesterday.setDate(yesterday.getDate() - 1);

      (await openMenu())[1].click();

      expect(seasons.update).toHaveBeenCalledWith(3, { ...autumn, endDate: toIsoDate(yesterday)! });
      expect(notifications.success).toHaveBeenCalledWith('seasons.closedSuccess');
      expect(seasons.getByFreelanceId).toHaveBeenCalledTimes(2);
      expect(emitted).toEqual([autumn, autumn]);
    });

    it('ends a season started today on its first day', () => {
      const today = toIsoDate(new Date())!;
      create(of([{ ...autumn, startDate: today }]));
      seasons.update.and.returnValue(of(autumn));

      component.closeSelected();

      expect(seasons.update).toHaveBeenCalledWith(3, jasmine.objectContaining({ startDate: today, endDate: today }));
    });

    it('reports a season that cannot be closed', () => {
      create();
      seasons.update.and.returnValue(throwError(() => new Error('offline')));

      component.closeSelected();

      expect(notifications.error).toHaveBeenCalledWith('seasons.saveError');
      expect(seasons.getByFreelanceId).toHaveBeenCalledTimes(1);
    });

    it('deletes the selected season once confirmed, then shows every season', async () => {
      create();
      confirmDialog.confirm.and.returnValue(of(true));
      seasons.delete.and.returnValue(of(undefined));
      seasons.getByFreelanceId.and.returnValue(of([spring]));

      (await openMenu())[2].click();

      expect(confirmDialog.confirm).toHaveBeenCalledWith({
        titleKey: 'seasons.deleteTitle', messageKey: 'seasons.deleteConfirm', messageParams: { name: 'Autumn search' },
        confirmKey: 'common.delete', danger: true
      });
      expect(seasons.delete).toHaveBeenCalledWith(3);
      expect(notifications.success).toHaveBeenCalledWith('seasons.deletedSuccess');
      expect(emitted).toEqual([autumn, null]);
      expect(component.selection).toBe(ALL_SEASONS);
    });

    it('keeps the season when the deletion is not confirmed', () => {
      create();
      confirmDialog.confirm.and.returnValue(of(false));

      component.deleteSelected();

      expect(seasons.delete).not.toHaveBeenCalled();
    });

    it('reports a season that cannot be deleted', () => {
      create();
      confirmDialog.confirm.and.returnValue(of(true));
      seasons.delete.and.returnValue(throwError(() => new Error('offline')));

      component.deleteSelected();

      expect(notifications.error).toHaveBeenCalledWith('seasons.deleteError');
      expect(component.selection).toBe(3);
    });
  });
});
