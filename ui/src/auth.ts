import { jwtDecode } from "jwt-decode";
import NextAuth from "next-auth";
import KeycloakProvider from "next-auth/providers/keycloak";

export const { handlers, signIn, signOut, auth } = NextAuth({
  providers: [
    KeycloakProvider({
      clientId: process.env.AUTH_KEYCLOAK_CLIENT_ID,
      clientSecret: process.env.AUTH_KEYCLOAK_CLIENT_SECRET,
      issuer: `${process.env.AUTH_KEYCLOAK_HOST}/realms/${process.env.AUTH_KEYCLOAK_REALM}`,
    }),
  ],

  callbacks: {
    async jwt({ token, account }: any) {
      if (account) {
        token.decoded = jwtDecode(account.access_token);
        token.access_token = account.access_token;
        token.id_token = account.id_token;
        token.expires_at = account.expires_at;
        token.refresh_token = account.refresh_token;

        return token;
      }

      const nowTimeStamp = Math.floor(Date.now() / 1000);
      if (nowTimeStamp < token.expires_at) {
        return token;
      }
    },
    async session({ session, token }: any) {
      session.access_token = token.access_token;
      session.id_token = token.id_token;
      session.roles = token.decoded.realm_access.roles;
      session.error = token.error;
      session.user = { ...session.user, id: token.decoded.sub };
      return session;
    },
  },
  events: {
    signOut: async ({ token: { id_token } }: any) => {
      const url = `${process.env.AUTH_KEYCLOAK_HOST}/realms/${process.env.AUTH_KEYCLOAK_REALM}/protocol/openid-connect/logout?id_token_hint=${id_token}`;
      await fetch(url, {
        method: "GET",
        headers: { Accept: "application/json" },
      });
    },
  },
});
