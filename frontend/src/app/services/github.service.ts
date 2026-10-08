import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GithubBranch, GithubRepo, RepositoryPage, GithubUserSearchPage } from '../models/github.model';

@Injectable({
  providedIn: 'root'
})
export class GithubService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8080/api/github';
  storedProjects(): Observable<{ id: number; githubRepoId: number; fullName: string }[]> {
    return this.http.get<{ id: number; githubRepoId: number; fullName: string }[]>('http://localhost:8080/api/projects');
  }

  myRepositories(page: number = 1): Observable<RepositoryPage> {
    return this.http.get<RepositoryPage>(`${this.apiUrl}/repositories/me`, { params: { page } });
  }

  userRepositories(username: string, page: number = 1): Observable<RepositoryPage> {
    return this.http.get<RepositoryPage>(`${this.apiUrl}/users/${encodeURIComponent(username)}/repositories`, { params: { page } });
  }

  searchUsers(query: string, page: number = 1): Observable<GithubUserSearchPage> {
    return this.http.get<GithubUserSearchPage>(`${this.apiUrl}/users/search`, { params: { q: query, page } });
  }

  searchRepositories(query: string, page: number = 1): Observable<RepositoryPage> {
    return this.http.get<RepositoryPage>(`${this.apiUrl}/repositories/search`, { params: { q: query, page } });
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
