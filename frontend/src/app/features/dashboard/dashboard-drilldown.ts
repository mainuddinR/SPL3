import { Component, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { Subject, Subscription, Observable, debounceTime, distinctUntilChanged, switchMap, catchError, of, map } from 'rxjs';
import { DashboardApiService } from '../../services/dashboard-api.service';
import { UiPreferencesService } from '../../services/ui-preferences.service';
import { DashboardAnalyzedRow, DashboardFindingRow, DashboardPage, DashboardPullRequestRow,
  DashboardRepositoryRow, RepositoryAnalysisSummary } from '../../models/dashboard.model';

type Mode = 'repositories' | 'pull-requests' | 'analyzed-prs' | 'findings';

@Component({
  selector: 'app-dashboard-drilldown', standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard-drilldown.html'
})
export class DashboardDrilldown implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private api = inject(DashboardApiService);
  preferences = inject(UiPreferencesService);
  private reload = new Subject<void>();
  private searchInput = new Subject<string>();
  private subscriptions = new Subscription();

  mode = this.route.snapshot.data['mode'] as Mode;
  page = signal(1);
  total = signal(0);
  loading = signal(false);
  error = signal(false);
  repositories = signal<DashboardRepositoryRow[]>([]);
  pullRequests = signal<DashboardPullRequestRow[]>([]);
  analyzed = signal<DashboardAnalyzedRow[]>([]);
  findings = signal<DashboardFindingRow[]>([]);
  repositoryOptions = signal<RepositoryAnalysisSummary[]>([]);
  repositoryId = signal('');
  state = signal('');
  analyzedFilter = signal('');
  category = signal('');
  severity = signal('');
  suggestion = signal('ANY');
  query = signal('');

  readonly categoryOptions = ['DESIGN', 'DEFECT', 'TEST', 'REQUIREMENT', 'DOCUMENTATION', 'UNCLASSIFIED', 'NOT_ASSESSED'];
  readonly severityOptions = ['1', '2', '3', '4', '5', 'NOT_ASSESSED'];

  ngOnInit() {
    this.subscriptions.add(this.searchInput.pipe(debounceTime(350), distinctUntilChanged()).subscribe(value => {
      this.query.set(value); this.loadPage(1);
    }));
    this.subscriptions.add(this.reload.pipe(switchMap(() => {
      const size = this.preferences.pageSize();
      const filters = this.filters();
      let request: Observable<DashboardPage<unknown>>;
      switch (this.mode) {
        case 'repositories': request = this.api.repositories(this.page(), size); break;
        case 'pull-requests': request = this.api.pullRequests(this.page(), size, filters); break;
        case 'analyzed-prs': request = this.api.analyzed(this.page(), size, filters); break;
        case 'findings': request = this.api.findings(this.page(), size, filters); break;
      }
      return request.pipe(map(result => ({ result, failed: false })),
        catchError(() => of({ result: null, failed: true })));
    })).subscribe(response => {
      this.loading.set(false);
      if (response.failed || !response.result) { this.error.set(true); return; }
      this.total.set(response.result.total);
      this.error.set(false);
      switch (this.mode) {
        case 'repositories': this.repositories.set(response.result.items as DashboardRepositoryRow[]); break;
        case 'pull-requests': this.pullRequests.set(response.result.items as DashboardPullRequestRow[]); break;
        case 'analyzed-prs': this.analyzed.set(response.result.items as DashboardAnalyzedRow[]); break;
        case 'findings': this.findings.set(response.result.items as DashboardFindingRow[]); break;
      }
    }));
    if (this.mode !== 'repositories') this.subscriptions.add(this.api.summary().subscribe({
      next: response => this.repositoryOptions.set(response.repositories), error: () => this.repositoryOptions.set([])
    }));
    this.loadPage(1);
  }

  ngOnDestroy() { this.subscriptions.unsubscribe(); }

  loadPage(page: number) {
    if (page < 1) return;
    this.page.set(page); this.loading.set(true); this.error.set(false); this.reload.next();
  }
  changeRepository(event: Event) { this.repositoryId.set((event.target as HTMLSelectElement).value); this.loadPage(1); }
  changeState(event: Event) { this.state.set((event.target as HTMLSelectElement).value); this.loadPage(1); }
  changeAnalyzed(event: Event) { this.analyzedFilter.set((event.target as HTMLSelectElement).value); this.loadPage(1); }
  changeCategory(event: Event) { this.category.set((event.target as HTMLSelectElement).value); this.loadPage(1); }
  changeSeverity(event: Event) { this.severity.set((event.target as HTMLSelectElement).value); this.loadPage(1); }
  changeSuggestion(event: Event) { this.suggestion.set((event.target as HTMLSelectElement).value); this.loadPage(1); }
  onSearch(event: Event) {
    const value = (event.target as HTMLInputElement).value.trim();
    this.searchInput.next(value);
  }
  hasNext() { return this.page() * this.preferences.pageSize() < this.total(); }
  formatCategory(value: string) { return value.replaceAll('_', ' ').toLowerCase(); }

  private filters(): Record<string, string> {
    const filters: Record<string, string> = {};
    if (this.repositoryId()) filters['repositoryId'] = this.repositoryId();
    if (this.mode === 'pull-requests') {
      if (this.state()) filters['state'] = this.state();
      if (this.analyzedFilter()) filters['analyzed'] = this.analyzedFilter();
    }
    if (this.mode === 'findings') {
      if (this.category()) filters['category'] = this.category();
      if (this.severity()) filters['severity'] = this.severity();
      if (this.suggestion() !== 'ANY') filters['aiSuggestion'] = this.suggestion();
    }
    if (this.query()) filters['q'] = this.query();
    return filters;
  }
}
