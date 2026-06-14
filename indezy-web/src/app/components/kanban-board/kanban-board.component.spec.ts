import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
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
    mockProjectService = jasmine.createSpyObj('ProjectService', ['getKanbanBoard', 'updateStatus', 'toggleFavorite']);
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
  });

  describe('Favorites', () => {
    let event: jasmine.SpyObj<Event>;
    let target: KanbanProjectCardDto;

    beforeEach(() => {
      // Use a fresh board so toggling does not mutate the shared fixtures.
      target = { ...mockCard, isFavorite: false };
      const other: KanbanProjectCardDto = { ...mockCard, projectId: 99, isFavorite: false };
      mockProjectService.getKanbanBoard.and.returnValue(of({
        columns: { [ProjectStatus.APPLIED]: [other, target] },
        columnOrder: [ProjectStatus.APPLIED]
      }));
      fixture.detectChanges();
      event = jasmine.createSpyObj('Event', ['stopPropagation', 'preventDefault']);
    });

    it('should toggle the favorite flag and pin the card to the top', () => {
      mockProjectService.toggleFavorite.and.returnValue(of({ isFavorite: true } as ProjectDto));

      component.toggleFavorite(target, event);

      expect(event.stopPropagation).toHaveBeenCalled();
      expect(event.preventDefault).toHaveBeenCalled();
      expect(mockProjectService.toggleFavorite).toHaveBeenCalledWith(42);
      expect(target.isFavorite).toBeTrue();
      // Favorite is pinned to the top of its column.
      expect(component.getCardsForColumn(ProjectStatus.APPLIED)[0]).toBe(target);
    });

    it('should notify on toggle failure', () => {
      mockProjectService.toggleFavorite.and.returnValue(throwError(() => new Error('boom')));

      component.toggleFavorite(target, event);

      expect(mockNotificationService.error).toHaveBeenCalledWith('errors.updatingFavorite');
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
