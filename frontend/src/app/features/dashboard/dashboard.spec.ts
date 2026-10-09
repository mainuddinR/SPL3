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
  it('uses collapsed accessible category and severity info icons without About rows', () => {
    const fixture = TestBed.createComponent(Dashboard); fixture.detectChanges();
    http.expectOne(url).flush(structuredClone(data)); fixture.detectChanges();
    for (const label of ['About SATD categories', 'About severity levels']) {
      const button = fixture.nativeElement.querySelector(`button[aria-label="${label}"]`) as HTMLButtonElement;
      expect(button).not.toBeNull(); expect(button.getAttribute('aria-expanded')).toBe('false');
      button.click(); fixture.detectChanges(); expect(button.getAttribute('aria-expanded')).toBe('true');
    }
    expect(fixture.nativeElement.textContent).not.toContain('About categories');
    expect(fixture.nativeElement.textContent).not.toContain('About severity');
    expect(fixture.nativeElement.querySelector('button[aria-label="Remove repository"]').classList.contains('repository-remove')).toBeTrue();
  });
  it('uses a trash icon, supports Cancel and removes the card after successful deletion', () => {
    const fixture = TestBed.createComponent(Dashboard); fixture.detectChanges();
    http.expectOne(url).flush(structuredClone(data)); fixture.detectChanges();
    const remove = fixture.nativeElement.querySelector('button[aria-label="Remove repository"]') as HTMLButtonElement;
    expect(remove.title).toBe('Remove repository'); expect(remove.querySelector('svg')).not.toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('SPL3'); remove.click(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull();
    (fixture.nativeElement.querySelector('[role="dialog"] button') as HTMLButtonElement).click(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    remove.click(); fixture.detectChanges();
    (fixture.nativeElement.querySelectorAll('[role="dialog"] button')[1] as HTMLButtonElement).click();
    http.expectOne('http://localhost:8080/api/projects/7').flush(null);
    http.expectOne(url).flush({ ...data, stats: { ...data.stats, repositoryCount: 0 }, repositories: [] });
    fixture.detectChanges(); expect(fixture.nativeElement.querySelector('app-repo-summary')).toBeNull();
    expect(fixture.componentInstance.notice).toBe('ant removed.');
  });

  it('loads persisted statistics with honest labels and no placeholder scores', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading dashboard statistics');
    http.expectOne(url).flush(structuredClone(data));
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Analyzed PRs');
    expect(text).toContain('Current SATD Findings');
    expect(text).toContain('Category Distribution');
    expect(text).toContain('Unclassified');
    expect(text).toContain('Not Assessed');
    expect(text).toContain('Severity 5');
    expect(text).toContain('Repository Analysis Summary');
    expect(fixture.nativeElement.querySelector('h2').textContent.trim()).toBe('Dashboard');
    expect(text).not.toContain('PR Analysis Dashboard');
    expect(text).not.toContain("Insights from each PR's latest completed analysis.");
    expect(fixture.nativeElement.querySelector('[aria-label="How Dashboard metrics are calculated"]')).not.toBeNull();
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
    http.expectOne(url).flush(structuredClone(data));
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
    const categorySection = fixture.nativeElement.querySelector('[data-distribution="category"]') as HTMLElement;
    const severitySection = fixture.nativeElement.querySelector('[data-distribution="severity"]') as HTMLElement;
    const unclassified = categorySection.querySelector('[data-tone="unclassified"]') as HTMLElement;
    const notAssessed = categorySection.querySelector('[data-tone="not-assessed"]') as HTMLElement;
    expect(unclassified.querySelector('.distribution-count')?.textContent?.trim()).toBe('7');
    expect(unclassified.querySelector('.distribution-percent')?.textContent?.trim()).toBe('100.0%');
    expect(notAssessed.querySelector('.distribution-count')?.textContent?.trim()).toBe('0');
    for (let score = 1; score <= 5; score++) {
      const row = severitySection.querySelector(`[data-tone="severity-${score}"]`) as HTMLElement;
      expect(row.querySelector('.distribution-count')?.textContent?.trim()).toBe('1');
      expect(row.querySelector('.distribution-percent')?.textContent?.trim()).toBe('14.3%');
    }
    expect(severitySection.querySelector('[data-tone="not-assessed"] .distribution-count')?.textContent?.trim()).toBe('2');
    expect(severitySection.querySelector('.distribution-stats')?.textContent).toContain('5');
  });

  it('uses truthful total-based percentages and highlights the dominant category', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    http.expectOne(url).flush({ ...data,
      categoryDistribution: { DESIGN: 1, DEFECT: 2, TEST: 0, REQUIREMENT: 0,
        DOCUMENTATION: 0, UNCLASSIFIED: 5, NOT_ASSESSED: 2 },
      severityDistribution: { '1': 1, '2': 1, '3': 1, '4': 4, '5': 0, NOT_ASSESSED: 3 }
    });
    fixture.detectChanges();
    const category = fixture.nativeElement.querySelector('[data-distribution="category"]') as HTMLElement;
    const severity = fixture.nativeElement.querySelector('[data-distribution="severity"]') as HTMLElement;
    expect(category.querySelectorAll('.distribution-stat strong')[0].textContent.trim()).toBe('10');
    expect(category.querySelectorAll('.distribution-stat strong')[1].textContent.trim()).toBe('2');
    expect(category.querySelectorAll('.distribution-stat strong')[2].textContent.trim()).toBe('Unclassified');
    const leader = category.querySelector('[data-tone="unclassified"]') as HTMLElement;
    expect(leader.classList).toContain('distribution-leader');
    expect(leader.querySelector('.distribution-percent')?.textContent?.trim()).toBe('50.0%');
    expect((leader.querySelector('.distribution-fill') as HTMLElement).style.width).toBe('50%');
    const severity4 = severity.querySelector('[data-tone="severity-4"]') as HTMLElement;
    expect(severity4.querySelector('.distribution-percent')?.textContent?.trim()).toBe('40.0%');
    expect(severity.querySelectorAll('.distribution-stat strong')[0].textContent.trim()).toBe('7');
    expect(severity.querySelectorAll('.distribution-stat strong')[1].textContent.trim()).toBe('Level 4');
    expect(severity.querySelectorAll('.distribution-stat strong')[2].textContent.trim()).toBe('Severity 4');
  });

  it('shows a clean zero-data state without invalid bar widths', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    http.expectOne(url).flush({ ...data,
      stats: { ...data.stats, currentSatdFindingCount: 0 },
      categoryDistribution: { DESIGN: 0, DEFECT: 0, TEST: 0, REQUIREMENT: 0,
        DOCUMENTATION: 0, UNCLASSIFIED: 0, NOT_ASSESSED: 0 },
      severityDistribution: { '1': 0, '2': 0, '3': 0, '4': 0, '5': 0, NOT_ASSESSED: 0 }
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.distribution-empty').length).toBe(2);
    expect(fixture.nativeElement.querySelectorAll('.distribution-fill').length).toBe(0);
    expect(fixture.componentInstance.percentageLabel(0, 0)).toBe('0.0%');
    expect(fixture.nativeElement.textContent).not.toContain('NaN');
  });

  it('uses severity semantics and makes zero rows muted, not invisible', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    http.expectOne(url).flush(structuredClone(data));
    fixture.detectChanges();
    const first = fixture.nativeElement.querySelector('[data-tone="severity-1"]') as HTMLElement;
    expect(first.classList.contains('distribution-zero')).toBeTrue();
    expect(first.textContent).toContain('Lowest');
    expect((first.querySelector('.distribution-fill') as HTMLElement).style.width).toBe('0%');
    const na = fixture.nativeElement.querySelector('[data-distribution="severity"] [data-tone="not-assessed"]') as HTMLElement;
    expect(na.textContent).toContain('Not Assessed');
    expect(na.textContent).toContain('100.0%');
  });

  it('makes all four summary cards real drill-down links', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    http.expectOne(url).flush(structuredClone(data));
    fixture.detectChanges();
    const paths = Array.from(fixture.nativeElement.querySelectorAll('app-stats-card a') as NodeListOf<HTMLAnchorElement>)
      .map(link => new URL(link.href).pathname);
    expect(paths).toEqual(['/dashboard/repositories', '/dashboard/pull-requests',
      '/dashboard/analyzed-prs', '/dashboard/findings']);
    expect(fixture.nativeElement.textContent).toContain('Current SATD Findings');
  });
});
