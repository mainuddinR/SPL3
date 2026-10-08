import { Component, computed, inject, signal, OnInit, OnDestroy, afterNextRender, Injector } from '@angular/core';
import { Subscription } from 'rxjs';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { PullRequestService } from '../../../services/pull-request.service';
import { PullRequest, PullRequestFile, PullRequestComment, SatdAnalysisResponse, AnalysisRunSummary, AnalysisRunDetail, SavedSatdFinding, ReviewPreview, AnalysisProgress } from '../../../models/pull-request.model';
import { DiffRow, findSatdLocation, isSatdRow, parseUnifiedDiff } from './unified-diff';
import { SettingsApiService } from '../../../services/settings-api.service';
import { UiCapability } from '../../../models/settings.model';
import { primarySuggestionSections, suggestionReviewNote } from './saved-suggestion';
import { PrDesignConformance } from './pr-design-conformance';

@Component({
  selector: 'app-pull-request-details',
  standalone: true,
  imports: [CommonModule, RouterModule, PrDesignConformance],
  templateUrl: './pull-request-details.html'
})
export class PullRequestDetails implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private prService = inject(PullRequestService);
  private settingsApi = inject(SettingsApiService);
  private analysisSubscription?: Subscription;
  private routeSubscription?: Subscription;
  private progressSubscription?: Subscription;
  private progressTimer?: ReturnType<typeof setTimeout>;
  private cooldownTimers = new Map<number, ReturnType<typeof setTimeout>>();
  private historySubscription?: Subscription;
  private historyDetailSubscription?: Subscription;
  private suggestionSubscriptions = new Map<number, Subscription>();
  private reviewSubscription?: Subscription;

  analysisResults = signal<SatdAnalysisResponse[] | null>(null);
  analyzing = signal(false);
  analysisProgress = signal<AnalysisProgress | null>(null);
  activeRunId = signal<number | null>(null);
  private analysisCursor = 0;
  private historyTarget: SatdAnalysisResponse | null = null;
  private highlightedSavedFindingId = signal<number | null>(null);
  suggestionCooldowns = signal<Record<number, number>>({});
  analysisError = signal<string | null>(null);
  historyRuns = signal<AnalysisRunSummary[]>([]);
  historyLoading = signal(false);
  historyError = signal<string | null>(null);
  selectedHistory = signal<AnalysisRunDetail | null>(null);
  historyDetailLoading = signal(false);
  historyDetailError = signal<string | null>(null);
  suggestionLoading = signal<Set<number>>(new Set());
  suggestionErrors = signal<Record<number, string>>({});
  suggestionCapability = signal<UiCapability | null>(null);
  capabilityLoading = signal(true);
  capabilityError = signal(false);
  suggestionSections = primarySuggestionSections;
  suggestionReviewNote = suggestionReviewNote;
  reviewPreview = signal<ReviewPreview | null>(null);
  reviewLoading = signal(false);
  reviewPublishing = signal(false);
  reviewError = signal<string | null>(null);

  prId = signal<number | null>(null);
  pullRequest = signal<PullRequest | null>(null);
  files = signal<PullRequestFile[]>([]);
  comments = signal<PullRequestComment[]>([]);
  
  loading = signal<boolean>(true);
  syncing = signal<boolean>(false);
  error = signal<string | null>(null);
  
  private injector = inject(Injector);
  openDesignCheck() {
    this.selectedTab.set('design');
    afterNextRender(() => {
      const area = document.getElementById('pr-design-area');
      area?.scrollIntoView({ behavior: document.documentElement.classList.contains('motion-reduced') || window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' });
      area?.focus({ preventScroll: true });
    }, { injector: this.injector });
  }
  selectedTab = signal<'overview' | 'files' | 'comments' | 'history' | 'design'>('overview');
  selectedFile = signal<PullRequestFile | null>(null);
  diffRows = computed(() => parseUnifiedDiff(this.selectedFile()?.patch));

  ngOnInit() {
    this.settingsApi.capabilities().subscribe({
      next: value => { this.suggestionCapability.set(value); this.capabilityLoading.set(false); },
      error: () => { this.capabilityError.set(true); this.capabilityLoading.set(false); }
    });
    this.routeSubscription = this.route.paramMap.subscribe(params => {
      const idStr = params.get('prId');
      if (idStr) {
        const id = parseInt(idStr, 10);
        this.resetAnalysis();
        this.prId.set(id);
        this.resetHistory();
        const requestedTab = this.route.snapshot.queryParamMap.get('tab');
        if (requestedTab === 'history' || requestedTab === 'files' || requestedTab === 'design') this.selectedTab.set(requestedTab);
        this.loadDetails(id);
        this.loadHistory(id);
        this.resumeProgress(id);
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
      const filename = this.route.snapshot.queryParamMap.get('filename');
      const line = Number(this.route.snapshot.queryParamMap.get('line'));
      if (this.selectedTab() === 'files' && filename && Number.isInteger(line) && line > 0) {
        const file = files.find(item => item.filename === filename);
        if (file) {
          this.selectedFile.set(file);
          const row = parseUnifiedDiff(file.patch).findIndex(item => item.newLine === line);
          if (row >= 0) setTimeout(() => document.getElementById(`diff-row-${file.id}-${row}`)?.scrollIntoView({ block: 'center' }));
        }
      }
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

    this.analysisResults.set([]);
    this.analysisError.set(null);
    this.analyzing.set(true);
    this.analysisSubscription = this.prService.startProgressiveAnalysis(id).subscribe({
      next: progress => {
        this.activeRunId.set(progress.runId);
        this.analysisCursor = 0;
        this.applyProgress(progress);
        this.loadHistory(id);
        if (progress.status === 'RUNNING') this.pollProgress(id, progress.runId);
      },
      error: () => {
        this.analysisError.set('Could not start analysis. Please try again.');
        this.analyzing.set(false);
      }
    });
  }

  private resumeProgress(id: number) {
    this.analysisSubscription = this.prService.currentProgressiveAnalysis(id).subscribe({
      next: progress => {
        if (!progress) return;
        this.activeRunId.set(progress.runId);
        this.analysisResults.set([]);
        this.analysisCursor = 0;
        this.applyProgress(progress);
        if (progress.status === 'RUNNING') this.pollProgress(id, progress.runId);
      },
      error: () => { /* The PR and saved History remain usable when live progress is unavailable. */ }
    });
  }

  private pollProgress(prId: number, runId: number) {
    this.progressTimer = setTimeout(() => {
      if (this.prId() !== prId || this.activeRunId() !== runId) return;
      this.progressSubscription = this.prService.analysisProgress(prId, runId, this.analysisCursor).subscribe({
        next: progress => {
          this.applyProgress(progress);
          if (progress.status === 'RUNNING' || this.analysisCursor < progress.processedCandidates)
            this.pollProgress(prId, runId);
          else this.loadHistory(prId);
        },
        error: () => {
          this.analysisError.set('Progress is temporarily unavailable. Refresh this PR to resume.');
          this.analyzing.set(false);
        }
      });
    }, 1200);
  }

  private applyProgress(progress: AnalysisProgress) {
    this.analysisProgress.set(progress);
    if (progress.nextCursor > this.analysisCursor) {
      this.analysisResults.update(current => [...(current || []), ...progress.results]);
      this.analysisCursor = progress.nextCursor;
    }
    this.analyzing.set(progress.status === 'RUNNING');
    if (progress.status === 'FAILED')
      this.analysisError.set(progress.failureMessage || 'Analysis failed. Retry analysis.');
  }

  openAnalysisHistory(finding?: SatdAnalysisResponse) {
    const id = this.prId();
    if (!id) return;
    this.selectedTab.set('history');
    this.historyTarget = finding || null;
    const runId = this.activeRunId();
    this.router.navigate([], { relativeTo: this.route, queryParams: { tab: 'history', runId }, queryParamsHandling: 'merge' });
    if (runId) {
      const run = this.historyRuns().find(item => item.id === runId);
      if (run && this.selectedHistory()?.run.id !== runId) this.selectHistoryRun(run);
      else if (!run) this.loadHistory(id);
    }
    setTimeout(() => document.getElementById('analysis-history-section')?.scrollIntoView({ block: 'start' }));
  }

  loadHistory(id: number) {
    this.historySubscription?.unsubscribe();
    this.historyLoading.set(true);
    this.historyError.set(null);
    this.historySubscription = this.prService.getAnalysisRuns(id).subscribe({
      next: runs => {
        this.historyRuns.set(runs);
        this.historyLoading.set(false);
        const target = Number(this.route.snapshot.queryParamMap.get('runId'));
        const targetRun = this.activeRunId() || target;
        if (this.selectedTab() === 'history' && targetRun && !this.selectedHistory()) {
          const run = runs.find(item => item.id === targetRun);
          if (run) this.selectHistoryRun(run);
        }
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
    this.resetReview();
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
        const findingId = Number(this.route.snapshot.queryParamMap.get('findingId'));
        const matched = findingId || detail.findings.find(finding => this.historyTarget &&
          finding.filename === this.historyTarget.filename && finding.lineNumber === this.historyTarget.lineNumber &&
          finding.commentText === this.historyTarget.commentText)?.id;
        if (matched && detail.findings.some(finding => finding.id === matched)) {
          this.highlightedSavedFindingId.set(matched);
          setTimeout(() => document.getElementById(`saved-finding-${matched}`)?.scrollIntoView({ block: 'center' }));
        }
        this.historyTarget = null;
      },
      error: () => {
        this.historyDetailError.set('Failed to load this saved analysis run. Please try again.');
        this.historyDetailLoading.set(false);
      }
    });
  }

  previewGitHubReview() {
    const prId = this.prId();
    const run = this.selectedHistory()?.run;
    if (!prId || !run || run.status !== 'COMPLETED' || this.reviewLoading() || this.reviewPublishing()) return;
    this.reviewLoading.set(true);
    this.reviewError.set(null);
    this.reviewSubscription = this.prService.previewReview(prId, run.id).subscribe({
      next: preview => { this.reviewPreview.set(preview); this.reviewLoading.set(false); },
      error: () => { this.reviewError.set('Review preview unavailable. Sign in with GitHub again or try later.'); this.reviewLoading.set(false); }
    });
  }

  publishGitHubReview() {
    const prId = this.prId();
    const preview = this.reviewPreview();
    if (!prId || !preview || preview.staleRevision || preview.publicationStatus !== 'NOT_PUBLISHED'
        || this.reviewLoading() || this.reviewPublishing()) return;
    this.reviewPublishing.set(true);
    this.reviewError.set(null);
    this.reviewSubscription = this.prService.publishReview(prId, preview.runId).subscribe({
      next: result => { this.reviewPreview.set(result); this.reviewPublishing.set(false); },
      error: () => {
        this.reviewError.set('GitHub review was not confirmed. Check the PR on GitHub before trying again, then refresh this preview.');
        this.reviewPublishing.set(false);
      }
    });
  }

  private resetReview() {
    this.reviewSubscription?.unsubscribe();
    this.reviewPreview.set(null);
    this.reviewLoading.set(false);
    this.reviewPublishing.set(false);
    this.reviewError.set(null);
  }

  generateSuggestion(finding: SavedSatdFinding) {
    const prId = this.prId();
    const runId = this.selectedHistory()?.run.id;
    if (!prId || !runId || this.selectedHistory()?.run.status !== 'COMPLETED' || finding.label !== 'SATD'
        || !this.suggestionCapability()?.llmEnabled || !this.suggestionCapability()?.apiKeyConfigured
        || finding.aiSuggestion || this.suggestionLoading().has(finding.id) || this.coolingDown(finding.id)) return;
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
          let message = this.suggestionMessage(response.reasonCode);
          if (response.reasonCode === 'RATE_LIMITED') {
            const seconds = response.retryAfterSeconds && response.retryAfterSeconds > 0 ? response.retryAfterSeconds : 30;
            if (response.retryAfterSeconds) message = `AI provider request limit reached. Try again in about ${seconds} seconds.`;
            this.suggestionCooldowns.update(values => ({ ...values, [finding.id]: Date.now() + seconds * 1000 }));
            const existing = this.cooldownTimers.get(finding.id);
            if (existing) clearTimeout(existing);
            this.cooldownTimers.set(finding.id, setTimeout(() => {
              this.suggestionCooldowns.update(values => { const next = { ...values }; delete next[finding.id]; return next; });
              this.cooldownTimers.delete(finding.id);
            }, seconds * 1000));
          }
          this.suggestionErrors.update(errors => ({ ...errors, [finding.id]: message }));
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

  coolingDown(id: number) { return (this.suggestionCooldowns()[id] || 0) > Date.now(); }
  highlightedFinding(id: number) { return this.highlightedSavedFindingId() === id || this.route.snapshot.queryParamMap.get('findingId') === id.toString(); }

  private suggestionMessage(code: string | null): string {
    switch (code) {
      case 'DISABLED': return 'AI suggestions are disabled by the server.';
      case 'NOT_CONFIGURED': return 'AI provider is not configured.';
      case 'AUTHENTICATION_FAILED': return 'AI provider authentication failed. Check the server configuration.';
      case 'RATE_LIMITED': return 'AI provider request limit reached. Try again later.';
      case 'MODEL_UNAVAILABLE': return 'The configured AI model is unavailable.';
      case 'TIMEOUT': return 'The AI provider took too long to respond. Retry.';
      case 'PROVIDER_UNAVAILABLE': return 'AI provider is temporarily unavailable.';
      case 'CONTENT_BLOCKED': return 'Suggestion unavailable because sensitive content may be present.';
      case 'INVALID_PROVIDER_RESPONSE': return 'AI provider returned an invalid response. Retry later.';
      case 'INVALID_REQUEST': return 'AI provider rejected the request. Check the server configuration.';
      default: return 'Suggestion unavailable. Retry.';
    }
  }

  private resetHistory() {
    this.resetReview();
    this.suggestionSubscriptions.forEach(subscription => subscription.unsubscribe());
    this.suggestionSubscriptions.clear();
    this.suggestionLoading.set(new Set());
    this.cooldownTimers.forEach(timer => clearTimeout(timer));
    this.cooldownTimers.clear();
    this.suggestionCooldowns.set({});
    this.suggestionErrors.set({});
    this.historySubscription?.unsubscribe();
    this.historyDetailSubscription?.unsubscribe();
    this.historyRuns.set([]);
    this.selectedHistory.set(null);
    this.highlightedSavedFindingId.set(null);
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
    if (!file) return false;
    const linkedFilename = this.route.snapshot.queryParamMap.get('filename');
    const linkedLine = Number(this.route.snapshot.queryParamMap.get('line'));
    return isSatdRow(row, file.filename, this.analysisResults() ?? []) ||
      (linkedFilename === file.filename && linkedLine > 0 && row.newLine === linkedLine);
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
    this.progressSubscription?.unsubscribe();
    if (this.progressTimer) clearTimeout(this.progressTimer);
    this.analyzing.set(false);
    this.analysisResults.set(null);
    this.analysisProgress.set(null);
    this.activeRunId.set(null);
    this.analysisCursor = 0;
    this.analysisError.set(null);
  }

  ngOnDestroy() {
    this.routeSubscription?.unsubscribe();
    this.progressSubscription?.unsubscribe();
    if (this.progressTimer) clearTimeout(this.progressTimer);
    this.cooldownTimers.forEach(timer => clearTimeout(timer));
    this.reviewSubscription?.unsubscribe();
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
