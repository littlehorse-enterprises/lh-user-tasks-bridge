import "next-auth";
import "next-auth/jwt";
import { DefaultJWT } from "next-auth/jwt";

declare module "next-auth/jwt" {
  interface JWT {
    decoded: DefaultJWT & {
      realm_access: {
        roles: string[];
        account: unknown;
      };
      allowed_tenant: string;
    };
    access_token: string;
    id_token: string;
    expires_at: number;
    refresh_token: string;
  }
}

declare module "next-auth" {
  interface User {
    id: string;
  }
  interface Session {
    access_token: string;
    id_token: number;
    roles: string[];
    error: unknown;
    user: User;
  }
}
