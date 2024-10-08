import React from "react";
import styles from "./layout.module.css";

export const Footer = () => {
  return (
    <footer className={styles.footer}>
      <div className={styles.copyright}>
        <p>&copy; 2024 LittleHorse Enterprises LLC.</p>
      </div>
    </footer>
  );
};
