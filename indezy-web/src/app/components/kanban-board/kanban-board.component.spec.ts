import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { MatDialogRef } from '@angular/material/dialog';
import { of, throwError, BehaviorSubject } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';

import { KanbanBoardComponent } from './kanban-board.component';
import { ProjectService } from '../../services/project/project.service';
import { AuthService } from '../../services/auth/auth.service';
import { NotificationService } from '../../services/notification/notification.service';
import { KanbanBoardDto, KanbanProjectCardDto, ProjectDto, ProjectStatus } from '../../models/project.models';
import { User } from '../../models';

describe('KanbanBoardComponent', () => {
  let component: KanbanBoardComponent;
  let fixture: ComponentFixture<KanbanBoardComponent>;
  let mockProjectService: jasmine.SpyObj<ProjectService>;
  let mockNotificationService: jasmine.SpyObj<NotificationService>;
  let currentUser$: BehaviorSubject<User | null>;

  const mockUser = { id: 1, email: 'test@example.com', firstName: 'Test', lastName: 'User' } as User;

  const mockCard: KanbanProjectCardDto = {
    projectId: 42,
    role: 'Full Stack Developer',
    status: ProjectStatus.APPLIED,
    clientName: 'Acme',
    dailyRate: 600,
    totalSteps: 5,
    completedSteps: 2,
    failedSteps: 0,
    personalRating: 4
  };

  const mockBoard: KanbanBoardDto = {
    columns: {
      [ProjectStatus.IDENTIFIED]: [],
      [ProjectStatus.APPLIED]: [mockCard],
      [ProjectStatus.INTERVIEW]: []
    },
    columnOrder: [ProjectStatus.IDENTIFIED, ProjectStatus.APPLIED, ProjectStatus.INTERVIEW]
  };

  beforeEach(async () => {
    mockProjectService = jasmine.createSpyObj('ProjectService', ['getKanbanBoard', 'updateStatus', 'setFavorite', 'reorderColumn', 'markAsLost']);
    mockNotificationService = jasmine.createSpyObj('NotificationService', ['success', 'error', 'successText', 'errorText']);
    currentUser$ = new BehaviorSubject<User | null>(mockUser);
    const mockAuthService = jasmine.createSpyObj('AuthService', ['getUser'], { currentUser$ });

    await TestBed.configureTestingModule({
      imports: [KanbanBoardComponent, TranslateModule.forRoot()],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ProjectService, useValue: mockProjectService },
        { provide: AuthService, useValue: mockAuthService },
        { provide: NotificationService, useValue: mockNotificationService },
        { provide: ActivatedRoute, useValue: { snapshot: { params: {} } } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(KanbanBoardComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Initialization', () => {
    it('should load the board for the current user', () => {
      mockProjectService.getKanbanBoard.and.returnValue(of(mockBoard));

      fixture.detectChanges();

      expect(mockProjectService.getKanbanBoard).toHaveBeenCalledWith(1);
      expect(component.kanbanBoard).toEqual(mockBoard);
      expect(component.isLoading).toBeFalse();
    });

    it('should not load the board when there is no user', () => {
      currentUser$.next(null);

      fixture.detectChanges();

      expect(mockProjectService.getKanbanBoard).not.toHaveBeenCalled();
    });

    it('should notify on load error', () => {
      mockProjectService.getKanbanBoard.and.returnValue(throwError(() => new Error('boom')));

      fixture.detectChanges();

      expect(mockNotificationService.error).toHaveBeenCalledWith('errors.loadingBoard');
      expect(component.isLoading).toBeFalse();
    });
  });

  describe('Board data accessors', () => {
    beforeEach(() => {
      mockProjectService.getKanbanBoard.and.returnValue(of(mockBoard));
      fixture.detectChanges();
    });

    it('should return columns in order', () => {
      expect(component.getColumns()).toEqual(mockBoard.columnOrder);
    });

    it('should return cards for a column', () => {
      expect(component.getCardsForColumn(ProjectStatus.APPLIED)).toEqual([mockCard]);
      expect(component.getCardsForColumn(ProjectStatus.INTERVIEW)).toEqual([]);
    });

    it('should build column ids and connected drop lists', () => {
      expect(component.getColumnId(ProjectStatus.APPLIED)).toBe(`kanban-col-${ProjectStatus.APPLIED}`);
      expect(component.getConnectedDropLists()).toEqual(
        mockBoard.columnOrder.map(s => `kanban-col-${s}`)
      );
    });

    it('should count cards per column', () => {
      expect(component.getColumnCount(ProjectStatus.APPLIED)).toBe(1);
      expect(component.getColumnCount(ProjectStatus.IDENTIFIED)).toBe(0);
    });

    it('should detect a non-empty board', () => {
      expect(component.isBoardEmpty()).toBeFalse();
    });

    it('should detect an empty board', () => {
      component.kanbanBoard = {
        columns: { [ProjectStatus.APPLIED]: [] },
        columnOrder: [ProjectStatus.APPLIED]
      };
      expect(component.isBoardEmpty()).toBeTrue();
    });
  });

  function dropEvent(previousId: string, containerId: string): CdkDragDrop<KanbanProjectCardDto[]> {
    return {
      previousContainer: { data: [mockCard], id: previousId },
      container: { data: [], id: containerId },
      previousIndex: 0,
      currentIndex: 0
    } as unknown as CdkDragDrop<KanbanProjectCardDto[]>;
  }

  function sameColumnDropEvent(prevIndex: number, currIndex: number): CdkDragDrop<KanbanProjectCardDto[]> {
    const container = {
      data: [
        { ...mockCard, projectId: 1 },
        { ...mockCard, projectId: 2 },
        { ...mockCard, projectId: 3 }
      ],
      id: `kanban-col-${ProjectStatus.APPLIED}`
    };
    return {
      previousContainer: container,
      container,
      previousIndex: prevIndex,
      currentIndex: currIndex
    } as unknown as CdkDragDrop<KanbanProjectCardDto[]>;
  }

  function dialogRefReturning(result: unknown): MatDialogRef<unknown> {
    const ref = jasmine.createSpyObj<MatDialogRef<unknown>>('MatDialogRef', ['afterClosed']);
    ref.afterClosed.and.returnValue(of(result));
    return ref;
  }

  describe('Card drag and drop', () => {
    beforeEach(() => {
      mockProjectService.getKanbanBoard.and.returnValue(of(mockBoard));
      fixture.detectChanges();
    });

    it('should update status and notify on cross-column drop', () => {
      mockProjectService.updateStatus.and.returnValue(of({} as ProjectDto));

      component.onCardDrop(dropEvent(
        `kanban-col-${ProjectStatus.APPLIED}`,
        `kanban-col-${ProjectStatus.INTERVIEW}`
      ));

      expect(mockProjectService.updateStatus).toHaveBeenCalledWith(42, ProjectStatus.INTERVIEW);
      expect(mockNotificationService.successText).toHaveBeenCalled();
    });

    it('should reload the board when the status update fails', () => {
      mockProjectService.updateStatus.and.returnValue(throwError(() => new Error('boom')));

      component.onCardDrop(dropEvent(
        `kanban-col-${ProjectStatus.APPLIED}`,
        `kanban-col-${ProjectStatus.INTERVIEW}`
      ));

      expect(mockNotificationService.error).toHaveBeenCalledWith('errors.movingCard');
      expect(mockProjectService.getKanbanBoard).toHaveBeenCalledTimes(2);
    });

    it('should persist the new order on a within-column reorder', () => {
      mockProjectService.reorderColumn.and.returnValue(of(undefined));

      component.onCardDrop(sameColumnDropEvent(0, 2));

      // After moving index 0 to 2: order becomes [2, 3, 1]
      expect(mockProjectService.reorderColumn).toHaveBeenCalledWith([2, 3, 1]);
    });

    it('should do nothing when a card is dropped in its original position', () => {
      component.onCardDrop(sameColumnDropEvent(1, 1));

      expect(mockProjectService.reorderColumn).not.toHaveBeenCalled();
    });

    it('should reload and notify when persisting the order fails', () => {
      mockProjectService.reorderColumn.and.returnValue(throwError(() => new Error('boom')));

      component.onCardDrop(sameColumnDropEvent(0, 1));

      expect(mockNotificationService.error).toHaveBeenCalledWith('kanban.reorder.error');
      expect(mockProjectService.getKanbanBoard).toHaveBeenCalledTimes(2);
    });

    it('should open the lost-reason dialog instead of a plain status update when dropping into LOST', () => {
      const openSpy = spyOn(component['dialog'], 'open').and.returnValue(dialogRefReturning(undefined));

      component.onCardDrop(dropEvent(
        `kanban-col-${ProjectStatus.APPLIED}`,
        `kanban-col-${ProjectStatus.LOST}`
      ));

      expect(openSpy).toHaveBeenCalled();
      // No plain status update for LOST; capture goes through markAsLost after confirmation.
      expect(mockProjectService.updateStatus).not.toHaveBeenCalled();
      // Cancelled (undefined result): nothing persisted.
      expect(mockProjectService.markAsLost).not.toHaveBeenCalled();
    });

    it('should mark the project as lost with the chosen reason on confirm', () => {
      spyOn(component['dialog'], 'open').and.returnValue(dialogRefReturning({ reason: 'RATE_TOO_LOW' }));
      mockProjectService.markAsLost.and.returnValue(of({ role: 'x', dailyRate: 1 } as ProjectDto));

      component.onCardDrop(dropEvent(
        `kanban-col-${ProjectStatus.APPLIED}`,
        `kanban-col-${ProjectStatus.LOST}`
      ));

      expect(mockProjectService.markAsLost).toHaveBeenCalledWith(42, 'RATE_TOO_LOW');
      // reload after marking lost (initial load + reload)
      expect(mockProjectService.getKanbanBoard).toHaveBeenCalledTimes(2);
    });
  });

  describe('Display helpers', () => {
    it('should format currency in euros', () => {
      const formatted = component.formatCurrency(600);
      expect(formatted).toContain('600');
      expect(formatted).toContain('€');
    });

    it('should build rating stars', () => {
      expect(component.getRatingStars(3)).toEqual(['star', 'star', 'star']);
      expect(component.getRatingStars()).toEqual([]);
    });
  });

  describe('Card aging', () => {
    const cardAgedDays = (days: number, status: string = ProjectStatus.APPLIED): KanbanProjectCardDto => {
      const d = new Date();
      d.setDate(d.getDate() - days);
      return { ...mockCard, status, updatedAt: d.toISOString() };
    };

    it('should return null days when there is no updatedAt', () => {
      expect(component.getDaysSinceActivity({ ...mockCard, updatedAt: undefined })).toBeNull();
    });

    it('should return null days when updatedAt is unparseable', () => {
      expect(component.getDaysSinceActivity({ ...mockCard, updatedAt: 'not-a-date' })).toBeNull();
    });

    it('should compute whole days since last activity', () => {
      expect(component.getDaysSinceActivity(cardAgedDays(10))).toBe(10);
    });

    it('should clamp future timestamps to zero days', () => {
      const future = new Date();
      future.setDate(future.getDate() + 5);
      expect(component.getDaysSinceActivity({ ...mockCard, updatedAt: future.toISOString() })).toBe(0);
    });

    it('should report no aging below the warn threshold', () => {
      expect(component.getAgingLevel(cardAgedDays(3))).toBe('none');
      expect(component.isAging(cardAgedDays(3))).toBeFalse();
    });

    it('should report warn aging at the warn threshold', () => {
      expect(component.getAgingLevel(cardAgedDays(component.AGING_WARN_DAYS))).toBe('warn');
      expect(component.isAging(cardAgedDays(component.AGING_WARN_DAYS))).toBeTrue();
    });

    it('should report stale aging at the stale threshold', () => {
      expect(component.getAgingLevel(cardAgedDays(component.AGING_STALE_DAYS))).toBe('stale');
    });

    it('should never age won or lost cards', () => {
      expect(component.getAgingLevel(cardAgedDays(60, ProjectStatus.WON))).toBe('none');
      expect(component.getAgingLevel(cardAgedDays(60, ProjectStatus.LOST))).toBe('none');
    });

    it('should report no aging when there is no timestamp', () => {
      expect(component.getAgingLevel({ ...mockCard, updatedAt: undefined })).toBe('none');
    });
  });

  describe('Quick add', () => {
    let openSpy: jasmine.Spy;

    beforeEach(() => {
      mockProjectService.getKanbanBoard.and.returnValue(of(mockBoard));
      fixture.detectChanges();
      openSpy = spyOn(component['dialog'], 'open');
    });

    it('should open the quick-add dialog with the column status and reload on success', () => {
      openSpy.and.returnValue(dialogRefReturning({ id: 123 }));

      component.openQuickAdd(ProjectStatus.APPLIED);

      expect(openSpy).toHaveBeenCalled();
      const config = openSpy.calls.mostRecent().args[1];
      expect(config?.data).toEqual({ freelanceId: 1, status: ProjectStatus.APPLIED });
      // initial load + reload after creation
      expect(mockProjectService.getKanbanBoard).toHaveBeenCalledTimes(2);
    });

    it('should not reload when the dialog is dismissed without a result', () => {
      openSpy.and.returnValue(dialogRefReturning(undefined));

      component.openQuickAdd(ProjectStatus.APPLIED);

      expect(mockProjectService.getKanbanBoard).toHaveBeenCalledTimes(1);
    });

    it('should not open the dialog without a current user', () => {
      component.currentUserId = null;

      component.openQuickAdd(ProjectStatus.APPLIED);

      expect(openSpy).not.toHaveBeenCalled();
    });
  });

  describe('Favorites', () => {
    beforeEach(() => {
      mockProjectService.getKanbanBoard.and.returnValue(of(mockBoard));
      fixture.detectChanges();
    });

    const projectDtoStub = (favorite: boolean): ProjectDto =>
      ({ role: 'Dev', dailyRate: 500, favorite });

    it('should toggle favorite, stop event propagation and reload on success', () => {
      const card = { ...mockCard, favorite: false };
      mockProjectService.setFavorite.and.returnValue(of(projectDtoStub(true)));
      const event = jasmine.createSpyObj('Event', ['stopPropagation', 'preventDefault']);

      component.toggleFavorite(card, event);

      expect(event.stopPropagation).toHaveBeenCalled();
      expect(event.preventDefault).toHaveBeenCalled();
      expect(mockProjectService.setFavorite).toHaveBeenCalledWith(card.projectId, true);
      expect(card.favorite).toBeTrue();
      // initial load + reload after toggle
      expect(mockProjectService.getKanbanBoard).toHaveBeenCalledTimes(2);
    });

    it('should unpin a favorite card', () => {
      const card = { ...mockCard, favorite: true };
      mockProjectService.setFavorite.and.returnValue(of(projectDtoStub(false)));
      const event = jasmine.createSpyObj('Event', ['stopPropagation', 'preventDefault']);

      component.toggleFavorite(card, event);

      expect(mockProjectService.setFavorite).toHaveBeenCalledWith(card.projectId, false);
    });

    it('should notify on favorite toggle failure', () => {
      const card = { ...mockCard, favorite: false };
      mockProjectService.setFavorite.and.returnValue(throwError(() => new Error('fail')));
      const event = jasmine.createSpyObj('Event', ['stopPropagation', 'preventDefault']);

      component.toggleFavorite(card, event);

      expect(mockNotificationService.error).toHaveBeenCalledWith('kanban.favorite.error');
    });

    it('should filter cards to favorites only when the filter is on', () => {
      component.kanbanBoard = {
        columns: {
          [ProjectStatus.APPLIED]: [
            { ...mockCard, projectId: 1, favorite: true },
            { ...mockCard, projectId: 2, favorite: false }
          ]
        },
        columnOrder: [ProjectStatus.APPLIED]
      };

      expect(component.getCardsForColumn(ProjectStatus.APPLIED).length).toBe(2);

      component.toggleFavoritesFilter();

      expect(component.showFavoritesOnly).toBeTrue();
      const filtered = component.getCardsForColumn(ProjectStatus.APPLIED);
      expect(filtered.length).toBe(1);
      expect(filtered[0].projectId).toBe(1);
    });
  });

  describe('Cleanup', () => {
    it('should complete destroy subject on destroy', () => {
      spyOn(component['destroy$'], 'next');
      spyOn(component['destroy$'], 'complete');

      component.ngOnDestroy();

      expect(component['destroy$'].next).toHaveBeenCalled();
      expect(component['destroy$'].complete).toHaveBeenCalled();
    });
  });
});
