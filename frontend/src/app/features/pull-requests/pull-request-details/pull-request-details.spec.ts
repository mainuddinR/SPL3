import { DesignPatternService } from '../../../services/design-pattern.service';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { AnalysisRunDetail, AnalysisRunSummary, PullRequest, SatdAnalysisResponse, SavedSatdFinding, SuggestionResponse, ReviewPreview, AnalysisProgress } from '../../../models/pull-request.model';
import { PullRequestService } from '../../../services/pull-request.service';
import { SettingsApiService } from '../../../services/settings-api.service';
import { PullRequestDetails } from './pull-request-details';

describe('PullRequestDetails history', () => {
  let fixture: ComponentFixture<PullRequestDetails>;
  let component: PullRequestDetails;
  let history$: Subject<AnalysisRunSummary[]>;
  let detail$: Subject<AnalysisRunDetail>;
  let analysis$: Subject<AnalysisProgress>;
  let suggestion$: Subject<SuggestionResponse>;
  let preview$: Subject<ReviewPreview>;
  let publish$: Subject<ReviewPreview>;
  let service: jasmine.SpyObj<PullRequestService>;
  let settingsApi: jasmine.SpyObj<SettingsApiService>;
  const params = new BehaviorSubject(convertToParamMap({ prId: '19' }));
  const pr: PullRequest = {
    id: 19, githubPrId: 123, number: 123, title: 'Test PR', state: 'open', htmlUrl: '',
    body: '', createdAt: '2026-09-24T10:00:00', updatedAt: '2026-09-24T10:00:00'
  };
  const run: AnalysisRunSummary = {
    id: 3, status: 'COMPLETED', startedAt: '2026-09-24T10:00:00',
    completedAt: '2026-09-24T10:01:00', analyzedCandidateCount: 7, satdFindingCount: 0
  };
  const finding: SatdAnalysisResponse = {
    filename: 'src/Main.java', lineNumber: 2, commentText: '// TODO: fix this',
    precedingCode: 'class Main {', succeedingCode: 'int x;', label: 'SATD',
    satdProbability: 0.9, nonSatdProbability: 0.1, confidence: 0.9,
    debtCategory: 'DESIGN', categoryReason: 'Explicit design debt', categoryRuleVersion: 'category-rules-v1',
    severityState: 'ASSESSED', severityScore: 3, severityReason: 'Elevated method metric',
    severityRuleVersion: 'severity-rules-v1', methodLength: 51, methodComplexity: 5,
    methodMetricsRuleVersion: 'method-metrics-v1', riskEvidence: null, riskEvidenceRuleVersion: null
  };
  const savedFinding: SavedSatdFinding = { ...finding, id: 12, label: 'SATD', debtCategory: 'DESIGN',
    aiSuggestion: null, aiSuggestionGeneratedAt: null, aiSuggestionProviderModel: null, aiSuggestionPromptVersion: null };
  const review: ReviewPreview = { runId: 3, repository: 'owner/repo', pullRequestNumber: 123,
    analyzedCandidateCount: 7, satdFindingCount: 0, inlineCommentCount: 0, unmappableCount: 0,
    staleRevision: false, publicationStatus: 'NOT_PUBLISHED', summary: '7 candidates, 0 SATD findings',
    inlineComments: [], githubReviewUrl: null, reviewPublishedAt: null, publicationFailure: null };
  const pageText = () => fixture.nativeElement.textContent as string;
  const progress = (results: SatdAnalysisResponse[]): AnalysisProgress => ({
    runId: 3, status: 'COMPLETED', totalCandidates: results.length,
    processedCandidates: results.length, satdCount: results.filter(item => item.label === 'SATD').length,
    nonSatdCount: results.filter(item => item.label !== 'SATD').length,
    progressPercent: 100, nextCursor: results.length, completedAt: '2026-09-24T10:01:00',
    failureMessage: null, results
  });
  const showHistory = () => { component.selectedTab.set('history'); fixture.detectChanges(); };

  beforeEach(async () => {
    params.next(convertToParamMap({ prId: '19' }));
    history$ = new Subject();
    detail$ = new Subject();
    analysis$ = new Subject();
    suggestion$ = new Subject();
    preview$ = new Subject();
    publish$ = new Subject();
    service = jasmine.createSpyObj<PullRequestService>('PullRequestService', [
      'getPullRequest', 'getPullRequestFiles', 'getPullRequestComments',
      'getAnalysisRuns', 'getAnalysisRun', 'analyzePullRequest', 'startProgressiveAnalysis',
      'currentProgressiveAnalysis', 'analysisProgress', 'syncPullRequestDetails', 'generateSuggestion',
      'previewReview', 'publishReview'
    ]);
    settingsApi = jasmine.createSpyObj<SettingsApiService>('SettingsApiService', ['capabilities']);
    settingsApi.capabilities.and.returnValue(of({ llmEnabled: true, apiKeyConfigured: true, model: 'test-model' }));
    service.getPullRequest.and.returnValue(of(pr));
    service.getPullRequestFiles.and.returnValue(of([]));
    service.getPullRequestComments.and.returnValue(of([]));
    service.getAnalysisRuns.and.callFake(() => history$.asObservable());
    service.getAnalysisRun.and.callFake(() => detail$.asObservable());
    service.startProgressiveAnalysis.and.callFake(() => analysis$.asObservable());
    service.currentProgressiveAnalysis.and.returnValue(of(null));
    service.generateSuggestion.and.callFake(() => suggestion$.asObservable());
    service.previewReview.and.callFake(() => preview$.asObservable());
    service.publishReview.and.callFake(() => publish$.asObservable());
    await TestBed.configureTestingModule({
      imports: [PullRequestDetails],
      providers: [
        { provide: DesignPatternService, useValue: { profileForPr: () => of(null), latestCheck: () => of(null), runCheck: jasmine.createSpy() } },
        { provide: PullRequestService, useValue: service },
        { provide: SettingsApiService, useValue: settingsApi },
        { provide: ActivatedRoute, useValue: { paramMap: params.asObservable(), snapshot: { queryParamMap: convertToParamMap({}) } } },
        { provide: Router, useValue: { navigate: jasmine.createSpy().and.returnValue(Promise.resolve(true)) } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(PullRequestDetails);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => fixture.destroy());

  it('loads history and shows the empty state', () => {
    showHistory();
    expect(pageText()).toContain('Loading analysis history');
    history$.next([]);
    fixture.detectChanges();
    expect(pageText()).toContain('No previous analysis runs.');
  });

  it('keeps candidate and SATD counts distinct for a completed run', () => {
    history$.next([run]);
    showHistory();
    expect(pageText()).toContain('Analyzed candidates: 7');
    expect(pageText()).toContain('SATD findings: 0');
    component.selectHistoryRun(run);
    fixture.detectChanges();
    expect(pageText()).toContain('Loading saved run');
    detail$.next({ run, findings: [] });
    fixture.detectChanges();
    expect(pageText()).toContain('No SATD findings were detected in this analysis run.');
  });

  it('distinguishes zero candidates and failed runs', () => {
    const zero = { ...run, analyzedCandidateCount: 0 };
    const failed: AnalysisRunSummary = { ...run, id: 4, status: 'FAILED', analyzedCandidateCount: null };
    history$.next([zero, failed]);
    showHistory();
    expect(pageText()).toContain('This analysis run failed.');
    component.selectHistoryRun(zero);
    detail$.next({ run: zero, findings: [] });
    fixture.detectChanges();
    expect(pageText()).toContain('No analyzable candidates were found in this run.');
    expect(pageText()).not.toContain('No SATD findings were detected in this analysis run.');
  });

  it('shows saved findings without historical diff navigation', () => {
    const saved = { ...run, satdFindingCount: 1 };
    history$.next([saved]);
    showHistory();
    component.selectHistoryRun(saved);
    detail$.next({ run: saved, findings: [savedFinding] });
    fixture.detectChanges();
    expect(pageText()).toContain('Saved historical result');
    expect(pageText()).toContain('// TODO: fix this');
    expect(pageText()).toContain('View saved surrounding code');
    expect(pageText()).not.toContain('View in diff');
  });

  it('refreshes history after successful live analysis and isolates refresh failure', () => {
    history$.next([run]);
    component.analyzePullRequest();
    analysis$.next(progress([finding]));
    expect(service.getAnalysisRuns).toHaveBeenCalledTimes(2);
    history$.error(new Error('secret backend details'));
    fixture.detectChanges();
    expect(component.analysisResults()).toEqual([finding]);
    expect(component.analysisError()).toBeNull();
    expect(component.historyError()).toContain('Failed to load analysis history');
    expect(pageText()).not.toContain('secret backend details');
  });

  it('shows the downstream category only for live SATD', () => {
    analysis$.next(progress([finding])); // No subscription yet.
    component.analyzePullRequest();
    analysis$.next(progress([finding, { ...finding, label: 'NON-SATD', debtCategory: null, categoryReason: null, categoryRuleVersion: null }]));
    fixture.detectChanges();
    expect(pageText()).toContain('Debt category: Design');
    expect(pageText().match(/Debt category:/g)?.length).toBe(1);
  });

  it('distinguishes saved unclassified and legacy not-assessed categories', () => {
    const saved = { ...run, satdFindingCount: 1 };
    history$.next([saved]);
    showHistory();
    component.selectHistoryRun(saved);
    detail$.next({ run: saved, findings: [{ ...savedFinding, debtCategory: 'UNCLASSIFIED', categoryReason: 'No explicit category evidence', categoryRuleVersion: 'category-rules-v1' }] });
    fixture.detectChanges();
    expect(pageText()).toContain('Debt category: Unclassified');
    component.selectHistoryRun(saved);
    component.selectHistoryRun(saved);
    detail$.next({ run: saved, findings: [{ ...savedFinding, debtCategory: 'NOT_ASSESSED', categoryReason: null, categoryRuleVersion: null }] });
    fixture.detectChanges();
    expect(pageText()).toContain('Debt category: Not Assessed');
  });

  it('shows live severity only for SATD and distinguishes historical unavailable from legacy', () => {
    component.analyzePullRequest();
    analysis$.next(progress([finding, { ...finding, label: 'NON-SATD', severityState: 'NOT_APPLICABLE', severityScore: null }]));
    fixture.detectChanges();
    expect(pageText()).toContain('Severity: 3 / 5');
    expect(pageText()).toContain('Method length: 51');
    expect(pageText().match(/Severity:/g)?.length).toBe(1);

    const saved = { ...run, satdFindingCount: 1 };
    history$.next([saved]);
    showHistory();
    component.selectHistoryRun(saved);
    detail$.next({ run: saved, findings: [{ ...savedFinding, severityState: 'LEGACY', severityScore: null }] });
    fixture.detectChanges();
    expect(pageText()).toContain('Not assessed by this historical run');
  });

  it('appends cursor results and completes without duplicate polling', fakeAsync(() => {
    const first = { ...progress([finding]), status: 'RUNNING' as const, totalCandidates: 2,
      processedCandidates: 1, progressPercent: 50, completedAt: null };
    const last = { ...progress([finding]), status: 'COMPLETED' as const, totalCandidates: 2,
      processedCandidates: 2, satdCount: 1, nonSatdCount: 1, nextCursor: 2,
      results: [{ ...finding, label: 'NON-SATD' as const }] };
    service.analysisProgress.and.returnValues(of(first), of(last));
    component.analyzePullRequest();
    analysis$.next({ ...progress([]), status: 'RUNNING', totalCandidates: 2, progressPercent: 0, completedAt: null });
    fixture.detectChanges();
    expect(pageText()).toContain('0 / 2 comments analyzed');
    tick(1200); fixture.detectChanges();
    expect(component.analysisResults()?.length).toBe(1);
    expect(pageText()).toContain('50%');
    tick(1200); fixture.detectChanges();
    expect(component.analysisResults()?.length).toBe(2);
    expect(pageText()).toContain('Analysis complete');
    tick(2400);
    expect(service.analysisProgress).toHaveBeenCalledTimes(2);
  }));

  it('resumes a running job on return and stops polling after component cleanup', fakeAsync(() => {
    const running = { ...progress([finding]), status: 'RUNNING' as const,
      totalCandidates: 2, processedCandidates: 1, progressPercent: 50, completedAt: null };
    service.currentProgressiveAnalysis.and.returnValue(of(running));
    service.analysisProgress.and.returnValue(of(running));
    params.next(convertToParamMap({ prId: '19' }));
    fixture.detectChanges();
    expect(component.analyzing()).toBeTrue();
    expect(component.analysisResults()?.length).toBe(1);
    fixture.destroy();
    tick(2400);
    expect(service.analysisProgress).not.toHaveBeenCalled();
  }));

  it('opens the completed live finding in its saved History run', () => {
    component.analyzePullRequest();
    analysis$.next(progress([finding]));
    history$.next([{ ...run, satdFindingCount: 1 }]);
    component.openAnalysisHistory(finding);
    detail$.next({ run, findings: [savedFinding] });
    fixture.detectChanges();
    expect(component.selectedTab()).toBe('history');
    expect(component.selectedHistory()?.run.id).toBe(3);
    expect(pageText()).toContain('AI Remediation Assistant');
  });

  it('holds a rate-limited finding until Retry-After expires without automatic retry', fakeAsync(() => {
    history$.next([{ ...run, satdFindingCount: 1 }]); showHistory();
    component.selectHistoryRun(run);
    detail$.next({ run, findings: [savedFinding] });
    component.generateSuggestion(savedFinding);
    suggestion$.next({ findingId: 12, status: 'UNAVAILABLE', suggestion: null, generatedAt: null,
      providerModel: null, promptVersion: null, reasonCode: 'RATE_LIMITED', reason: 'private body', retryAfterSeconds: 2 });
    fixture.detectChanges();
    expect(component.coolingDown(12)).toBeTrue();
    expect(pageText()).toContain('Try again in about 2 seconds');
    component.generateSuggestion(savedFinding);
    expect(service.generateSuggestion).toHaveBeenCalledTimes(1);
    tick(2000); fixture.detectChanges();
    expect(component.coolingDown(12)).toBeFalse();
    expect(pageText()).toContain('Retry');
    expect(service.generateSuggestion).toHaveBeenCalledTimes(1);
  }));

  it('generates only after explicit action and updates the saved finding', () => {
    history$.next([{ ...run, satdFindingCount: 1 }]);
    showHistory();
    component.selectHistoryRun(run);
    detail$.next({ run, findings: [savedFinding] });
    fixture.detectChanges();
    expect(pageText()).toContain('configured AI provider');
    expect(pageText()).toContain('limited saved code excerpt');
    expect(service.generateSuggestion).not.toHaveBeenCalled();
    component.generateSuggestion(savedFinding);
    component.generateSuggestion(savedFinding);
    expect(service.generateSuggestion).toHaveBeenCalledOnceWith(19, 3, 12);
    fixture.detectChanges();
    expect(pageText()).toContain('Generating suggestion...');
    suggestion$.next({ findingId: 12, status: 'AVAILABLE', suggestion: 'Summary\nReview this', generatedAt: '2026-09-24T10:02:00', providerModel: 'test-model', promptVersion: 'llm-suggestion-v1', reasonCode: null, reason: null });
    fixture.detectChanges();
    expect(pageText()).toContain('Review this');
    expect(pageText()).not.toContain('Generate AI Suggestion');
  });

  it('shows a disabled provider response safely and rejects ineligible UI actions', () => {
    history$.next([{ ...run, satdFindingCount: 1 }]);
    showHistory();
    component.selectHistoryRun(run);
    detail$.next({ run, findings: [savedFinding] });
    component.selectedHistory.update(detail => detail && ({ ...detail, run: { ...detail.run, status: 'FAILED' } }));
    component.generateSuggestion(savedFinding);
    expect(service.generateSuggestion).not.toHaveBeenCalled();
    component.selectedHistory.update(detail => detail && ({ ...detail, run }));
    component.generateSuggestion(savedFinding);
    suggestion$.next({ findingId: 12, status: 'UNAVAILABLE', suggestion: null, generatedAt: null,
      providerModel: null, promptVersion: null, reasonCode: 'DISABLED', reason: 'LLM integration disabled.' });
    fixture.detectChanges();
    expect(pageText()).toContain('AI suggestions are disabled by the server.');
    expect(pageText()).toContain('Retry');
    expect(pageText()).not.toContain('allowlist');
  });

  it('maps safe provider reason codes without rendering raw provider text', () => {
    history$.next([{ ...run, satdFindingCount: 1 }]); showHistory();
    component.selectHistoryRun(run);
    detail$.next({ run, findings: [savedFinding] });
    const cases: [string, string][] = [
      ['AUTHENTICATION_FAILED', 'AI provider authentication failed.'],
      ['RATE_LIMITED', 'AI provider request limit reached.'],
      ['MODEL_UNAVAILABLE', 'The configured AI model is unavailable.'],
      ['TIMEOUT', 'The AI provider took too long to respond.'],
      ['PROVIDER_UNAVAILABLE', 'AI provider is temporarily unavailable.'],
      ['INVALID_PROVIDER_RESPONSE', 'AI provider returned an invalid response.']
    ];
    for (const [reasonCode, expected] of cases) {
      component.generateSuggestion(savedFinding);
      suggestion$.next({ findingId: 12, status: 'UNAVAILABLE', suggestion: null, generatedAt: null,
        providerModel: null, promptVersion: null, reasonCode, reason: 'private raw provider response' });
      fixture.detectChanges();
      expect(pageText()).toContain(expected);
      expect(pageText()).not.toContain('private raw provider response');
    }
  });

  it('explains disabled and unconfigured capability without a provider request', () => {
    history$.next([{ ...run, satdFindingCount: 1 }]); showHistory();
    component.selectHistoryRun(run);
    detail$.next({ run, findings: [savedFinding] });
    component.suggestionCapability.set({ llmEnabled: false, apiKeyConfigured: true, model: 'test-model' });
    fixture.detectChanges();
    expect(pageText()).toContain('AI suggestions are currently disabled by the server.');
    const action = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find(button => button.textContent?.includes('Generate AI Suggestion'));
    expect(action?.disabled).toBeTrue();
    component.generateSuggestion(savedFinding);
    expect(service.generateSuggestion).not.toHaveBeenCalled();
    component.suggestionCapability.set({ llmEnabled: true, apiKeyConfigured: false, model: 'test-model' });
    fixture.detectChanges();
    expect(pageText()).toContain('AI provider is not configured.');
    expect(service.generateSuggestion).not.toHaveBeenCalled();
  });

  it('renders structured saved advice immediately with metadata', () => {
    const storedSuggestion = 'Summary\nSimplify method.\n\nRisk\nRegression.\n\nRecommendation\nRefactor.\n\nVerification\nRun tests.\n\nLimitations\nReview context.';
    history$.next([{ ...run, satdFindingCount: 1 }]); showHistory();
    component.selectHistoryRun(run);
    detail$.next({ run, findings: [{ ...savedFinding,
      aiSuggestion: storedSuggestion,
      aiSuggestionProviderModel: 'test-model', aiSuggestionGeneratedAt: '2026-09-24T10:02:00',
      aiSuggestionPromptVersion: 'llm-suggestion-v1' }] });
    fixture.detectChanges();
    expect(pageText()).toContain('AI Remediation Assistant');
    expect(pageText()).toContain('AI-assisted remediation guidance');
    const assistant = fixture.nativeElement.querySelector('[aria-label="AI Remediation Assistant"]') as HTMLElement;
    const cards = Array.from(assistant.querySelectorAll('h5')).map(node => node.textContent?.trim());
    expect(cards).toEqual(['Summary', 'Risk', 'Recommended remediation', 'Verification']);
    expect(cards).not.toContain('Limitations');
    const details = assistant.querySelector('details') as HTMLDetailsElement;
    expect(details.open).toBeFalse();
    expect(details.textContent).toContain('Model: test-model');
    expect(details.textContent).toContain('Prompt version: llm-suggestion-v1');
    details.open = true;
    fixture.detectChanges();
    expect(details.textContent).toContain('Review note: Review context.');
    expect(component.selectedHistory()?.findings[0].aiSuggestion).toBe(storedSuggestion);
    expect(pageText()).not.toContain('Generate AI Suggestion');
    expect(service.generateSuggestion).not.toHaveBeenCalled();
  });

  it('shows existing advice without a request and keeps provider failures safe', () => {
    history$.next([{ ...run, satdFindingCount: 1 }]);
    showHistory();
    component.selectHistoryRun(run);
    detail$.next({ run, findings: [{ ...savedFinding, aiSuggestion: 'Saved human-reviewed advice' }] });
    fixture.detectChanges();
    expect(pageText()).toContain('Saved human-reviewed advice');
    expect(pageText()).not.toContain('Generate AI Suggestion');
    expect(service.generateSuggestion).not.toHaveBeenCalled();

    component.selectHistoryRun(run);
    component.selectHistoryRun(run);
    detail$.next({ run, findings: [savedFinding] });
    component.generateSuggestion(savedFinding);
    suggestion$.error(new Error('private provider error'));
    fixture.detectChanges();
    expect(pageText()).toContain('Suggestion unavailable. Retry.');
    expect(pageText()).not.toContain('private provider error');
  });
  it('previews a zero-SATD review and requires a second confirmation action', () => {
    history$.next([run]); showHistory(); component.selectHistoryRun(run);
    detail$.next({ run, findings: [] });
    component.previewGitHubReview(); fixture.detectChanges();
    expect(pageText()).toContain('Loading review preview');
    expect(service.publishReview).not.toHaveBeenCalled();
    preview$.next(review); fixture.detectChanges();
    expect(pageText()).toContain('0 SATD findings');
    expect(pageText()).toContain('Confirm and Publish Review');
    component.publishGitHubReview(); fixture.detectChanges();
    expect(pageText()).toContain('Publishing...');
    publish$.next({ ...review, publicationStatus: 'PUBLISHED',
      githubReviewUrl: 'https://github.com/owner/repo/pull/123#pullrequestreview-1' });
    fixture.detectChanges();
    expect(pageText()).toContain('Published to GitHub');
    expect(pageText()).not.toContain('Confirm and Publish Review');
  });

  it('blocks stale previews and shows a safe provider error', () => {
    history$.next([run]); showHistory(); component.selectHistoryRun(run);
    detail$.next({ run, findings: [] });
    component.previewGitHubReview();
    preview$.next({ ...review, staleRevision: true }); fixture.detectChanges();
    expect(pageText()).toContain('PR revision changed');
    component.publishGitHubReview();
    expect(service.publishReview).not.toHaveBeenCalled();
    component.previewGitHubReview();
    preview$.error(new Error('provider secret'));
    fixture.detectChanges();
    expect(pageText()).toContain('Review preview unavailable');
    expect(pageText()).not.toContain('provider secret');
  });
  it('opens and focuses Design Check without starting a backend check', async () => {
    const button = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(b => b.textContent?.includes('Design Check'))!;
    expect(button).toBeDefined(); button.click(); fixture.detectChanges(); await fixture.whenStable();
    expect(component.selectedTab()).toBe('design');
    const area = fixture.nativeElement.querySelector('#pr-design-area');
    expect(area).not.toBeNull(); expect(document.activeElement).toBe(area);
    button.click(); fixture.detectChanges(); await fixture.whenStable();
    expect(TestBed.inject(DesignPatternService).runCheck).not.toHaveBeenCalled();
  });
  for (const width of [320, 768, 1280]) {
    it(`wraps the three PR header actions without overlap at ${width}px`, () => {
      const host = fixture.nativeElement as HTMLElement;
      host.style.width = `${width}px`; host.style.display = 'block';
      const actions = Array.from(host.querySelectorAll<HTMLButtonElement>('button.design-action'));
      expect(actions.length).toBe(3);
      const boxes = actions.map(button => button.getBoundingClientRect());
      const container = actions[0].parentElement!.getBoundingClientRect();
      for (const box of boxes) {
        expect(box.left).toBeGreaterThanOrEqual(container.left);
        expect(box.right).toBeLessThanOrEqual(container.right + 1);
      }
      for (let i = 0; i < boxes.length; i++) for (let j = i + 1; j < boxes.length; j++) {
        expect(boxes[i].right <= boxes[j].left || boxes[j].right <= boxes[i].left
          || boxes[i].bottom <= boxes[j].top || boxes[j].bottom <= boxes[i].top).toBeTrue();
      }
    });
  }
});
