export interface GithubOwner {
  login: string;
  avatar_url: string;
}

export interface GithubRepo {
  id: number;
  name: string;
  full_name: string;
  html_url: string;
  description: string;
  language: string;
  stargazers_count: number;
  private?: boolean;
  updated_at?: string;
  owner: GithubOwner;
}

export interface RepositoryPage {
  repositories: GithubRepo[];
  storedRepositoryIds: number[];
  page: number;
  hasNext: boolean;
}

export interface GithubBranch {
  name: string;
  commit: {
    sha: string;
    url: string;
  };
}

export interface GithubSearchResponse {
  total_count: number;
  incomplete_results: boolean;
  items: GithubRepo[];
}

export interface GithubUserResult {
  login: string;
  avatar_url: string;
  html_url: string;
  name?: string | null;
}

export interface GithubUserSearchPage {
  users: GithubUserResult[];
  page: number;
  hasNext: boolean;
}
