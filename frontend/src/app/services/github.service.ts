import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GithubBranch, GithubRepo, GithubSearchResponse } from '../models/github.model';

@Injectable({
  providedIn: 'root'
})
export class GithubService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8080/api/github';

  searchRepositories(query: string, page: number = 1): Observable<GithubSearchResponse> {
    const params = new HttpParams()
      .set('q', query)
      .set('page', page.toString());
    
    return this.http.get<GithubSearchResponse>(`${this.apiUrl}/search`, { params });
  }

  getRepositoryDetails(owner: string, repo: string): Observable<GithubRepo> {
    return this.http.get<GithubRepo>(`${this.apiUrl}/repos/${owner}/${repo}`);
  }

  getBranches(owner: string, repo: string): Observable<GithubBranch[]> {
    return this.http.get<GithubBranch[]>(`${this.apiUrl}/repos/${owner}/${repo}/branches`);
  }

  selectRepository(repo: GithubRepo): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/repos/select`, repo);
  }
}
