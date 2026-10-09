import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { PrDesignConformance } from './pr-design-conformance';
import { DesignPatternService } from '../../../services/design-pattern.service';
import { UiPreferencesService } from '../../../services/ui-preferences.service';
import { PatternConformance, PatternProfile } from '../../../models/design-pattern.model';

describe('PrDesignConformance', () => {
  let api: jasmine.SpyObj<DesignPatternService>;
  const profile: PatternProfile = {
    id: 7, projectId: 3, status: 'COMPLETED', progress: 'Completed',
    analyzedCommitSha: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', currentDefaultBranchSha: null,
    stale: null, defaultBranch: 'main', analyzedAt: null, javaFileCount: 4, failureMessage: null, occurrences: []
  };
  const checked: PatternConformance = {
    id: 11, pullRequestId: 19, profileId: 7, status: 'COMPLETED', progress: 'Completed',
    failureMessage: null, checkedHeadSha: 'bbbb', checkedBaseSha: 'aaaa', checkedAt: '2026-10-07T10:00:00',
    findings: [
      { patternType: 'STRATEGY', patternFamily: 'GOF', status: 'CONFORMS', confidence: 'HIGH',
        file: 'PushNotificationStrategy.java', line: 1, className: 'PushNotificationStrategy',
        rationale: 'New implementation extends Strategy.', recommendation: null, evidence: [] },
      { patternType: 'CONTROLLER_SERVICE_REPOSITORY', patternFamily: 'ARCHITECTURAL', status: 'POTENTIAL_DEVIATION', confidence: 'HIGH',
        file: 'UserController.java', line: 74, className: 'UserController',
        rationale: 'Controller directly depends on repository.', recommendation: 'Use service layer.', evidence: [] },
      { patternType: 'SINGLETON', patternFamily: 'GOF', status: 'NOT_APPLICABLE', confidence: 'HIGH',
        file: null, line: null, className: null, rationale: 'Unrelated PR.', recommendation: null, evidence: [] },
      { patternType: 'FACTORY_METHOD', patternFamily: 'GOF', status: 'INSUFFICIENT_EVIDENCE', confidence: 'MEDIUM',
        file: null, line: null, className: null, rationale: 'Baseline changed.', recommendation: null, evidence: [] }
    ]
  };

  beforeEach(async () => {
    api = jasmine.createSpyObj<DesignPatternService>('DesignPatternService', ['profileForPr', 'latestCheck', 'runCheck', 'generateDesignAdvice']);
    api.profileForPr.and.returnValue(of(profile));
    api.latestCheck.and.returnValue(of(null));
    api.runCheck.and.returnValue(of(checked));
    await TestBed.configureTestingModule({ imports: [PrDesignConformance],
      providers: [provideRouter([]), { provide: DesignPatternService, useValue: api }] }).compileComponents();
  });

  function render() {
    const fixture = TestBed.createComponent(PrDesignConformance);
    fixture.componentRef.setInput('prId', 19);
    fixture.detectChanges();
    return fixture;
  }

  it('offers exact-base analysis even when no default profile is available', () => {
    api.profileForPr.and.returnValue(of({ ...profile, id: null, status: 'NOT_ANALYZED' }));
    const fixture = render();
    expect(fixture.nativeElement.textContent).toContain('exact base commit');
    expect(fixture.nativeElement.textContent).not.toContain('How this review works');
    expect(fixture.nativeElement.textContent).toContain('No design check yet.');
    expect((fixture.nativeElement.querySelector('button:not(.info-control)') as HTMLButtonElement).disabled).toBeFalse();
  });

  it('shows saved conformance, potential deviation, not applicable and insufficient evidence without rerunning', () => {
    api.latestCheck.and.returnValue(of(checked));
    const fixture = render();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Conforms');
    expect(text).toContain('Potential deviations');
    expect(text).toContain('Not applicable:');
    expect(text).toContain('Insufficient evidence:');
    expect(fixture.nativeElement.querySelectorAll('.design-review-metric').length).toBe(4);
    expect(text).toContain('PushNotificationStrategy.java');
    expect(text).toContain('UserController.java');
    expect(text).toContain('Use service layer.');
    expect(api.runCheck).not.toHaveBeenCalled();
  });

  it('starts a check only on explicit action and displays empty results normally', () => {
    api.runCheck.and.returnValue(of({ ...checked, findings: [] }));
    const fixture = render();
    (fixture.nativeElement.querySelector('button:not(.info-control)') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(api.runCheck).toHaveBeenCalledOnceWith(19);
    expect(fixture.nativeElement.textContent).toContain('No supported repository pattern occurrences');
  });
  it('uses the backend overall summary and renders individual cards with evidence', () => {
    api.latestCheck.and.returnValue(of({ ...checked, summary: { overallStatus: 'POTENTIAL_DEVIATION', conforms: 1, potentialDeviations: 1, notApplicable: 1, insufficientEvidence: 1 } }));
    const fixture = render();
    expect(fixture.nativeElement.querySelector('[aria-label="Overall design status"]').textContent).toContain('Potential Deviation');
    expect(fixture.nativeElement.querySelectorAll('article').length).toBe(5);
    expect(fixture.nativeElement.querySelectorAll('article details').length).toBe(5);
  });
  for (const width of [320, 768, 1280]) {
    it(`keeps conformance cards readable within ${width}px in each theme`, () => {
      api.latestCheck.and.returnValue(of(checked)); const fixture = render();
      const host = fixture.nativeElement as HTMLElement; host.style.display = 'block'; host.style.width = `${width}px`;
      const preferences = TestBed.inject(UiPreferencesService);
      for (const theme of ['light', 'dark', 'system'] as const) {
        preferences.setTheme(theme);
        expect(host.scrollWidth).toBeLessThanOrEqual(host.clientWidth);
        const overall = host.querySelector('[aria-label="Overall design status"]') as HTMLElement;
        expect(getComputedStyle(overall).color).not.toBe(getComputedStyle(overall).backgroundColor);
      }
      preferences.setTheme('light'); fixture.destroy();
    });
  }
  it('keeps a new run visible when an older saved-check request finishes late', () => {
    const old = new Subject<PatternConformance | null>(); api.latestCheck.and.returnValue(old);
    const fixture = render(); fixture.componentInstance.run();
    old.next({ ...checked, id: 1 });
    expect(fixture.componentInstance.check()?.id).toBe(11);
  });
  it('disables duplicate checks while starting and while the saved job is running', () => {
    const response = new Subject<PatternConformance>(); api.runCheck.and.returnValue(response);
    const fixture = render(); fixture.componentInstance.run(); fixture.componentInstance.run(); fixture.detectChanges();
    expect(api.runCheck).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelector('button.design-action').disabled).toBeTrue();
    response.next({ ...checked, status: 'RUNNING' }); fixture.detectChanges();
    fixture.componentInstance.run(); expect(api.runCheck).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelector('button.design-action').disabled).toBeTrue();
    fixture.destroy();
  });
  it('shows safe baseline mismatch without offering unsupported matching-baseline analysis', () => {
    api.latestCheck.and.returnValue(of({ ...checked, findings: [{ ...checked.findings[0], patternType: 'REPOSITORY_BASELINE', status: 'INSUFFICIENT_EVIDENCE' }] }));
    const fixture = render();
    expect(fixture.nativeElement.querySelector('[aria-label="Baseline mismatch"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('prepare or reuse the exact PR-base profile');
    expect(fixture.nativeElement.querySelector('button[aria-label="About design conformance"]')).not.toBeNull();
  });
  it('displays exact-base progress and a safe failed-acquisition reason', () => {
    api.latestCheck.and.returnValue(of({ ...checked, status: 'RUNNING', progress: 'Preparing exact PR base design profile...' }));
    const fixture = render(); expect(fixture.nativeElement.textContent).toContain('Preparing exact PR base');
    fixture.componentInstance.check.set({ ...checked, status: 'FAILED', findings: [], failureMessage: 'A Java source file could not be parsed safely.' });
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('parsed safely');
    expect(fixture.nativeElement.querySelector('[aria-label="Overall design status"]')).toBeNull();
    fixture.destroy();
  });
  it('explains ordinary changes without claiming every PR needs GoF patterns', () => {
    api.latestCheck.and.returnValue(of({ ...checked, findings: [], changedJavaFileCount: 2 }));
    const fixture = render();
    expect(fixture.nativeElement.textContent).toContain('No applicable structural design change');
    expect(fixture.nativeElement.textContent).toContain('PRs are not required to contain GoF patterns');
    expect(fixture.nativeElement.textContent).toContain('Java files checked');
    expect(fixture.nativeElement.textContent).toContain('Relevant patterns');
  });
  it('keeps source evidence collapsed and shows design impact without numeric risk', () => {
    const f = { ...checked.findings[1], structuralChange: 'Added repository field', designImpact: 'Couples HTTP and persistence', reviewPriority: 'HIGH' as const };
    api.latestCheck.and.returnValue(of({ ...checked, findings: [f] }));
    const fixture = render();
    expect(fixture.nativeElement.textContent).toContain('Added repository field');
    expect(fixture.nativeElement.textContent).toContain('Couples HTTP and persistence');
    expect(fixture.nativeElement.textContent).toContain('High review priority');
    expect(fixture.nativeElement.querySelector('details').open).toBeFalse();
  });
  it('requests eligible design advice explicitly and retains the verdict on provider failure', () => {
    const f = { ...checked.findings[1], designImpact: 'Couples boundaries', evidence: [{ role: 'Repository', className: 'Store', sourcePath: 'Store.java', methodName: null, line: 1 }] };
    api.latestCheck.and.returnValue(of({ ...checked, findings: [f] }));
    const response = new Subject<any>(); api.generateDesignAdvice.and.returnValue(response);
    const fixture = render(); expect(api.generateDesignAdvice).not.toHaveBeenCalled();
    fixture.componentInstance.suggest(0); fixture.componentInstance.suggest(0);
    expect(api.generateDesignAdvice).toHaveBeenCalledOnceWith(19, 11, 0);
    response.next({ status: 'UNAVAILABLE', advice: null, message: 'AI provider request limit reached.', retryAfterSeconds: 60 });
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('AI provider request limit reached');
    expect(fixture.componentInstance.check()?.findings[0].status).toBe('POTENTIAL_DEVIATION');
    fixture.componentInstance.suggest(0); expect(api.generateDesignAdvice).toHaveBeenCalledTimes(1);
    fixture.destroy();
  });
  it('renders saved design guidance with Review note in collapsed Details and no regeneration', () => {
    const f = { ...checked.findings[1], designImpact: 'Couples boundaries', evidence: [{ role: 'Repository', className: 'Store', sourcePath: 'Store.java', methodName: null, line: 1 }],
      aiSuggestion: { summary: 'Saved summary', risk: 'Coupling', recommendation: 'Use service', verification: 'Test routing', limitations: 'Partial context', generatedAt: '2026-10-08T10:00:00', providerModel: 'provider-model', promptVersion: 'design-advice-v1' } };
    api.latestCheck.and.returnValue(of({ ...checked, findings: [f] }));
    const fixture = render(); expect(fixture.nativeElement.textContent).toContain('Saved summary');
    const details = fixture.nativeElement.querySelector('details[aria-label="AI advice details"]'); expect(details.open).toBeFalse();
    details.open = true; fixture.detectChanges(); expect(details.textContent).toContain('Review note: Partial context');
    fixture.componentInstance.suggest(0); expect(api.generateDesignAdvice).not.toHaveBeenCalled();
  });
  it('never offers AI for conforming, neutral, uncertain or legacy unsupported findings', () => {
    api.latestCheck.and.returnValue(of(checked)); const fixture = render();
    expect(fixture.nativeElement.textContent).not.toContain('✦ Generate AI Suggestion');
    expect(fixture.nativeElement.textContent).toContain('AI remediation is unavailable for these findings');
    for (let i = 0; i < checked.findings.length; i++) fixture.componentInstance.suggest(i);
    expect(api.generateDesignAdvice).not.toHaveBeenCalled();
  });
  it('presents a clean conforming review without inventing a remediation need', () => {
    api.latestCheck.and.returnValue(of({ ...checked, changedJavaFileCount: 8,
      findings: [checked.findings[0], checked.findings[2], { ...checked.findings[2], patternType: 'ADAPTER' }],
      summary: { overallStatus: 'CONFORMS', conforms: 1, potentialDeviations: 0, notApplicable: 2, insufficientEvidence: 0 } }));
    const fixture = render();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('.design-review-metric').length).toBe(4);
    expect(host.querySelectorAll('.design-review-finding-conforms').length).toBe(1);
    expect(host.querySelectorAll('.design-review-finding-neutral').length).toBe(2);
    expect(host.textContent).toContain('No design remediation needed for the detected findings');
    expect(host.textContent).toContain('Generate AI Suggestion appears only');
    expect(host.querySelector('button[aria-label="Generate AI Suggestion"]')).toBeNull();
    expect(api.generateDesignAdvice).not.toHaveBeenCalled();
    fixture.destroy();
  });
  it('ignores advice returned for an old check after a newer run is visible', () => {
    const f = { ...checked.findings[1], designImpact: 'Couples boundaries', evidence: [{ role: 'Repository', className: 'Store', sourcePath: 'Store.java', methodName: null, line: 1 }] };
    api.latestCheck.and.returnValue(of({ ...checked, findings: [f] }));
    const response = new Subject<any>(); api.generateDesignAdvice.and.returnValue(response);
    const fixture = render(); fixture.componentInstance.suggest(0);
    fixture.componentInstance.check.set({ ...checked, id: 12 });
    response.next({ status: 'UNAVAILABLE', message: 'Old failure', retryAfterSeconds: null });
    expect(fixture.componentInstance.check()?.id).toBe(12); fixture.destroy();
  });
  it('does not expose raw HTTP failure details', () => {
    api.runCheck.and.returnValue(throwError(() => ({ status: 500, error: { message: 'private provider stack' } })));
    const fixture = render(); fixture.componentInstance.run(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Could not start Pattern Check');
    expect(fixture.nativeElement.textContent).not.toContain('private provider stack');
  });
});
