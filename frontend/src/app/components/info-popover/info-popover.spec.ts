import { TestBed } from '@angular/core/testing';
import { InfoPopover } from './info-popover';

describe('Shared info popover', () => {
  it('starts hidden, opens on focus/hover/click, and closes on Escape or outside click', () => {
    TestBed.configureTestingModule({ imports: [InfoPopover] });
    const fixture = TestBed.createComponent(InfoPopover);
    fixture.componentRef.setInput('label', 'About SATD categories'); fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    const panel = fixture.nativeElement.querySelector('.info-popover') as HTMLElement;
    expect(button.getAttribute('aria-label')).toBe('About SATD categories');
    expect(panel.hidden).toBeTrue();
    for (const event of ['focus', 'mouseenter', 'click']) {
      button.dispatchEvent(new Event(event)); fixture.detectChanges();
      expect(panel.hidden).toBeFalse(); expect(button.getAttribute('aria-expanded')).toBe('true');
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' })); fixture.detectChanges();
      expect(panel.hidden).toBeTrue();
    }
    button.click(); fixture.detectChanges(); document.body.click(); fixture.detectChanges();
    expect(panel.hidden).toBeTrue();
  });
});
