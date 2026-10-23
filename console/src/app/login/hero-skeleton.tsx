import styles from "./login.module.css";

// Static artwork placeholders share one neutral fill, independent of card accents.
export default function HeroSkeleton({ className }: { className: string }) {
  return (
    <span
      data-slot="hero-skeleton"
      className={`${styles.heroSkeleton} ${className}`}
    />
  );
}
