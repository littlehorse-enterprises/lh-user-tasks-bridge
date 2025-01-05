/**
 * Base error class for LH User Tasks API errors.
 * Provides common functionality and proper stack trace capture for all derived error classes.
 *
 * @extends Error
 * @example
 * ```typescript
 * throw new LHUserTasksError('Custom error message');
 * ```
 */
export class LHUserTasksError extends Error {
  /**
   * Creates a new LHUserTasksError instance
   * @param message - The error message
   */
  constructor(message: string) {
    super(message);
    this.name = this.constructor.name;
    // Maintains proper stack trace for where error was thrown (only available on V8)
    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, this.constructor);
    }
  }
}

/**
 * Thrown when the request is malformed or contains invalid parameters.
 * Common cases include:
 * - Missing required fields
 * - Invalid field formats
 * - Incompatible parameter combinations
 *
 * @extends LHUserTasksError
 * @see ValidationError for business logic validation errors
 */
export class BadRequestError extends LHUserTasksError {
  constructor(
    message: string = "Bad request - The request was malformed or contained invalid parameters",
  ) {
    super(message);
  }
}

/**
 * Thrown when authentication fails or is missing.
 * Common cases include:
 * - Missing OIDC token
 * - Expired token
 * - Invalid token format
 *
 * @extends LHUserTasksError
 * @see ForbiddenError for permission-related errors
 */
export class UnauthorizedError extends LHUserTasksError {
  constructor(
    message: string = "Unauthorized - Authentication is required to access this resource",
  ) {
    super(message);
  }
}

/**
 * Thrown when the authenticated user lacks necessary permissions.
 * Common cases include:
 * - Non-admin user attempting admin operations
 * - User not in required group
 * - User attempting to access tasks they don't own
 *
 * @extends LHUserTasksError
 */
export class ForbiddenError extends LHUserTasksError {
  constructor(
    message: string = "Forbidden - You do not have permission to perform this action",
  ) {
    super(message);
  }
}

/**
 * Thrown when the requested resource cannot be found.
 * Common cases include:
 * - Invalid task ID
 * - Invalid workflow run ID
 * - Deleted or expired tasks
 *
 * @extends LHUserTasksError
 */
export class NotFoundError extends LHUserTasksError {
  constructor(
    message: string = "Not found - The requested resource does not exist",
  ) {
    super(message);
  }
}

/**
 * Thrown when a precondition for the request has not been met.
 * Common cases include:
 * - Task already claimed by another user
 * - Task in wrong state for operation
 * - Concurrent modification conflicts
 *
 * @extends LHUserTasksError
 * @see TaskStateError for specific task state transition errors
 */
export class PreconditionFailedError extends LHUserTasksError {
  constructor(
    message: string = "Precondition failed - The request cannot be completed in the current state",
  ) {
    super(message);
  }
}

// Business Logic Errors

/**
 * Thrown when input validation fails during business logic processing.
 * Common cases include:
 * - Invalid task result values
 * - Schema validation failures
 * - Business rule violations
 *
 * @extends LHUserTasksError
 * @see BadRequestError for HTTP request validation errors
 */
export class ValidationError extends LHUserTasksError {
  constructor(
    message: string = "Validation error - The provided data is invalid",
  ) {
    super(message);
  }
}

/**
 * Thrown when attempting to perform an action on a task in an invalid state.
 * Common cases include:
 * - Completing a cancelled task
 * - Cancelling a completed task
 * - Claiming an already assigned task
 *
 * @extends LHUserTasksError
 * @see PreconditionFailedError for general precondition failures
 */
export class TaskStateError extends LHUserTasksError {
  constructor(
    message: string = "Task state error - The task is in an invalid state for this operation",
  ) {
    super(message);
  }
}

/**
 * Thrown when task assignment operations fail.
 * Common cases include:
 * - Assignment to non-existent user/group
 * - Assignment of already claimed task
 * - Assignment policy violations
 *
 * @extends LHUserTasksError
 * @example
 * ```typescript
 * throw new AssignmentError('Cannot assign task: already claimed by another user');
 * ```
 */
export class AssignmentError extends LHUserTasksError {
  constructor(
    message: string = "Assignment error - The task cannot be assigned in its current state",
  ) {
    super(message);
  }
}
