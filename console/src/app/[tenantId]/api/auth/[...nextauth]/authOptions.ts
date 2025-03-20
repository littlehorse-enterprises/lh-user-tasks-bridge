import { getRoles } from "@/lib/utils";
import { jwtDecode } from "jwt-decode";
import {
  GetServerSidePropsContext,
  NextApiRequest,
  NextApiResponse,
} from "next";
import { getServerSession, NextAuthOptions } from "next-auth";


export const authOptions: Omit<NextAuthOptions, "providers"> = {
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
      session.roles = getRoles(token.decoded);
      session.error = token.error;
      session.user = { ...session.user, id: token.decoded.sub };
      return session;
    },
  },
  events: {
    signOut: async ({ token: { id_token } }) => {
      const url = `${process.env.AUTH_KEYCLOAK_ISSUER}/protocol/openid-connect/logout?id_token_hint=${id_token}`;
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
