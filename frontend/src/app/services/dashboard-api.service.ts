import { API_ORIGIN } from '../core/config/api-origin';
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DashboardAnalyzedRow, DashboardFindingRow, DashboardFindingDetail, DashboardPage,
  DashboardPullRequestRow, DashboardRepositoryRow, DashboardResponse } from '../models/dashboard.model';

@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  private http = inject(HttpClient);
  private base = `${API_ORIGIN}/api/dashboard`;

  summary(): Observable<DashboardResponse> { return this.http.get<DashboardResponse>(`${this.base}/summary`); }
  repositories(page: number, size: number): Observable<DashboardPage<DashboardRepositoryRow>> {
    return this.http.get<DashboardPage<DashboardRepositoryRow>>(`${this.base}/repositories`, { params: { page, size } });
  }
  pullRequests(page: number, size: number, filters: Record<string, string>): Observable<DashboardPage<DashboardPullRequestRow>> {
    return this.http.get<DashboardPage<DashboardPullRequestRow>>(`${this.base}/pull-requests`, { params: this.params(page, size, filters) });
  }
  analyzed(page: number, size: number, filters: Record<string, string>): Observable<DashboardPage<DashboardAnalyzedRow>> {
    return this.http.get<DashboardPage<DashboardAnalyzedRow>>(`${this.base}/analyzed-prs`, { params: this.params(page, size, filters) });
  }
  findings(page: number, size: number, filters: Record<string, string>): Observable<DashboardPage<DashboardFindingRow>> {
    return this.http.get<DashboardPage<DashboardFindingRow>>(`${this.base}/findings`, { params: this.params(page, size, filters) });
  }
  findingDetail(id: number): Observable<DashboardFindingDetail> {
    return this.http.get<DashboardFindingDetail>(`${this.base}/findings/${id}`);
  }
  private params(page: number, size: number, filters: Record<string, string>): HttpParams {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [key, value] of Object.entries(filters)) if (value) params = params.set(key, value);
    return params;
  }
}
