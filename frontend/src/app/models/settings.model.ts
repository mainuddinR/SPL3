export interface UiCapability {
  llmEnabled: boolean;
  apiKeyConfigured: boolean;
  model: string;
}

export interface UiStatus {
  githubId: string;
  username: string;
  avatarUrl: string | null;
  connectedRepositoryCount: number;
  githubSessionConnected: boolean;
  mlServiceAvailable: boolean;
  databaseConnected: boolean;
  ai: UiCapability;
}

export interface GithubMonitorStatus {
  enabled: boolean;
  githubSessionConnected: boolean;
}
