// Error types for consistent error handling
export enum ErrorType {
  UNAUTHORIZED = "UNAUTHORIZED",
  FORBIDDEN = "FORBIDDEN",
  NOT_FOUND = "NOT_FOUND",
  VALIDATION = "VALIDATION",
  CONFLICT = "CONFLICT",
  SERVER = "SERVER",
  NETWORK = "NETWORK",
  UNKNOWN = "UNKNOWN",
}

// Standardized error response format
export interface ErrorResponse {
  type: ErrorType;
  message: string;
  statusCode?: number;
  details?: unknown;
}

// Helper to determine error type from HTTP status code
function getErrorTypeFromStatus(status: number): ErrorType {
  switch (status) {
    case 401:
      return ErrorType.UNAUTHORIZED;
    case 403:
      return ErrorType.FORBIDDEN;
    case 404:
      return ErrorType.NOT_FOUND;
    case 400:
    case 422:
      return ErrorType.VALIDATION;
    case 409:
      return ErrorType.CONFLICT;
    case 500:
    case 502:
    case 503:
      return ErrorType.SERVER;
    default:
      return ErrorType.UNKNOWN;
  }
}

// Main error handling wrapper for server actions
export async function withErrorHandling<T>(
  action: () => Promise<T>,
): Promise<{ data?: T; error?: ErrorResponse }> {
  try {
    const data = await action();
    return { data };
  } catch (error: unknown) {
    console.error("Action error detailed:", JSON.stringify(error, null, 2));

    // Handle fetch errors from API client
    if (
      error instanceof Response ||
      (error && typeof error === "object" && "status" in error)
    ) {
      const response = error as Response;
      let errorData: any = {};

      try {
        // Try to parse response as JSON if possible
        if (
          response.headers?.get?.("content-type")?.includes("application/json")
        ) {
          errorData = await response.json();
          console.log("Parsed error data:", errorData);
        } else {
          try {
            const text = await response.text();
            console.log("Raw error text:", text);
            errorData = { message: text };
          } catch (textError) {
            console.error("Failed to get response text:", textError);
            errorData = { message: "Failed to parse error response" };
          }
        }
      } catch (parseError) {
        console.error("Error parsing response:", parseError);
        errorData = { message: "Failed to parse error response" };
      }

      // Extract the error message from various possible formats
      let errorMessage = "Unknown error";
      if (errorData.message) {
        errorMessage = errorData.message;
      } else if (errorData.error) {
        errorMessage = errorData.error;
      } else if (errorData.detail) {
        errorMessage = errorData.detail;
      } else if (
        errorData.errors &&
        Array.isArray(errorData.errors) &&
        errorData.errors.length > 0
      ) {
        // Handle Spring Boot validation errors format
        errorMessage = errorData.errors
          .map((e: any) => e.message || e.defaultMessage)
          .join(", ");
      }

      return {
        error: {
          type: getErrorTypeFromStatus(response.status),
          message:
            errorMessage ||
            `Error ${response.status}: ${response.statusText || "Unknown error"}`,
          statusCode: response.status,
          details: errorData,
        },
      };
    }

    // Handle network errors
    if (error instanceof TypeError && error.message.includes("fetch")) {
      return {
        error: {
          type: ErrorType.NETWORK,
          message: "Network error: Unable to connect to server",
          details: error.message,
        },
      };
    }

    // Handle all other errors
    return {
      error: {
        type: ErrorType.UNKNOWN,
        message:
          error instanceof Error ? error.message : "An unknown error occurred",
        details: error,
      },
    };
  }
}
