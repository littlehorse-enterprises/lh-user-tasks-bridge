/**
 * Base error class for LH User Tasks API errors.
 * Provides common functionality and proper stack trace capture for all derived error classes.
 * @extends Error
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
 * Typically corresponds to HTTP 400 Bad Request responses.
 * @extends LHUserTasksError
 */
export class BadRequestError extends LHUserTasksError {
  constructor(
    message: string = "Bad request - The request was malformed or contained invalid parameters",
  ) {
    super(message);
  }
}

/**
 * Thrown when authentication is required but not provided or is invalid.
 * Typically corresponds to HTTP 401 Unauthorized responses.
 * @extends LHUserTasksError
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
 * Typically corresponds to HTTP 403 Forbidden responses.
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
 * Typically corresponds to HTTP 404 Not Found responses.
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
 * Typically corresponds to HTTP 412 Precondition Failed responses.
 * @extends LHUserTasksError
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
 * Thrown when input validation fails.
 * Used for both client-side and server-side validation errors.
 * @extends LHUserTasksError
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
 * For example, trying to complete an already cancelled task.
 * @extends LHUserTasksError
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
 * This could be due to conflicts, permissions, or invalid state transitions.
 * @extends LHUserTasksError
 */
export class AssignmentError extends LHUserTasksError {
  constructor(
    message: string = "Assignment error - The task cannot be assigned in its current state",
  ) {
    super(message);
  }
}
