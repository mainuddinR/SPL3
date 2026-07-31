export interface PullRequest {
    id: number;
    githubPrId: number;
    number: number;
    title: string;
    state: string;
    htmlUrl: string;
    body: string;
    createdAt: string;
    updatedAt: string;
}

export interface PullRequestFile {
    id: number;
    sha: string;
    filename: string;
    status: string;
    additions: number;
    deletions: number;
    changes: number;
    patch: string;
}

export interface PullRequestComment {
    id: number;
    githubCommentId: number;
    path: string;
    position: number;
    body: string;
    userLogin: string;
    createdAt: string;
}
