import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { RepoSummary } from './repo-summary';
import { UiPreferencesService } from '../../../../services/ui-preferences.service';

describe('Repository summary presentation', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [RepoSummary], providers: [provideRouter([]), provideHttpClient()] }));
  it('uses readable light/dark navigation and danger hover styles', () => {
    const rules = Array.from(document.styleSheets).flatMap(sheet => {
      try { return Array.from(sheet.cssRules); } catch { return []; }
    }).filter((rule): rule is CSSStyleRule => rule instanceof CSSStyleRule);
    for (const selector of ['.repository-action:hover', 'html.dark .repository-action:hover', '.repository-remove:hover', 'html.dark .repository-remove:hover']) {
      const rule = rules.find(rule => rule.selectorText === selector);
      expect(rule).withContext(selector).toBeDefined();
      const luminance = (color: string) => {
        const rgb = color.match(/\d+/g)!.slice(0, 3).map(value => Number(value) / 255)
          .map(value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4);
        return rgb[0] * .2126 + rgb[1] * .7152 + rgb[2] * .0722;
      };
      const foreground = luminance(rule!.style.color), background = luminance(rule!.style.backgroundColor);
      expect((Math.max(foreground, background) + .05) / (Math.min(foreground, background) + .05)).toBeGreaterThanOrEqual(4.5);
      expect(rule?.style.color).not.toBe(''); expect(rule?.style.backgroundColor).not.toBe('');
    }
  });
  for (const status of ['Strategy · Primary', 'Mixed patterns', 'Layered architecture · No dominant GoF', 'No supported GoF patterns', 'No Java source', 'Analysis failed']) {
    it(`shows the saved design summary ${status}`, () => {
      const fixture = TestBed.createComponent(RepoSummary);
      fixture.componentRef.setInput('repo', { id: 7, name: 'owner/repository', url: 'https://example.test',
        pullRequestCount: 0, analyzedPullRequestCount: 0, currentSatdFindingCount: 0,
        lastCompletedAnalysisAt: null, designProfileStatus: status }); fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain(status === 'Analysis failed' ? 'Failed' : status);
    });
  }
  for (const width of [320, 768, 1280]) {
    it(`keeps card actions within the card without overlap at ${width}px`, () => {
      const fixture = TestBed.createComponent(RepoSummary);
      fixture.componentRef.setInput('repo', { id: 7, name: 'owner/repository', url: 'https://example.test',
        pullRequestCount: 2, analyzedPullRequestCount: 1, currentSatdFindingCount: 3,
        lastCompletedAnalysisAt: null, designProfileStatus: 'No supported patterns' });
      const host = fixture.nativeElement as HTMLElement;
      host.style.display = 'block'; host.style.width = `${width}px`; fixture.detectChanges();
      const card = host.firstElementChild as HTMLElement;
      const buttons = [...host.querySelectorAll('a[ng-reflect-router-link], a[href^="/dashboard"], button')];
      const bounds = card.getBoundingClientRect();
      expect(card.scrollWidth).toBeLessThanOrEqual(card.clientWidth);
      const rectangles = buttons.map(button => button.getBoundingClientRect());
      for (const r of rectangles) { expect(r.left).toBeGreaterThanOrEqual(bounds.left); expect(r.right).toBeLessThanOrEqual(bounds.right); }
      for (let i = 0; i < rectangles.length; i++) for (let j = i + 1; j < rectangles.length; j++) {
        const a = rectangles[i], b = rectangles[j];
        expect(a.right <= b.left || b.right <= a.left || a.bottom <= b.top || b.bottom <= a.top).toBeTrue();
      }
      expect(host.textContent).toContain('No supported GoF patterns');
      const preferences = TestBed.inject(UiPreferencesService);
      preferences.setTheme('light'); const light = getComputedStyle(card).backgroundColor;
      preferences.setTheme('dark'); expect(getComputedStyle(card).backgroundColor).not.toBe(light);
      preferences.setTheme('system'); expect(preferences.theme()).toBe('system');
      preferences.setTheme('light'); fixture.destroy();
    });
  }
});
