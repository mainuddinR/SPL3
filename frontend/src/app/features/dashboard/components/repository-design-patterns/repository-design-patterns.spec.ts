import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { convertToParamMap, ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { RepositoryDesignPatterns } from './repository-design-patterns';
import { DesignPatternService } from '../../../../services/design-pattern.service';
import { PatternProfile, RepositoryDesignSummary } from '../../../../models/design-pattern.model';
import { UiPreferencesService } from '../../../../services/ui-preferences.service';

describe('RepositoryDesignPatterns', () => {
  let api: jasmine.SpyObj<DesignPatternService>;
  const profile: PatternProfile = {
    id: 7, projectId: 3, status: 'COMPLETED', progress: 'Completed',
    analyzedCommitSha: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    currentDefaultBranchSha: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', stale: true,
    defaultBranch: 'main', analyzedAt: '2026-10-07T10:00:00', javaFileCount: 4,
    failureMessage: null, occurrences: [{ id: 9, patternType: 'STRATEGY', patternFamily: 'GOF',
      confidence: 'HIGH', rationale: 'NotificationService delegates to NotificationStrategy.',
      confidenceReason: 'Multiple implementations and delegated call.',
      evidence: [{ role: 'Strategy', className: 'NotificationStrategy', sourcePath: 'src/NotificationStrategy.java', methodName: null, line: 1 }] }]
  };

  beforeEach(async () => {
    api = jasmine.createSpyObj<DesignPatternService>('DesignPatternService', ['profile', 'refreshProfile']);
    api.profile.and.returnValue(of(null));
    api.refreshProfile.and.returnValue(of(profile));
    await TestBed.configureTestingModule({
      imports: [RepositoryDesignPatterns],
      providers: [provideRouter([]), { provide: DesignPatternService, useValue: api },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ projectId: '3' }) } } }]
    }).compileComponents();
  });

  it('shows not-analyzed state and starts a profile only when requested', () => {
    const fixture = TestBed.createComponent(RepositoryDesignPatterns);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Repository design has not been analyzed yet');
    expect(api.refreshProfile).not.toHaveBeenCalled();
    (fixture.nativeElement.querySelector('button:not(.info-control)') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(api.refreshProfile).toHaveBeenCalledOnceWith(3);
    expect(fixture.nativeElement.textContent).toContain('Strategy');
  });

  it('shows commit, staleness, confidence and expandable source evidence', () => {
    api.profile.and.returnValue(of(profile));
    const fixture = TestBed.createComponent(RepositoryDesignPatterns);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Analyzed at commit');
    expect(fixture.nativeElement.textContent).toContain('may be outdated');
    expect(fixture.nativeElement.textContent).toContain('High confidence');
    const details = fixture.nativeElement.querySelector('article details') as HTMLDetailsElement;
    expect(details.open).toBeFalse();
    details.open = true;
    expect(details.textContent).toContain('src/NotificationStrategy.java');
  });

  it('treats no detected patterns as an ordinary completed result', () => {
    api.profile.and.returnValue(of({ ...profile, stale: false, occurrences: [] }));
    const fixture = TestBed.createComponent(RepositoryDesignPatterns);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No supported GoF pattern detected');
    expect(fixture.nativeElement.textContent).not.toContain('role="alert"');
  });

  it('distinguishes no Java source from analyzed Java without detections', () => {
    api.profile.and.returnValue(of({ ...profile, javaFileCount: 0, occurrences: [], diagnostics: null }));
    const fixture = TestBed.createComponent(RepositoryDesignPatterns); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Java design-pattern analysis is not applicable');
    expect(fixture.nativeElement.textContent).not.toContain('No supported GoF pattern detected');
  });

  it('shows safe exact failure metadata and diagnostic counts', () => {
    api.profile.and.returnValue(of({ ...profile, status: 'FAILED', occurrences: [], failureCode: 'TOO_MANY_JAVA_FILES',
      observed: 1135, limit: 250, failureMessage: 'Repository exceeds the safe Java-file count limit.' }));
    const fixture = TestBed.createComponent(RepositoryDesignPatterns); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Repository too large for pattern analysis');
    expect(fixture.nativeElement.textContent).toContain('1,135 Java files'); expect(fixture.nativeElement.textContent).toContain('250 files');
    expect((fixture.nativeElement.querySelector('[role="alert"] details') as HTMLDetailsElement).open).toBeFalse();
  });

  it('polls a running analysis and preserves Light, Dark and System preferences', fakeAsync(() => {
    api.profile.and.returnValues(of({ ...profile, status: 'RUNNING', progress: 'Parsing Java files...', occurrences: [] }), of(profile));
    const preferences = TestBed.inject(UiPreferencesService);
    const fixture = TestBed.createComponent(RepositoryDesignPatterns);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Parsing Java files...');
    expect(fixture.nativeElement.textContent).toContain('Analyzing repository...');
    expect((fixture.nativeElement.querySelector('button:not(.info-control)') as HTMLButtonElement).disabled).toBeTrue();
    fixture.componentInstance.refresh(); expect(api.refreshProfile).not.toHaveBeenCalled();
    preferences.setTheme('light');
    expect(document.documentElement.classList.contains('dark')).toBeFalse();
    preferences.setTheme('dark');
    expect(document.documentElement.classList.contains('dark')).toBeTrue();
    preferences.setTheme('system');
    expect(preferences.theme()).toBe('system');
    tick(1800); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Strategy');
    fixture.destroy(); preferences.setTheme('light');
  }));
  it('keeps all three keyboard-accessible info panels collapsed and renders every metric', () => {
    api.profile.and.returnValue(of({ ...profile, diagnostics: { javaFileCount: 4, typeCount: 7, interfaceCount: 2,
      abstractClassCount: 1, controllerCount: 1, serviceCount: 1, repositoryCount: 1, relationshipCount: 8, detectedPatternCount: 1 } }));
    const fixture = TestBed.createComponent(RepositoryDesignPatterns); fixture.detectChanges();
    for (const label of ['About repository design profile', 'About GoF patterns', 'About architectural style']) {
      const summary = fixture.nativeElement.querySelector(`button[aria-label="${label}"]`) as HTMLElement;
      expect(summary).not.toBeNull();
      expect(summary.getAttribute('aria-expanded')).toBe('false');
      summary.click(); fixture.detectChanges(); expect(summary.getAttribute('aria-expanded')).toBe('true');
    }
    expect(fixture.nativeElement.querySelectorAll('dl dd').length).toBe(9);
    expect(fixture.nativeElement.textContent).toContain('Decorator');
    expect(fixture.nativeElement.textContent).toContain('DTO / Mapper separation');
    expect(fixture.nativeElement.textContent).not.toContain('About this analysis');
  });
  const design: RepositoryDesignSummary = {
    architecturalStyle: 'Layered Spring Architecture', primaryGofPattern: 'STRATEGY', primaryPatternStatus: 'DOMINANT',
    secondaryGofPatterns: ['FACTORY_METHOD', 'BUILDER'], mixedPatternUsage: false, rankingExplanation: 'HIGH = 2; MEDIUM = 1; spread bonus.',
    patternSummaries: [
      { patternType: 'STRATEGY', occurrenceCount: 4, highConfidenceCount: 3, mediumConfidenceCount: 1, participatingClassCount: 12, packageCount: 3, sourceRootCount: 1, importanceScore: 9, classification: 'PRIMARY' },
      { patternType: 'FACTORY_METHOD', occurrenceCount: 2, highConfidenceCount: 2, mediumConfidenceCount: 0, participatingClassCount: 6, packageCount: 2, sourceRootCount: 1, importanceScore: 5, classification: 'SECONDARY' },
      { patternType: 'BUILDER', occurrenceCount: 1, highConfidenceCount: 1, mediumConfidenceCount: 0, participatingClassCount: 2, packageCount: 1, sourceRootCount: null, importanceScore: 2, classification: 'LOCALIZED' }
    ], architecturalConventionSummaries: [
      { patternType: 'DEPENDENCY_INJECTION', occurrenceCount: 18, highConfidenceCount: 18, mediumConfidenceCount: 0, participatingClassCount: 20, packageCount: 3, sourceRootCount: 1, importanceScore: 0, classification: 'SUPPORTING' }
    ]
  };
  it('renders primary, secondary, localized, architectural and coverage cards with expandable occurrences', () => {
    api.profile.and.returnValue(of({ ...profile, designSummary: design }));
    const fixture = TestBed.createComponent(RepositoryDesignPatterns); fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('.design-pattern-card').length).toBe(3);
    expect(host.querySelector('.design-profile-primary')?.textContent).toContain('Strategy');
    for (const text of ['Secondary', 'Localized', 'Layered Spring Architecture', 'Analysis Coverage', '12 participating classes', '3 packages']) expect(host.textContent).toContain(text);
    const occurrences = Array.from(host.querySelectorAll<HTMLDetailsElement>('.design-profile-primary details')).find(d => d.querySelector('summary')?.textContent?.startsWith('View occurrences'))!;
    expect(occurrences.open).toBeFalse(); occurrences.open = true;
    expect(occurrences.textContent).toContain('Strategy #1'); expect(occurrences.textContent).toContain('src/NotificationStrategy.java');
    expect(host.querySelector('button[aria-label="About primary pattern"]')).not.toBeNull();
  });
  for (const state of ['MIXED', 'LOCALIZED', 'NO_GOF'] as const) {
    it(`renders a meaningful ${state} profile without inventing a primary`, () => {
      api.profile.and.returnValue(of({ ...profile, designSummary: { ...design, primaryGofPattern: null,
        primaryPatternStatus: state, mixedPatternUsage: state === 'MIXED',
        patternSummaries: state === 'NO_GOF' ? [] : design.patternSummaries.map(p => ({ ...p, classification: 'LOCALIZED' as const })) } }));
      const fixture = TestBed.createComponent(RepositoryDesignPatterns); fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.design-profile-primary')).toBeNull();
      expect(fixture.nativeElement.textContent).toContain(state === 'MIXED' ? 'Mixed GoF Design' : state === 'LOCALIZED' ? 'No repository-wide dominant' : 'No supported GoF pattern detected');
      expect(fixture.nativeElement.textContent).toContain('Layered Spring Architecture');
    });
  }
  for (const width of [320, 768, 1280]) {
    it(`keeps design cards within ${width}px in light, dark and system modes`, () => {
      api.profile.and.returnValue(of({ ...profile, designSummary: design }));
      const fixture = TestBed.createComponent(RepositoryDesignPatterns); const host = fixture.nativeElement as HTMLElement;
      host.style.display = 'block'; host.style.width = `${width}px`; fixture.detectChanges();
      const preferences = TestBed.inject(UiPreferencesService);
      for (const theme of ['light', 'dark', 'system'] as const) {
        preferences.setTheme(theme);
        expect(host.scrollWidth).toBeLessThanOrEqual(host.clientWidth);
        for (const card of Array.from(host.querySelectorAll('.design-pattern-card'))) {
          const bounds = card.getBoundingClientRect(); expect(bounds.right).toBeLessThanOrEqual(host.getBoundingClientRect().right);
        }
      }
      preferences.setTheme('light'); fixture.destroy();
    });
  }
});
