import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ListSelectionBarComponent } from './list-selection-bar.component';

describe('ListSelectionBarComponent', () => {
  it('counts the selection and hands over its deletion and clearing', () => {
    TestBed.configureTestingModule({ imports: [ListSelectionBarComponent, TranslateModule.forRoot()] });
    const fixture = TestBed.createComponent(ListSelectionBarComponent);
    fixture.componentRef.setInput('count', 3);
    fixture.componentRef.setInput('busy', true);
    fixture.detectChanges();
    const events: string[] = [];
    fixture.componentInstance.delete.subscribe(() => events.push('delete'));
    fixture.componentInstance.clear.subscribe(() => events.push('clear'));
    const buttons = (fixture.nativeElement as HTMLElement).querySelectorAll('button');

    expect(buttons[0].disabled).toBe(true);
    fixture.componentRef.setInput('busy', false);
    fixture.detectChanges();
    buttons[0].click();
    buttons[1].click();

    expect(events).toEqual(['delete', 'clear']);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('common.selection.count');
  });
});
