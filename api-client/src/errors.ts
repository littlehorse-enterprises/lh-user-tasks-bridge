export class LHUserTasksError extends Error {
  constructor(message: string) {
    super(message);
    this.name = this.constructor.name;
    // Maintains proper stack trace for where error was thrown (only available on V8)
    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, this.constructor);
    }
  }
}

export class BadRequestError extends LHUserTasksError {
  constructor(
    message: string = "Bad request - The request was malformed or contained invalid parameters",
  ) {
    super(message);
  }
}

export class UnauthorizedError extends LHUserTasksError {
  constructor(
    message: string = "Unauthorized - Authentication is required to access this resource",
  ) {
    super(message);
  }
}

export class ForbiddenError extends LHUserTasksError {
  constructor(
    message: string = "Forbidden - You do not have permission to perform this action",
  ) {
    super(message);
  }
}

export class NotFoundError extends LHUserTasksError {
  constructor(
    message: string = "Not found - The requested resource does not exist",
  ) {
    super(message);
  }
}

export class PreconditionFailedError extends LHUserTasksError {
  constructor(
    message: string = "Precondition failed - The request cannot be completed in the current state",
  ) {
    super(message);
  }
}

// Business Logic Errors
export class ValidationError extends LHUserTasksError {
  constructor(
    message: string = "Validation error - The provided data is invalid",
  ) {
    super(message);
  }
}

export class TaskStateError extends LHUserTasksError {
  constructor(
    message: string = "Task state error - The task is in an invalid state for this operation",
  ) {
    super(message);
  }
}

export class AssignmentError extends LHUserTasksError {
  constructor(
    message: string = "Assignment error - The task cannot be assigned in its current state",
  ) {
    super(message);
  }
}

// Optional: Helper function to determine if an error is a LHUserTasksError
export function isLHUserTasksError(error: unknown): error is LHUserTasksError {
  return error instanceof LHUserTasksError;
}
