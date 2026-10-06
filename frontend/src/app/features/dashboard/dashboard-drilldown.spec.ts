import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { DashboardApiService } from '../../services/dashboard-api.service';
import { UiPreferencesService } from '../../services/ui-preferences.service';
import { DashboardFindingRow, DashboardPage } from '../../models/dashboard.model';
import { DashboardDrilldown } from './dashboard-drilldown';

describe('DashboardDrilldown', () => {
  const finding: DashboardFindingRow = { id: 21, repositoryId: 7, repository: 'owner/repo',
    pullRequestId: 19, pullRequestNumber: 5, pullRequestTitle: 'Fix parser', runId: 11,
    filename: 'src/Main.java', lineNumber: 8, commentText: '// TODO debt',
    satdProbability: 0.9, confidence: 0.9, debtCategory: 'DESIGN', categoryReason: 'Design debt',
    severityScore: 3, severityReason: 'Method evidence', aiSuggestionAvailable: true,
    completedAt: '2026-10-01T10:00:00' };
  let api: jasmine.SpyObj<DashboardApiService>;
  let route: { snapshot: { data: { mode: string } } };
  let results: Subject<DashboardPage<DashboardFindingRow>>;

  beforeEach(async () => {
    results = new Subject();
    api = jasmine.createSpyObj<DashboardApiService>('DashboardApiService',
      ['summary', 'repositories', 'pullRequests', 'analyzed', 'findings']);
    api.summary.and.returnValue(of({ stats: { repositoryCount: 1, pullRequestCount: 1,
      analyzedPullRequestCount: 1, currentSatdFindingCount: 1, latestAnalysisAt: null },
      categoryDistribution: {}, severityDistribution: {}, repositories: [{ id: 7, name: 'owner/repo',
        url: '', pullRequestCount: 1, analyzedPullRequestCount: 1, currentSatdFindingCount: 1,
        lastCompletedAnalysisAt: null }] }));
    api.findings.and.returnValue(results.asObservable());
    api.repositories.and.returnValue(of({ items: [], total: 0, page: 1, size: 10 }));
    api.pullRequests.and.returnValue(of({ items: [], total: 0, page: 1, size: 10 }));
    api.analyzed.and.returnValue(of({ items: [], total: 0, page: 1, size: 10 }));
    route = { snapshot: { data: { mode: 'findings' } } };
    await TestBed.configureTestingModule({ imports: [DashboardDrilldown], providers: [
      provideRouter([]), { provide: DashboardApiService, useValue: api },
      { provide: ActivatedRoute, useValue: route },
      { provide: UiPreferencesService, useValue: { pageSize: () => 10 } }
    ] }).compileComponents();
  });

  it('shows loading, current finding count, saved evidence, and history navigation', () => {
    const fixture = TestBed.createComponent(DashboardDrilldown);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[aria-label="Loading details"]')).toBeTruthy();
    results.next({ items: [finding], total: 1, page: 1, size: 10 });
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('1 findings');
    expect(text).toContain('// TODO debt');
    expect(text).toContain('AI Suggestion Available');
    const links = Array.from(fixture.nativeElement.querySelectorAll('a') as NodeListOf<HTMLAnchorElement>);
    expect(links.some(link => link.textContent?.includes('View Finding') && link.href.endsWith('/dashboard/findings/21'))).toBeTrue();
    expect(links.some(link => link.textContent?.includes('View Analysis History') && link.href.includes('tab=history'))).toBeTrue();
  });

  it('sends filters to the paged endpoint and shows a truthful empty state', () => {
    const fixture = TestBed.createComponent(DashboardDrilldown);
    fixture.detectChanges();
    results.next({ items: [finding], total: 1, page: 1, size: 10 });
    fixture.detectChanges();
    fixture.componentInstance.changeCategory({ target: { value: 'DEFECT' } } as unknown as Event);
    expect(api.findings.calls.mostRecent().args[2]['category']).toBe('DEFECT');
    results.next({ items: [], total: 0, page: 1, size: 10 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No matching current SATD findings');
  });

  it('loads repository, PR, and analyzed routes from their dedicated endpoints', () => {
    for (const mode of ['repositories', 'pull-requests', 'analyzed-prs']) {
      route.snapshot.data.mode = mode;
      const fixture = TestBed.createComponent(DashboardDrilldown);
      fixture.detectChanges();
      fixture.destroy();
    }
    expect(api.repositories).toHaveBeenCalled();
    expect(api.pullRequests).toHaveBeenCalled();
    expect(api.analyzed).toHaveBeenCalled();
  });
});
