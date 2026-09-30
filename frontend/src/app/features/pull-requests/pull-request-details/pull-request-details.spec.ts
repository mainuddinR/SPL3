import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { AnalysisRunDetail, AnalysisRunSummary, PullRequest, SatdAnalysisResponse } from '../../../models/pull-request.model';
import { PullRequestService } from '../../../services/pull-request.service';
import { PullRequestDetails } from './pull-request-details';

describe('PullRequestDetails history', () => {
  let fixture: ComponentFixture<PullRequestDetails>;
  let component: PullRequestDetails;
  let history$: Subject<AnalysisRunSummary[]>;
  let detail$: Subject<AnalysisRunDetail>;
  let analysis$: Subject<SatdAnalysisResponse[]>;
  let service: jasmine.SpyObj<PullRequestService>;
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
  const pageText = () => fixture.nativeElement.textContent as string;
  const showHistory = () => { component.selectedTab.set('history'); fixture.detectChanges(); };

  beforeEach(async () => {
    history$ = new Subject();
    detail$ = new Subject();
    analysis$ = new Subject();
    service = jasmine.createSpyObj<PullRequestService>('PullRequestService', [
      'getPullRequest', 'getPullRequestFiles', 'getPullRequestComments',
      'getAnalysisRuns', 'getAnalysisRun', 'analyzePullRequest', 'syncPullRequestDetails'
    ]);
    service.getPullRequest.and.returnValue(of(pr));
    service.getPullRequestFiles.and.returnValue(of([]));
    service.getPullRequestComments.and.returnValue(of([]));
    service.getAnalysisRuns.and.callFake(() => history$.asObservable());
    service.getAnalysisRun.and.callFake(() => detail$.asObservable());
    service.analyzePullRequest.and.callFake(() => analysis$.asObservable());
    await TestBed.configureTestingModule({
      imports: [PullRequestDetails],
      providers: [
        { provide: PullRequestService, useValue: service },
        { provide: ActivatedRoute, useValue: { paramMap: params.asObservable() } },
        { provide: Router, useValue: {} }
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
    detail$.next({ run: saved, findings: [{ ...finding, label: 'SATD', debtCategory: 'DESIGN' }] });
    fixture.detectChanges();
    expect(pageText()).toContain('Saved historical result');
    expect(pageText()).toContain('// TODO: fix this');
    expect(pageText()).toContain('View saved surrounding code');
    expect(pageText()).not.toContain('View in diff');
  });

  it('refreshes history after successful live analysis and isolates refresh failure', () => {
    history$.next([run]);
    component.analyzePullRequest();
    analysis$.next([finding]);
    expect(service.getAnalysisRuns).toHaveBeenCalledTimes(2);
    history$.error(new Error('secret backend details'));
    fixture.detectChanges();
    expect(component.analysisResults()).toEqual([finding]);
    expect(component.analysisError()).toBeNull();
    expect(component.historyError()).toContain('Failed to load analysis history');
    expect(pageText()).not.toContain('secret backend details');
  });

  it('shows the downstream category only for live SATD', () => {
    analysis$.next([finding]); // No subscription yet.
    component.analyzePullRequest();
    analysis$.next([finding, { ...finding, label: 'NON-SATD', debtCategory: null, categoryReason: null, categoryRuleVersion: null }]);
    fixture.detectChanges();
    expect(pageText()).toContain('Debt category: Design');
    expect(pageText().match(/Debt category:/g)?.length).toBe(1);
  });

  it('distinguishes saved unclassified and legacy not-assessed categories', () => {
    const saved = { ...run, satdFindingCount: 1 };
    history$.next([saved]);
    showHistory();
    component.selectHistoryRun(saved);
    detail$.next({ run: saved, findings: [{ ...finding, label: 'SATD', debtCategory: 'UNCLASSIFIED', categoryReason: 'No explicit category evidence', categoryRuleVersion: 'category-rules-v1' }] });
    fixture.detectChanges();
    expect(pageText()).toContain('Debt category: Unclassified');
    component.selectHistoryRun(saved);
    component.selectHistoryRun(saved);
    detail$.next({ run: saved, findings: [{ ...finding, label: 'SATD', debtCategory: 'NOT_ASSESSED', categoryReason: null, categoryRuleVersion: null }] });
    fixture.detectChanges();
    expect(pageText()).toContain('Debt category: Not Assessed');
  });

  it('shows live severity only for SATD and distinguishes historical unavailable from legacy', () => {
    component.analyzePullRequest();
    analysis$.next([finding, { ...finding, label: 'NON-SATD', severityState: 'NOT_APPLICABLE', severityScore: null }]);
    fixture.detectChanges();
    expect(pageText()).toContain('Severity: 3 / 5');
    expect(pageText()).toContain('Method length: 51');
    expect(pageText().match(/Severity:/g)?.length).toBe(1);

    const saved = { ...run, satdFindingCount: 1 };
    history$.next([saved]);
    showHistory();
    component.selectHistoryRun(saved);
    detail$.next({ run: saved, findings: [{ ...finding, label: 'SATD', debtCategory: 'DESIGN', severityState: 'LEGACY', severityScore: null }] });
    fixture.detectChanges();
    expect(pageText()).toContain('Not assessed by this historical run');
  });
});
