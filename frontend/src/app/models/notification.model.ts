export interface PrNotification {
  id: number;
  type: 'NEW_PR';
  repository: string;
  pullRequestId: number;
  pullRequestNumber: number;
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
}

export interface NotificationPage {
  content: PrNotification[];
  totalElements: number;
  totalPages: number;
}
