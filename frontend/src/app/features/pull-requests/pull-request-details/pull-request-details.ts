import { Component, computed, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { PullRequestService } from '../../../services/pull-request.service';
import { PullRequest, PullRequestFile, PullRequestComment, SatdAnalysisResponse, AnalysisRunSummary, AnalysisRunDetail, SavedSatdFinding } from '../../../models/pull-request.model';
import { DiffRow, findSatdLocation, isSatdRow, parseUnifiedDiff } from './unified-diff';

@Component({
  selector: 'app-pull-request-details',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './pull-request-details.html'
})
export class PullRequestDetails implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private prService = inject(PullRequestService);
  private analysisSubscription?: Subscription;
  private historySubscription?: Subscription;
  private historyDetailSubscription?: Subscription;
  private suggestionSubscriptions = new Map<number, Subscription>();

  analysisResults = signal<SatdAnalysisResponse[] | null>(null);
  analyzing = signal(false);
  analysisError = signal<string | null>(null);
  historyRuns = signal<AnalysisRunSummary[]>([]);
  historyLoading = signal(false);
  historyError = signal<string | null>(null);
  selectedHistory = signal<AnalysisRunDetail | null>(null);
  historyDetailLoading = signal(false);
  historyDetailError = signal<string | null>(null);
  suggestionLoading = signal<Set<number>>(new Set());
  suggestionErrors = signal<Record<number, string>>({});

  prId = signal<number | null>(null);
  pullRequest = signal<PullRequest | null>(null);
  files = signal<PullRequestFile[]>([]);
  comments = signal<PullRequestComment[]>([]);
  
  loading = signal<boolean>(true);
  syncing = signal<boolean>(false);
  error = signal<string | null>(null);
  
  selectedTab = signal<'overview' | 'files' | 'comments' | 'history'>('overview');
  selectedFile = signal<PullRequestFile | null>(null);
  diffRows = computed(() => parseUnifiedDiff(this.selectedFile()?.patch));

  ngOnInit() {
    this.route.paramMap.subscribe(params => {
      const idStr = params.get('prId');
      if (idStr) {
        const id = parseInt(idStr, 10);
        this.resetAnalysis();
        this.prId.set(id);
        this.resetHistory();
        this.loadDetails(id);
        this.loadHistory(id);
      }
    });
  }

  loadDetails(id: number) {
    this.loading.set(true);
    this.prService.getPullRequest(id).subscribe({
      next: (pr) => {
        this.pullRequest.set(pr);
        this.loadFilesAndComments(id);
      },
      error: (err) => {
        console.error(err);
        this.error.set('Failed to load PR details.');
        this.loading.set(false);
      }
    });
  }

  loadFilesAndComments(id: number) {
    this.prService.getPullRequestFiles(id).subscribe(files => {
      this.files.set(files);
      if (files.length > 0) this.selectedFile.set(files[0]);
    });
    this.prService.getPullRequestComments(id).subscribe(comments => {
      this.comments.set(comments);
      this.loading.set(false);
    });
  }

  syncDetails() {
    const id = this.prId();
    if (!id || this.syncing() || this.analyzing()) return;
    this.resetAnalysis();
    this.syncing.set(true);
    this.prService.syncPullRequestDetails(id).subscribe({
      next: () => {
        this.syncing.set(false);
        this.loadFilesAndComments(id); // Reload after sync
      },
      error: (err) => {
        console.error(err);
        this.error.set('Failed to sync PR details.');
        this.syncing.set(false);
      }
    });
  }

  analyzePullRequest() {
    const id = this.prId();
    if (!id || this.loading() || this.syncing() || this.analyzing()) return;

    this.analysisResults.set(null);
    this.analysisError.set(null);
    this.analyzing.set(true);
    this.analysisSubscription = this.prService.analyzePullRequest(id).subscribe({
      next: (results) => {
        this.analysisResults.set(results);
        this.analyzing.set(false);
        this.loadHistory(id);
      },
      error: () => {
        this.analysisError.set('Failed to analyze this PR. Please try again. If the problem persists, check that you are signed in and the analysis service is available.');
        this.analyzing.set(false);
      }
    });
  }

  loadHistory(id: number) {
    this.historySubscription?.unsubscribe();
    this.historyLoading.set(true);
    this.historyError.set(null);
    this.historySubscription = this.prService.getAnalysisRuns(id).subscribe({
      next: runs => {
        this.historyRuns.set(runs);
        this.historyLoading.set(false);
      },
      error: () => {
        this.historyError.set('Failed to load analysis history. Please try again.');
        this.historyLoading.set(false);
      }
    });
  }

  selectHistoryRun(run: AnalysisRunSummary) {
    const id = this.prId();
    if (!id || this.historyDetailLoading()) return;
    if (this.selectedHistory()?.run.id === run.id) {
      this.selectedHistory.set(null);
      return;
    }
    this.historyDetailSubscription?.unsubscribe();
    this.selectedHistory.set(null);
    this.historyDetailError.set(null);
    this.historyDetailLoading.set(true);
    this.historyDetailSubscription = this.prService.getAnalysisRun(id, run.id).subscribe({
      next: detail => {
        this.selectedHistory.set(detail);
        this.historyRuns.update(runs => runs.map(current => current.id === detail.run.id ? detail.run : current));
        this.historyDetailLoading.set(false);
      },
      error: () => {
        this.historyDetailError.set('Failed to load this saved analysis run. Please try again.');
        this.historyDetailLoading.set(false);
      }
    });
  }

  generateSuggestion(finding: SavedSatdFinding) {
    const prId = this.prId();
    const runId = this.selectedHistory()?.run.id;
    if (!prId || !runId || finding.aiSuggestion || this.suggestionLoading().has(finding.id)) return;
    this.suggestionLoading.update(ids => new Set(ids).add(finding.id));
    this.suggestionErrors.update(errors => { const next = { ...errors }; delete next[finding.id]; return next; });
    const subscription = this.prService.generateSuggestion(prId, runId, finding.id).subscribe({
      next: response => {
        if (response.status === 'AVAILABLE' && response.suggestion) {
          this.selectedHistory.update(detail => detail?.run.id === runId ? {
            ...detail, findings: detail.findings.map(current => current.id === finding.id ? {
              ...current, aiSuggestion: response.suggestion, aiSuggestionGeneratedAt: response.generatedAt,
              aiSuggestionProviderModel: response.providerModel, aiSuggestionPromptVersion: response.promptVersion
            } : current)
          } : detail);
        } else {
          this.suggestionErrors.update(errors => ({ ...errors, [finding.id]: response.reason || 'Suggestion unavailable. Retry.' }));
        }
        this.finishSuggestion(finding.id);
      },
      error: () => {
        this.suggestionErrors.update(errors => ({ ...errors, [finding.id]: 'Suggestion unavailable. Retry.' }));
        this.finishSuggestion(finding.id);
      }
    });
    this.suggestionSubscriptions.set(finding.id, subscription);
  }

  private finishSuggestion(id: number) {
    this.suggestionLoading.update(ids => { const next = new Set(ids); next.delete(id); return next; });
    this.suggestionSubscriptions.delete(id);
  }

  private resetHistory() {
    this.suggestionSubscriptions.forEach(subscription => subscription.unsubscribe());
    this.suggestionSubscriptions.clear();
    this.suggestionLoading.set(new Set());
    this.suggestionErrors.set({});
    this.historySubscription?.unsubscribe();
    this.historyDetailSubscription?.unsubscribe();
    this.historyRuns.set([]);
    this.selectedHistory.set(null);
    this.historyLoading.set(false);
    this.historyDetailLoading.set(false);
    this.historyError.set(null);
    this.historyDetailError.set(null);
  }

  findLocation(finding: SatdAnalysisResponse) {
    return findSatdLocation(finding, this.files());
  }

  isSatdLocation(row: DiffRow): boolean {
    const file = this.selectedFile();
    return !!file && isSatdRow(row, file.filename, this.analysisResults() ?? []);
  }

  viewInDiff(finding: SatdAnalysisResponse) {
    const location = this.findLocation(finding);
    if (!location) return;

    this.selectedFile.set(location.file);
    this.selectedTab.set('files');
    setTimeout(() => {
      const target = document.getElementById(`diff-row-${location.file.id}-${location.rowIndex}`);
      target?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      target?.focus({ preventScroll: true });
    });
  }

  private resetAnalysis() {
    this.analysisSubscription?.unsubscribe();
    this.analyzing.set(false);
    this.analysisResults.set(null);
    this.analysisError.set(null);
  }

  ngOnDestroy() {
    this.suggestionSubscriptions.forEach(subscription => subscription.unsubscribe());
    this.analysisSubscription?.unsubscribe();
    this.historySubscription?.unsubscribe();
    this.historyDetailSubscription?.unsubscribe();
  }

  goBack() {
    const pr = this.pullRequest();
    if (pr) {
      window.history.back();
    }
  }
}
