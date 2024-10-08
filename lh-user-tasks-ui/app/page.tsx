/* eslint-disable @typescript-eslint/no-explicit-any */
"use client";
import { useSession } from "next-auth/react";

import {
  useInitLH,
  ListTasks,
  UserInfo,
} from "@littlehorse-enterprises/user-tasks";

import styles from "./page.module.css";

export default function Home() {
  const response: any = useSession();

  const { isAuthorized } = useInitLH({
    tenantId: process.env.NEXT_PUBLIC_KEYCLOAK_REALM,
    sessionToken: response?.data?.access_token,
    url: process.env.NEXT_PUBLIC_SERVER_URL,
  });

  return (
    <div className={styles.page}>
      {isAuthorized && (
        <>
          <UserInfo />
          <ListTasks admin />
        </>
      )}
    </div>
  );
}
