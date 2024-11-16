# LH User Tasks API Client

A TypeScript client for interacting with LittleHorse User Tasks API.

## Installation

```bash
npm install @littlehorse-enterprises/user-tasks-api-client
```

## Authentication

You'll need to provide an access token when initializing the client. You can obtain this token through your organization's OIDC provider configured in the **User Tasks API** configs.

## Usage

### Basic Example

```typescript
import { LittleHorseUserTasksApiClient } from "@littlehorse-enterprises/user-tasks-api-client";

const client = new LittleHorseUserTasksApiClient({
  baseUrl: "http://localhost:8089",
  tenantId: "default",
  accessToken: "your-oauth-access-token",
});

// List user tasks with pagination
const response = await client.listUserTasks({
  limit: 10,
  offset: 0,
});

console.log(response);
```

```typescript
// example console output
{
  userTasks: [
     {
       id: "123e4567-e89b-12d3-a456-426614174000",
       wfRunId: "987fcdeb-4321-12d3-a456-426614174000",
       userTaskDefName: "approve_request",
       status: "UNASSIGNED",
       notes: "Please review this purchase request",
       scheduledTime: "2024-03-20T15:30:00Z",
       userGroup: {
         id: "550e8400-e29b-41d4-a716-446655440000",
         name: "Finance Team"
       }
     },
     {
       id: "550e8400-e29b-41d4-a716-446655440001",
       wfRunId: "123e4567-e89b-12d3-a456-426614174001",
       userTaskDefName: "review_document",
       status: "ASSIGNED",
       notes: "Document needs final approval",
       scheduledTime: "2024-03-20T14:00:00Z",
       userGroup: {
         id: "550e8400-e29b-41d4-a716-446655440002",
         name: "Legal Team"
       },
       user: {
         id: "123e4567-e89b-12d3-a456-426614174002",
         email: "john.doe@company.com",
         firstName: "John",
         lastName: "Doe"
       }
     }
   ],
  bookmark: "eyJwYWdlIjoyLCJsaW1pdCI6MTB9"
}
```

### Response Format

```typescript
type ListUserTasksResponse = {
  userTasks: Array<{
    id: string;
    wfRunId: string;
    userTaskDefName: string;
    status: Status;
    notes: string;
    scheduledTime: string;
    userGroup?: UserGroup;
    user?: User;
  }>;
  bookmark: string | null;
};
```

## API Methods

The client provides the following methods:

- `listUserTasks(params)`: Retrieve a paginated list of user tasks
- `getUserTask(id)`: Get details of a specific user task
- `completeUserTask(id, result)`: Complete a user task with a result
- Additional methods...

For detailed API documentation, please visit [our documentation](https://littlehorse.dev/docs/overview).

## Development

To contribute to this project, please follow our [contribution guidelines](https://github.com/littlehorse-enterprises/lh-user-tasks-api-client/blob/main/CONTRIBUTING.md).

## License

This project is licensed under the [Apache 2.0 License](LICENSE).
