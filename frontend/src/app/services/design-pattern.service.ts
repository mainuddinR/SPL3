import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DesignAdviceResponse, PatternConformance, PatternProfile } from '../models/design-pattern.model';

@Injectable({ providedIn: 'root' })
export class DesignPatternService {
  private http = inject(HttpClient);
  private base = 'http://localhost:8080/api';

  profile(projectId: number): Observable<PatternProfile | null> {
    return this.http.get<PatternProfile | null>(`${this.base}/projects/${projectId}/design-profile`);
  }
  refreshProfile(projectId: number): Observable<PatternProfile> {
    return this.http.post<PatternProfile>(`${this.base}/projects/${projectId}/design-profile`, null);
  }
  profileForPr(prId: number): Observable<PatternProfile | null> {
    return this.http.get<PatternProfile | null>(`${this.base}/prs/${prId}/design-profile`);
  }
  latestCheck(prId: number): Observable<PatternConformance | null> {
    return this.http.get<PatternConformance | null>(`${this.base}/prs/${prId}/design-conformance`);
  }
  runCheck(prId: number): Observable<PatternConformance> {
    return this.http.post<PatternConformance>(`${this.base}/prs/${prId}/design-conformance`, null);
  }
  generateDesignAdvice(prId: number, checkId: number, index: number): Observable<DesignAdviceResponse> {
    return this.http.post<DesignAdviceResponse>(`${this.base}/prs/${prId}/design-conformance/${checkId}/findings/${index}/suggestion`, null);
  }
}
