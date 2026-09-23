import { Component, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { PullRequestService } from '../../../services/pull-request.service';
import { PullRequest, PullRequestFile, PullRequestComment, SatdAnalysisResponse } from '../../../models/pull-request.model';

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

  analysisResults = signal<SatdAnalysisResponse[] | null>(null);
  analyzing = signal(false);
  analysisError = signal<string | null>(null);

  prId = signal<number | null>(null);
  pullRequest = signal<PullRequest | null>(null);
  files = signal<PullRequestFile[]>([]);
  comments = signal<PullRequestComment[]>([]);
  
  loading = signal<boolean>(true);
  syncing = signal<boolean>(false);
  error = signal<string | null>(null);
  
  selectedTab = signal<'overview' | 'files' | 'comments'>('overview');
  selectedFile = signal<PullRequestFile | null>(null);

  ngOnInit() {
    this.route.paramMap.subscribe(params => {
      const idStr = params.get('prId');
      if (idStr) {
        const id = parseInt(idStr, 10);
        this.resetAnalysis();
        this.prId.set(id);
        this.loadDetails(id);
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
      },
      error: () => {
        this.analysisError.set('Failed to analyze this PR. Please try again. If the problem persists, check that you are signed in and the analysis service is available.');
        this.analyzing.set(false);
      }
    });
  }

  private resetAnalysis() {
    this.analysisSubscription?.unsubscribe();
    this.analyzing.set(false);
    this.analysisResults.set(null);
    this.analysisError.set(null);
  }

  ngOnDestroy() {
    this.analysisSubscription?.unsubscribe();
  }

  goBack() {
    const pr = this.pullRequest();
    if (pr) {
      window.history.back();
    }
  }
}
