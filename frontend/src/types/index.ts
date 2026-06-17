export type UserRole = 'student' | 'instructor' | 'admin';
export type ApiUserRole = 'STUDENT' | 'INSTRUCTOR' | 'ADMIN';

export type AuthUser = {
  id: string;
  email: string;
  fullName: string;
  role: ApiUserRole;
  status: string;
  phoneNumber?: string | null;
  avatarUrl?: string | null;
};

export type AuthTokens = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
};
