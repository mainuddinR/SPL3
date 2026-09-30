import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Dashboard } from './dashboard';
import { DashboardResponse } from '../../models/dashboard.model';

describe('Dashboard', () => {
  const url = 'http://localhost:8080/api/dashboard/summary';
  const data: DashboardResponse = {
    stats: { repositoryCount: 1, pullRequestCount: 2, analyzedPullRequestCount: 1,
      currentSatdFindingCount: 2, latestAnalysisAt: '2026-09-30T11:00:00' },
    categoryDistribution: { DESIGN: 0, DEFECT: 0, TEST: 0, REQUIREMENT: 0,
      DOCUMENTATION: 0, UNCLASSIFIED: 1, NOT_ASSESSED: 1 },
    severityDistribution: { '1': 0, '2': 0, '3': 0, '4': 0, '5': 0, NOT_ASSESSED: 2 },
    repositories: [{ id: 7, name: 'ant', url: 'https://example.test/ant', pullRequestCount: 2,
      analyzedPullRequestCount: 1, currentSatdFindingCount: 2,
      lastCompletedAnalysisAt: '2026-09-30T11:00:00' }]
  };
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads persisted statistics with honest labels and no placeholder scores', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading dashboard statistics');
    http.expectOne(url).flush(data);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Analyzed PRs');
    expect(text).toContain('Current SATD Findings');
    expect(text).toContain('Category Distribution');
    expect(text).toContain('Unclassified');
    expect(text).toContain('Not Assessed');
    expect(text).toContain('Severity 5');
    expect(text).toContain('Repository Analysis Summary');
    expect(text).toContain('not a complete inventory');
    expect(text).not.toContain('Health Score');
    expect(text).not.toContain('Tech Debt Score');
    expect(text).not.toContain('Critical Issues');
  });

  it('distinguishes zero repositories, no PRs, no analyses, and completed zero SATD', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    http.expectOne(url).flush({ ...data, stats: { ...data.stats, repositoryCount: 0,
      pullRequestCount: 0, analyzedPullRequestCount: 0, currentSatdFindingCount: 0,
      latestAnalysisAt: null }, repositories: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No selected repositories yet');
    fixture.componentInstance.fetchDashboardData();
    http.expectOne(url).flush({ ...data, stats: { ...data.stats, pullRequestCount: 0,
      analyzedPullRequestCount: 0, currentSatdFindingCount: 0 } });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('no stored pull requests');
    fixture.componentInstance.fetchDashboardData();
    http.expectOne(url).flush({ ...data, stats: { ...data.stats, analyzedPullRequestCount: 0,
      currentSatdFindingCount: 0 } });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No completed PR analysis yet');
    fixture.componentInstance.fetchDashboardData();
    http.expectOne(url).flush({ ...data, stats: { ...data.stats, currentSatdFindingCount: 0 } });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('0 SATD findings in latest completed PR analyses');
    expect(fixture.nativeElement.textContent).not.toContain('No completed PR analysis yet');
  });

  it('shows a safe error and retries the read', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    http.expectOne(url).flush({ secret: 'backend detail' }, { status: 500, statusText: 'error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Could not load dashboard statistics');
    expect(fixture.nativeElement.textContent).not.toContain('backend detail');
    fixture.nativeElement.querySelector('button').click();
    http.expectOne(url).flush(data);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Current SATD Findings');
  });

  it('renders saved counts for every severity and keeps all-unclassified separate from not-assessed', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    http.expectOne(url).flush({ ...data,
      stats: { ...data.stats, currentSatdFindingCount: 7 },
      categoryDistribution: { ...data.categoryDistribution, UNCLASSIFIED: 7, NOT_ASSESSED: 0 },
      severityDistribution: { '1': 1, '2': 1, '3': 1, '4': 1, '5': 1, NOT_ASSESSED: 2 }
    });
    fixture.detectChanges();
    const categoryText = fixture.nativeElement.querySelector('#category-heading').parentElement.textContent as string;
    const severityText = fixture.nativeElement.querySelector('#severity-heading').parentElement.textContent as string;
    expect(categoryText).toMatch(/Unclassified\s*7/);
    expect(categoryText).toMatch(/Not Assessed\s*0/);
    for (let score = 1; score <= 5; score++) expect(severityText).toMatch(new RegExp(`Severity ${score}\\s*1`));
    expect(severityText).toMatch(/Not Assessed\s*2/);
  });
});
