import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { UiPreferencesService } from '../../services/ui-preferences.service';
import { Sidebar } from './sidebar';

describe('Sidebar compact branding', () => {
  let fixture: ComponentFixture<Sidebar>;
  let preferences: UiPreferencesService;

  beforeEach(async () => {
    localStorage.removeItem('spl3.sidebar');
    document.documentElement.classList.remove('sidebar-compact');
    await TestBed.configureTestingModule({
      imports: [Sidebar],
      providers: [provideRouter([])]
    }).compileComponents();
    fixture = TestBed.createComponent(Sidebar);
    preferences = TestBed.inject(UiPreferencesService);
    preferences.setSidebar('expanded');
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    localStorage.removeItem('spl3.sidebar');
    document.documentElement.classList.remove('sidebar-compact');
  });

  it('keeps both brand lines visible in expanded mode', () => {
    const host = fixture.nativeElement as HTMLElement;
    const brandText = host.querySelector('.sidebar-brand-text') as HTMLElement;
    const sidebar = host.querySelector('.app-sidebar') as HTMLElement;
    expect(getComputedStyle(brandText).display).not.toBe('none');
    expect(host.textContent).toContain('Code Review');
    expect(host.textContent).toContain('SATD Detection');
    expect(getComputedStyle(sidebar).width).toBe('256px');
  });

  it('centers only the logo and hides both brand lines and navigation labels in desktop compact mode', () => {
    const desktop = window.matchMedia('(min-width: 768px)').matches;
    preferences.setSidebar('compact');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const sidebar = host.querySelector('.app-sidebar') as HTMLElement;
    const brand = host.querySelector('.sidebar-brand') as HTMLElement;
    const brandText = host.querySelector('.sidebar-brand-text') as HTMLElement;
    const logo = brand.querySelector('img') as HTMLImageElement;
    const firstLabel = host.querySelector('.sidebar-label') as HTMLElement;

    expect(document.documentElement.classList.contains('sidebar-compact')).toBeTrue();
    if (desktop) {
      expect(getComputedStyle(sidebar).width).toBe('72px');
      expect(getComputedStyle(brandText).display).toBe('none');
      expect(getComputedStyle(firstLabel).display).toBe('none');
      expect(getComputedStyle(brand).justifyContent).toBe('center');
      expect(getComputedStyle(brand).paddingLeft).toBe('0px');
      expect(getComputedStyle(brand).paddingRight).toBe('0px');
    } else {
      // Karma may launch a viewport narrower than the desktop breakpoint.
      // Verify the actual loaded desktop CSS rather than testing a rule that cannot apply.
      const desktopCss = Array.from(document.styleSheets)
        .flatMap(sheet => {
          try { return Array.from(sheet.cssRules); } catch { return []; }
        })
        .filter((rule): rule is CSSMediaRule =>
          rule instanceof CSSMediaRule && rule.conditionText.includes('768px'))
        .map(rule => rule.cssText).join('\n');
      expect(desktopCss).toContain('.sidebar-compact .app-sidebar .sidebar-brand-text');
      expect(desktopCss).toContain('.sidebar-compact .app-sidebar .sidebar-label');
      expect(desktopCss).toContain('display: none');
      expect(desktopCss).toContain('justify-content: center');
      expect(desktopCss).toContain('padding-inline');
    }
    expect(getComputedStyle(logo).width).toBe('36px');
    expect(logo.getAttribute('src')).toBe('/brand-icon.png');
    expect(host.querySelectorAll('nav a').length).toBe(3);
    expect(host.querySelector('nav a')?.getAttribute('aria-label')).toBe('Dashboard');
    expect(host.querySelector('nav a')?.getAttribute('title')).toBe('Dashboard');
  });

  it('restores the full brand when switching from compact to expanded', () => {
    preferences.setSidebar('compact');
    fixture.detectChanges();
    preferences.setSidebar('expanded');
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    const brandText = host.querySelector('.sidebar-brand-text') as HTMLElement;
    const sidebar = host.querySelector('.app-sidebar') as HTMLElement;
    expect(getComputedStyle(brandText).display).not.toBe('none');
    expect(getComputedStyle(sidebar).width).toBe('256px');
    expect(document.documentElement.classList.contains('sidebar-compact')).toBeFalse();
  });
});
