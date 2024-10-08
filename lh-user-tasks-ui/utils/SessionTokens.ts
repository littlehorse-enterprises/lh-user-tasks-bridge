/* eslint-disable @typescript-eslint/no-explicit-any */
import { getServerSession } from "next-auth";
import { authOptions } from "./authOptions";

export async function getAccessToken() {
  const session: any = await getServerSession(authOptions);
  if (session) {
    return session.access_token;
  }
  return null;
}

export async function getIdToken() {
  const session: any = await getServerSession(authOptions);
  if (session) {
    return session.id_token;
  }
  return null;
}
