import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GithubBranch, GithubRepo, RepositoryPage } from '../models/github.model';

@Injectable({
  providedIn: 'root'
})
export class GithubService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8080/api/github';

  myRepositories(page: number = 1): Observable<RepositoryPage> {
    return this.http.get<RepositoryPage>(`${this.apiUrl}/repositories/me`, { params: { page } });
  }

  userRepositories(username: string, page: number = 1): Observable<RepositoryPage> {
    return this.http.get<RepositoryPage>(`${this.apiUrl}/users/${encodeURIComponent(username)}/repositories`, { params: { page } });
  }

  exactRepository(owner: string, repo: string): Observable<GithubRepo> {
    return this.http.get<GithubRepo>(`${this.apiUrl}/repositories/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}`);
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
