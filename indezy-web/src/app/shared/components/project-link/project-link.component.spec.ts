import { TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { Clipboard } from '@angular/cdk/clipboard';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProjectLinkComponent } from './project-link.component';

describe('ProjectLinkComponent', () => {
  let clipboard: jasmine.SpyObj<Clipboard>;
  let notifications: jasmine.SpyObj<NotificationService>;

  beforeEach(() => {
    clipboard = jasmine.createSpyObj('Clipboard', ['copy']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [ProjectLinkComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: Clipboard, useValue: clipboard },
        { provide: NotificationService, useValue: notifications }]
    });
  });

  it('copies an absolute link respecting the deployment base and leaves the card open', () => {
    const fixture = TestBed.createComponent(ProjectLinkComponent);
    fixture.componentRef.setInput('projectId', 42);
    spyOn(TestBed.inject(Location), 'prepareExternalUrl').and.returnValue('/indezy/projects?project=42');
    clipboard.copy.and.returnValue(true);
    const event = new MouseEvent('click');
    spyOn(event, 'stopPropagation');
    fixture.componentInstance.copy(event);
    expect(clipboard.copy).toHaveBeenCalledWith(`${location.origin}/indezy/projects?project=42`);
    expect(event.stopPropagation).toHaveBeenCalled();
    expect(notifications.success).toHaveBeenCalledWith('projects.share.copied');
  });

  it('reports clipboard failures without claiming success', () => {
    const fixture = TestBed.createComponent(ProjectLinkComponent);
    fixture.componentRef.setInput('projectId', 42);
    clipboard.copy.and.returnValue(false);
    fixture.componentInstance.copy(new MouseEvent('click'));
    expect(notifications.error).toHaveBeenCalledWith('projects.share.failed');
    expect(notifications.success).not.toHaveBeenCalled();
  });
});
