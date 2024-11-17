import { jwtDecode } from "jwt-decode";
import {
  GetServerSidePropsContext,
  NextApiRequest,
  NextApiResponse,
} from "next";
import { getServerSession, NextAuthOptions } from "next-auth";
import KeycloakProvider from "next-auth/providers/keycloak";

export const authOptions: NextAuthOptions = {
  providers: [
    KeycloakProvider({
      clientId: `${process.env.KEYCLOAK_CLIENT_ID}`,
      clientSecret: `${process.env.KEYCLOAK_CLIENT_SECRET}`,
      issuer: `${process.env.KEYCLOAK_HOST}/realms/${process.env.KEYCLOAK_REALM}`,
    }),
  ],

  callbacks: {
    async jwt({ token, account }: any) {
      const nowTimeStamp = Math.floor(Date.now() / 1000);

      if (account) {
        token.decoded = jwtDecode(account.access_token);
        token.access_token = account.access_token;
        token.id_token = account.id_token;
        token.expires_at = account.expires_at;
        token.refresh_token = account.refresh_token;

        return token;
      }
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
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    signOut: async ({ token: { id_token } }) => {
      const url = `${process.env.KEYCLOAK_HOST}/realms/${process.env.KEYCLOAK_REALM}/protocol/openid-connect/logout?id_token_hint=${id_token}`;
      await fetch(url, {
        method: "GET",
        headers: { Accept: "application/json" },
      });
    },
  },
};

// Use it in server contexts
export async function auth(
  ...args:
    | [GetServerSidePropsContext["req"], GetServerSidePropsContext["res"]]
    | [NextApiRequest, NextApiResponse]
    | []
) {
  return await getServerSession(...args, authOptions);
}
