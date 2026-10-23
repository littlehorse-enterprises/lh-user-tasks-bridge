"use client";

import { LoaderCircle } from "lucide-react";
import Image from "next/image";
import keycloak from "./keycloak.svg";
import { useFormStatus } from "react-dom";
import styles from "./login.module.css";

export default function SignInButton() {
  const { pending } = useFormStatus();
  return (
    <button
      type="submit"
      className={styles.button}
      disabled={pending}
      aria-busy={pending}
    >
      <Image src={keycloak} alt="" width={24} height={22} />
      <span>
        {pending ? "Connecting to Keycloak…" : "Sign in with Keycloak"}
      </span>
      <span className={styles.buttonIndicator} aria-hidden="true">
        {pending && <LoaderCircle className={styles.spinner} size={18} />}
      </span>
    </button>
  );
}
