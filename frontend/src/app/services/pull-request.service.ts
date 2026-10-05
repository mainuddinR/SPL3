import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PullRequest, PullRequestFile, PullRequestComment, SatdAnalysisResponse, AnalysisRunSummary, AnalysisRunDetail, SuggestionResponse } from '../models/pull-request.model';

@Injectable({
  providedIn: 'root'
})
export class PullRequestService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8080/api/prs';

  analyzePullRequest(prId: number): Observable<SatdAnalysisResponse[]> {
    return this.http.post<SatdAnalysisResponse[]>(`${this.apiUrl}/${prId}/analyze`, null);
  }

  getAnalysisRuns(prId: number): Observable<AnalysisRunSummary[]> {
    return this.http.get<AnalysisRunSummary[]>(`${this.apiUrl}/${prId}/analysis-runs`);
  }

  getAnalysisRun(prId: number, runId: number): Observable<AnalysisRunDetail> {
    return this.http.get<AnalysisRunDetail>(`${this.apiUrl}/${prId}/analysis-runs/${runId}`);
  }

  generateSuggestion(prId: number, runId: number, findingId: number): Observable<SuggestionResponse> {
    return this.http.post<SuggestionResponse>(`${this.apiUrl}/${prId}/analysis-runs/${runId}/findings/${findingId}/suggestion`, null);
  }

  syncPullRequests(projectId: number): Observable<any> {
    return this.http.post(`${this.apiUrl}/project/${projectId}/sync`, {});
  }

  getPullRequests(projectId: number): Observable<PullRequest[]> {
    return this.http.get<PullRequest[]>(`${this.apiUrl}/project/${projectId}`);
  }

  getPullRequest(prId: number): Observable<PullRequest> {
    return this.http.get<PullRequest>(`${this.apiUrl}/${prId}`);
  }

  syncPullRequestDetails(prId: number): Observable<any> {
    return this.http.post(`${this.apiUrl}/${prId}/sync-details`, {});
  }

  getPullRequestFiles(prId: number): Observable<PullRequestFile[]> {
    return this.http.get<PullRequestFile[]>(`${this.apiUrl}/${prId}/files`);
  }

  getPullRequestComments(prId: number): Observable<PullRequestComment[]> {
    return this.http.get<PullRequestComment[]>(`${this.apiUrl}/${prId}/comments`);
  }
}
