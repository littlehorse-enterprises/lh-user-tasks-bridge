import { getRoles } from "@/lib/utils";
import { jwtDecode } from "jwt-decode";
import NextAuth from "next-auth";
import { DefaultJWT } from "next-auth/jwt";
import KeycloakProvider from "next-auth/providers/keycloak";

declare module "next-auth" {
  interface Session {
    accessToken: string;
    idToken: string;
    roles: string[];
    expiresAt: number;
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    decoded: DefaultJWT & {
      realm_access: {
        roles: string[];
        account: unknown;
      };
      allowed_tenant: string;
      [key: string]: unknown;
    };
    accessToken: string;
    idToken: string;
    expiresAt: number;
    refreshToken: string;
  }
}

export const { handlers, signIn, signOut, auth } = NextAuth({
  providers: [KeycloakProvider],

  callbacks: {
    async jwt({ token, account }) {
      if (account) {
        token.decoded = jwtDecode(account.access_token || "");
        token.accessToken = account.access_token || "";
        token.idToken = account.id_token || "";
        token.expiresAt = account.expires_at || 0;
        token.refreshToken = account.refresh_token || "";
        return token;
      }

      if (token.expiresAt >= Math.floor(Date.now() / 1000)) return token;

      return null;
    },

    async session({ session, token }) {
      session.accessToken = token.accessToken;
      session.idToken = token.idToken;
      session.roles = getRoles(token.decoded);
      session.user = { ...session.user, id: token.decoded.sub || "" };
      return session;
    },

    async authorized({ auth }) {
      const token = auth?.accessToken;
      if (!token) return false;
      if (auth?.expiresAt && auth.expiresAt < Math.floor(Date.now() / 1000))
        return false;

      try {
        const { ok } = await fetch(
          `${process.env.AUTH_KEYCLOAK_ISSUER}/protocol/openid-connect/userinfo`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
            },
          },
        );

        return ok;
      } catch {
        return false;
      }
    },
  },
  events: {
    async signOut(message) {
      if ("token" in message && message.token?.idToken) {
        const url = `${process.env.AUTH_KEYCLOAK_ISSUER}/protocol/openid-connect/logout?id_token_hint=${message.token.idToken}`;
        await fetch(url, {
          method: "GET",
          headers: { Accept: "application/json" },
        });
      }
    },
  },
});
