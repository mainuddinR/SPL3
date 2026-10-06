import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { DashboardApiService } from '../../services/dashboard-api.service';
import { DashboardFindingDetail } from '../../models/dashboard.model';
import { FindingDetail } from './finding-detail';

describe('FindingDetail', () => {
  it('shows saved evidence and three distinct destinations', async () => {
    const item: DashboardFindingDetail = {
      repository: 'owner/repo', pullRequestId: 19, pullRequestNumber: 3,
      pullRequestTitle: 'Improve parser', runId: 23, completedAt: '2026-10-06T10:00:00',
      finding: { id: 70, filename: 'src/Main.java', lineNumber: 8, commentText: '// TODO refactor',
        precedingCode: 'void run() {', succeedingCode: '}', label: 'SATD', satdProbability: 0.9,
        nonSatdProbability: 0.1, confidence: 0.9, debtCategory: 'DESIGN', categoryReason: 'Design debt',
        categoryRuleVersion: 'category-v1', severityState: 'ASSESSED', severityScore: 3,
        severityReason: 'Method length', severityRuleVersion: 'severity-v1', methodLength: 42,
        methodComplexity: 5, methodMetricsRuleVersion: 'metrics-v1', riskEvidence: null,
        riskEvidenceRuleVersion: null, aiSuggestion: null, aiSuggestionGeneratedAt: null,
        aiSuggestionProviderModel: null, aiSuggestionPromptVersion: null }
    };
    const api = jasmine.createSpyObj<DashboardApiService>('DashboardApiService', ['findingDetail']);
    api.findingDetail.and.returnValue(of(item));
    await TestBed.configureTestingModule({ imports: [FindingDetail], providers: [
      provideRouter([]), { provide: DashboardApiService, useValue: api },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ findingId: '70' }) } } }
    ] }).compileComponents();
    const fixture = TestBed.createComponent(FindingDetail);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('// TODO refactor');
    const links = Array.from(fixture.nativeElement.querySelectorAll('a') as NodeListOf<HTMLAnchorElement>);
    expect(links.some(link => link.textContent?.includes('View PR') && link.href.endsWith('/dashboard/pulls/19'))).toBeTrue();
    expect(links.some(link => link.textContent?.includes('View in Diff') && link.href.includes('tab=files'))).toBeTrue();
    expect(links.some(link => link.textContent?.includes('View Analysis History') && link.href.includes('runId=23'))).toBeTrue();

    const storedSuggestion = 'Summary\nSimplify method.\n\nRisk\nRegression.\n\nRecommendation\nRefactor.\n\nVerification\nRun tests.\n\nLimitations\nCheck repository context.';
    fixture.componentInstance.detail.set({ ...item, finding: { ...item.finding,
      aiSuggestion: storedSuggestion, aiSuggestionGeneratedAt: '2026-10-06T10:02:00',
      aiSuggestionProviderModel: 'test-model', aiSuggestionPromptVersion: 'llm-suggestion-v1' } });
    fixture.detectChanges();
    const section = Array.from(fixture.nativeElement.querySelectorAll('section') as NodeListOf<HTMLElement>)
      .find(value => value.querySelector('h2')?.textContent === 'AI Suggestion') as HTMLElement;
    const cards = Array.from(section.querySelectorAll('h3')).map(value => value.textContent?.trim());
    expect(cards).toEqual(['Summary', 'Risk', 'Recommended remediation', 'Verification']);
    const details = section.querySelector('details') as HTMLDetailsElement;
    expect(details.open).toBeFalse();
    details.open = true;
    fixture.detectChanges();
    expect(details.textContent).toContain('Review note: Check repository context.');
    expect(fixture.componentInstance.detail()?.finding.aiSuggestion).toBe(storedSuggestion);
  });
});
