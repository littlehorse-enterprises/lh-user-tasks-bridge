import {
  AdminController,
  GroupManagementController,
  InitController,
  PublicController,
  UserController,
  UserManagementController,
} from "./client/index";

/**
 * Configuration options for the LittleHorse User Tasks Bridge API client
 * @property {string} baseUrl - Base URL of the API endpoint (e.g., "http://localhost:8089")
 * @property {string} tenantId - Tenant identifier for multi-tenant environments
 * @property {string} accessToken - OAuth access token for authentication (Bearer token)
 */
export interface ClientConfig {
  baseUrl: string;
  tenantId: string;
  accessToken: string;
}

/**
 * Client for interacting with the LittleHorse User Tasks Bridge API.
 *
 * This client provides methods for managing user tasks in LittleHorse, including:
 * - Task operations (claim, complete, cancel)
 * - Task listing and filtering
 * - Administrative functions (assign, force complete)
 * - User and group management
 *
 * @example
 * ```typescript
 * const client = new LHUTBApiClient({
 *   baseUrl: 'http://localhost:8089',  // UserTasks API endpoint
 *   tenantId: 'default',              // Your LittleHorse tenant
 *   accessToken: 'your-oidc-token'    // Valid OIDC access token
 * });
 * ```
 */
export class LHUTBApiClient {
  private baseUrl: string;
  private tenantId: string;
  private accessToken: string;

  public user: UserController;
  public admin: AdminController;
  public userManagement: UserManagementController;
  public groupManagement: GroupManagementController;
  public init: InitController;
  public public: PublicController;

  /**
   * Creates a new instance of the LittleHorse User Tasks Bridge API client
   * @param config - Configuration object containing connection details
   * @throws {ValidationError} If required configuration parameters are missing or invalid
   * @throws {UnauthorizedError} If initial connection test fails
   */
  constructor(config: ClientConfig) {
    this.baseUrl = config.baseUrl.replace(/\/$/, ""); // Remove trailing slash if present
    this.tenantId = config.tenantId;
    this.accessToken = config.accessToken;

    this.user = new UserController(this);
    this.admin = new AdminController(this);
    this.userManagement = new UserManagementController(this);
    this.groupManagement = new GroupManagementController(this);
    this.init = new InitController(this);
    this.public = new PublicController(this);
  }

  /**
   * Internal method to make authenticated HTTP requests to the API
   * @param path - API endpoint path (without base URL and tenant)
   * @param init - Optional fetch configuration including method, headers, and body
   * @returns Promise resolving to the JSON response or void
   */
  async fetch<T>(path: string, init?: RequestInit): Promise<T> {
    const url = `${this.baseUrl}/${this.tenantId}${path}`;

    try {
      const response = await fetch(url, {
        ...init,
        headers: {
          ...init?.headers,
          Authorization: `Bearer ${this.accessToken}`,
          "Content-Type": "application/json",
        },
      });

      // Throw the response object for non-2xx status codes
      if (!response.ok) {
        // Clone the response before reading its body
        const responseClone = response.clone();

        // Try to get more error details from the response
        try {
          const contentType = response.headers.get("content-type");
          if (contentType && contentType.includes("application/json")) {
            const errorBody = await response.json();
            console.error("API Error details:", errorBody);
          } else {
            const errorText = await response.text();
            console.error("API Error body:", errorText);
          }
        } catch (e) {
          console.error("Failed to parse error response:", e);
        }

        // Throw the cloned response so it can still be processed by error handlers
        throw responseClone;
      }

      const contentType = response.headers.get("content-type");
      if (contentType && contentType.includes("application/json")) {
        return response.json();
      } else if (!contentType) {
        // Handle no-content responses explicitly
        return undefined as T;
      }
      return response as T;
    } catch (error) {
      if (error instanceof Response) {
        throw error; // Re-throw response objects
      }
      console.error("API Request failed:", error);
      throw error;
    }
  }
}
