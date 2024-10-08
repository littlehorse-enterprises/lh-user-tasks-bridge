import React from "react";
import Image from "next/image";
import styles from "./Loader.module.css";

const Loader: React.FC = () => {
  return (
    <div className={styles.loaderContainer}>
      <Image
        src="/images/1488.gif"
        alt="Loading..."
        width={40}
        height={40}
        priority
      />
    </div>
  );
};

export default Loader;
