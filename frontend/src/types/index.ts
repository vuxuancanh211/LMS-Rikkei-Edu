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

export type UserStatus = 'ACTIVE' | 'PENDING_ACTIVATION' | 'DISABLED' | 'DELETED';

export type UserResponse = {
  id: string;
  email: string;
  fullName: string;
  role: ApiUserRole;
  status: string;
  phoneNumber?: string | null;
  avatarUrl?: string | null;
};

export type AdminUserDetailResponse = UserResponse & {
  birthDate?: string | null;
  gender?: string | null;
  bio?: string | null;
  lastLoginAt?: string | null;
  disabledAt?: string | null;
  disabledBy?: string | null;
  disabledReason?: string | null;
  deletedAt?: string | null;
  createdBy?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
};

export type AdminUserListRequest = {
  search?: string;
  role?: string;
  status?: string;
  sortBy?: string;
  sortDir?: string;
  page?: number;
  size?: number;
};

export type AdminUserCreateRequest = {
  fullName: string;
  email: string;
  role: string;
  phoneNumber?: string;
};

export type AdminUserUpdateRequest = {
  fullName?: string;
  email?: string;
  phoneNumber?: string;
  avatarUrl?: string;
  birthDate?: string;
  gender?: string;
  bio?: string;
  role?: string;
  status?: string;
};

export type PagedResponse<T> = {
  items: T[];
  totalRecords: number;
  totalPages: number;
  page: number;
  size: number;
};

export type MessageResponse = {
  message: string;
};

// Group types
export type GroupResponse = {
  id: string;
  courseId: string;
  courseTitle: string;
  name: string;
  description?: string | null;
  maxCapacity?: number | null;
  memberCount: number;
  startDate: string;
  endDate?: string | null;
  status: 'UPCOMING' | 'ACTIVE' | 'COMPLETED';
  createdAt: string;
};

export type StudentSearchItem = {
  id: string;
  email: string;
  fullName: string;
  avatarUrl?: string | null;
};

export type GroupMemberResponse = {
  id: string;
  studentId: string;
  studentName: string;
  studentEmail: string;
  avatarUrl?: string | null;
  joinedAt: string;
};

export type GroupDetailResponse = GroupResponse & {
  members: GroupMemberResponse[];
};

export type CreateGroupPayload = {
  courseId: string;
  name: string;
  description?: string;
  maxCapacity?: number;
  startDate: string;
  endDate?: string | null;
};

export type UpdateGroupPayload = {
  name: string;
  description?: string;
  maxCapacity?: number;
  startDate?: string;
  endDate?: string | null;
};

export type AddMembersPayload = {
  emails: string[];
};

// CSV Import
export type CsvRowStatus = 'VALID' | 'FORMAT_ERROR' | 'DUPLICATE_IN_FILE' | 'DUPLICATE_IN_DB' | 'IMPORTED' | 'IMPORT_FAILED';

export type CsvImportRowResult = {
  rowNumber: number;
  fullName: string;
  email: string;
  phoneNumber: string;
  status: CsvRowStatus;
  errors: string[];
};

export type CsvImportPreviewResponse = {
  token: string;
  totalRows: number;
  validCount: number;
  formatErrorCount: number;
  duplicateInFileCount: number;
  duplicateInDbCount: number;
  rows: CsvImportRowResult[];
};

export type CsvImportConfirmResponse = {
  totalProcessed: number;
  successCount: number;
  failCount: number;
  results: CsvImportRowResult[];
};

export type GroupMemberCsvRowStatus = 'VALID' | 'FORMAT_ERROR' | 'DUPLICATE_IN_FILE' | 'NOT_FOUND' | 'ALREADY_IN_GROUP' | 'CAPACITY_EXCEEDED' | 'IMPORTED' | 'IMPORT_FAILED';

export type GroupMemberCsvImportRowResult = {
  rowNumber: number;
  email: string;
  status: GroupMemberCsvRowStatus;
  errors: string[];
};

export type GroupMemberCsvImportPreviewResponse = {
  token: string;
  totalRows: number;
  validCount: number;
  formatErrorCount: number;
  duplicateInFileCount: number;
  notFoundCount: number;
  alreadyInGroupCount: number;
  capacityExceededCount: number;
  rows: GroupMemberCsvImportRowResult[];
};

export type GroupMemberCsvImportConfirmResponse = {
  totalProcessed: number;
  successCount: number;
  failCount: number;
  results: GroupMemberCsvImportRowResult[];
};
