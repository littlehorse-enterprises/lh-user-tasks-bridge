/**
 * Represents a user in the system
 */
export type UserManagementUser = {
  /** Unique identifier for the user */
  id: string;
  /** User's email address */
  email: string;
  /** Username */
  username: string;
  /** User's first name */
  firstName: string;
  /** User's last name */
  lastName: string;
  /** Whether the user account is enabled */
  enabled: boolean;
  /** Whether the user's email has been verified */
  emailVerified: boolean;
  /** User's realm roles */
  realmRoles?: string[];
  /** Groups the user belongs to */
  groups?: string[];
};

/**
 * Represents a group in the system
 */
export type UserManagementGroup = {
  /** Unique identifier for the group */
  id: string;
  /** Display name of the group */
  name: string;
  /** Optional path in the group hierarchy */
  path?: string;
};

/**
 * Request type for creating a new user
 */
export type CreateUserRequest = {
  /** Username for the new user */
  username: string;
  /** Email address */
  email: string;
  /** First name */
  firstName: string;
  /** Last name */
  lastName: string;
  /** Whether the account is enabled */
  enabled: boolean;
  /** Optional initial password */
  password?: string;
  /** Whether the password is temporary and must be changed */
  temporaryPassword?: boolean;
};

/**
 * Request type for updating a user
 */
export type UpdateUserRequest = {
  /** User ID to update */
  id: string;
  /** Username */
  username: string;
  /** Email address */
  email: string;
  /** First name */
  firstName: string;
  /** Last name */
  lastName: string;
  /** Whether the account is enabled */
  enabled: boolean;
};

/**
 * Request type for setting/resetting a user's password
 */
export type UpsertPasswordRequest = {
  /** New password for the user */
  password: string;
  /** Whether the password is temporary and must be changed on first login */
  temporary?: boolean;
};

/**
 * Request type for searching users
 */
export type UserManagementSearchRequest = {
  /** Filter by email */
  email?: string;
  /** Filter by first name */
  firstName?: string;
  /** Filter by last name */
  lastName?: string;
  /** Filter by username */
  username?: string;
  /** Filter by user group ID */
  userGroupId?: string;
  /** Starting index for pagination */
  firstResult?: number;
  /** Maximum number of results to return */
  maxResults?: number;
};

/**
 * Response type for listing users
 */
export type UserManagementListUsersResponse = {
  /** Array of users */
  users: UserManagementUser[];
  /** Optional error message */
  message?: string;
};

/**
 * Response type for listing groups
 */
export type UserManagementListGroupsResponse = {
  /** Array of groups */
  groups: UserManagementGroup[];
  /** Optional error message */
  message?: string;
};

/**
 * Response type for listing group members
 */
export type UserManagementListGroupMembersResponse = {
  /** Array of users in the group */
  members: UserManagementUser[];
  /** Optional error message */
  message?: string;
};

/**
 * Generic API response type
 */
export type UserManagementApiResponse = {
  /** Whether the operation was successful */
  success: boolean;
  /** Optional error message */
  message?: string;
};

/**
 * Request type for creating a new group
 */
export type CreateGroupRequest = {
  /** Name of the group */
  name: string;
};

/**
 * Request type for updating a group
 */
export type UpdateGroupRequest = {
  /** New name for the group */
  name: string;
};

/**
 * Request type for searching groups
 */
export type GroupManagementSearchRequest = {
  /** Filter by group name */
  name?: string;
  /** Starting index for pagination */
  firstResult?: number;
  /** Maximum number of results to return */
  maxResults?: number;
};
