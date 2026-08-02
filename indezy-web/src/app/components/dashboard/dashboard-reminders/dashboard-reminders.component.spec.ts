import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { DashboardRemindersComponent } from './dashboard-reminders.component';
import { DashboardStatsDto, OnThisDayItem } from '../../../models';

describe('DashboardRemindersComponent', () => {
  let fixture: ComponentFixture<DashboardRemindersComponent>;
  let component: DashboardRemindersComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardRemindersComponent, RouterModule.forRoot([]), TranslateModule.forRoot()]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardRemindersComponent);
    component = fixture.componentInstance;
  });

  it('returns empty lists when no stats are set', () => {
    expect(component.getMissionsEndingSoon()).toEqual([]);
    expect(component.getDormantContacts()).toEqual([]);
    expect(component.getStaleOpportunities()).toEqual([]);
  });

  it('reads reminder lists from the provided stats', () => {
    component.stats = {
      dormantContacts: [{ id: 3, name: 'Old', clientName: 'Acme', monthsSinceActivity: 7 }]
    } as DashboardStatsDto;

    expect(component.getDormantContacts().length).toBe(1);
    expect(component.getMissionsEndingSoon()).toEqual([]);
  });

  it('links "on this day" items by type', () => {
    expect(component.getOnThisDayLink({ type: 'CONTACT', id: 5 } as OnThisDayItem)).toEqual(['/contacts', 5]);
    expect(component.getOnThisDayLink({ type: 'PROJECT', id: 9 } as OnThisDayItem)).toEqual(['/projects', 9]);
  });
});
