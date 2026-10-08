import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GithubMonitorStatus, UiCapability, UiStatus } from '../models/settings.model';

@Injectable({ providedIn: 'root' })
export class SettingsApiService {
  private http = inject(HttpClient);
  private base = 'http://localhost:8080/api/settings';
  capabilities(): Observable<UiCapability> { return this.http.get<UiCapability>(`${this.base}/capabilities`); }
  status(): Observable<UiStatus> { return this.http.get<UiStatus>(`${this.base}/status`); }
  githubMonitorStatus(): Observable<GithubMonitorStatus> { return this.http.get<GithubMonitorStatus>(`${this.base}/github-monitor`); }
}
