import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { Router, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

import { NetworkBackComponent } from './network-back.component';

@Component({ template: '' })
class BlankComponent {}

describe('NetworkBackComponent', () => {
  function create(fallback?: string) {
    TestBed.configureTestingModule({
      imports: [NetworkBackComponent, TranslateModule.forRoot()],
      providers: [provideRouter([{ path: '**', component: BlankComponent }])]
    });
    const fixture = TestBed.createComponent(NetworkBackComponent);
    if (fallback) {
      fixture.componentRef.setInput('fallback', fallback);
    }
    fixture.detectChanges();
    return fixture;
  }

  it('goes back to the page the reader came from within the app', async () => {
    const fixture = create();
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/network/members');
    await router.navigateByUrl('/network/tags/kafka');
    const back = spyOn(TestBed.inject(Location), 'back');
    (fixture.nativeElement.querySelector('[data-network-back]') as HTMLButtonElement).click();
    expect(back).toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('network.back');
  });

  it('goes to the feed, or the page given, when this page was opened first', async () => {
    let fixture = create();
    let router = TestBed.inject(Router);
    await router.navigateByUrl('/network/saved');
    const navigate = spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture.componentInstance.back();
    expect(navigate).toHaveBeenCalledWith('/network');
    TestBed.resetTestingModule();
    fixture = create('/network/members');
    router = TestBed.inject(Router);
    const navigateAgain = spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture.componentInstance.back();
    expect(navigateAgain).toHaveBeenCalledWith('/network/members');
  });
});
