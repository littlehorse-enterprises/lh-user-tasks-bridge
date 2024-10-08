/* eslint-disable @typescript-eslint/no-explicit-any */
"use client";
import React, { useState } from "react";
import { signOut, useSession } from "next-auth/react";
import styles from "./layout.module.css";
import Image from "next/image";

export const Header = () => {
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const response: any = useSession();
  const toggleDropdown = () => {
    setDropdownOpen(!dropdownOpen);
  };

  // async function keycloakSessionLogOut() {
  //   try {
  //     await fetch(`/api/auth/logout`, { method: "GET" });
  //   } catch (err) {
  //     // eslint-disable-next-line no-console
  //     console.error(err);
  //   }
  // }
  return (
    <header className={styles.header}>
      <div className={styles.leftSection}>
        <div className={styles.logoContainer}>
          <Image
            src="/images/little-horse-logo.png"
            alt="Logo"
            width={32}
            height={32}
          />
        </div>
      </div>
      <div className={styles.rightSection}>
        <div className={styles.profile} onClick={toggleDropdown}>
          <span className={styles.profileName}>
            {response?.data?.user?.name}
          </span>
          <Image
            src="/images/profile-avatar.svg"
            alt="Profile"
            className={styles.profileIcon}
            width={32}
            height={32}
          />
          {dropdownOpen && (
            <div className={styles.dropdownMenu}>
              <button
                className={styles.logoutButton}
                onClick={() => {
                  // keycloakSessionLogOut().then(() =>
                  signOut({
                    callbackUrl: "/",
                  });
                  // );
                }}
              >
                Logout
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};
