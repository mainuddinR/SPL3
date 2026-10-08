import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
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
    api = jasmine.createSpyObj<DesignPatternService>('DesignPatternService', ['profileForPr', 'latestCheck', 'runCheck']);
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

  it('offers repository analysis when no profile is available', () => {
    api.profileForPr.and.returnValue(of({ ...profile, id: null, status: 'NOT_ANALYZED' }));
    const fixture = render();
    expect(fixture.nativeElement.textContent).toContain('Analyze Repository Design First');
    expect((fixture.nativeElement.querySelector('a') as HTMLAnchorElement).getAttribute('href')).toContain('/dashboard/projects/3/design-patterns');
    expect((fixture.nativeElement.querySelector('button:not(.info-control)') as HTMLButtonElement).disabled).toBeTrue();
  });

  it('shows saved conformance, potential deviation, not applicable and insufficient evidence without rerunning', () => {
    api.latestCheck.and.returnValue(of(checked));
    const fixture = render();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Conforms:');
    expect(text).toContain('Potential deviations:');
    expect(text).toContain('Not applicable:');
    expect(text).toContain('Insufficient evidence:');
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
    expect(fixture.nativeElement.querySelectorAll('article details').length).toBe(4);
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
    expect(fixture.nativeElement.textContent).not.toContain('Analyze matching baseline');
    expect(fixture.nativeElement.querySelector('button[aria-label="About design conformance"]')).not.toBeNull();
  });
});
