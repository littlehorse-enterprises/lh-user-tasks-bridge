import { signIn } from "@/auth";
import horse from "@/../public/images/logo.svg";
import {
  ArrowRight,
  ClipboardList,
  LockKeyhole,
  UserCheck,
} from "lucide-react";
import { ThemeToggle } from "@/components/theme-toggle";
import { AuthError } from "next-auth";
import localFont from "next/font/local";
import Image from "next/image";
import { redirect } from "next/navigation";
import { headers } from "next/headers";
import type { Metadata } from "next";
import SignInButton from "./sign-in-button";
import ProcessMatrix from "./process-matrix";
import HeroSkeleton from "./hero-skeleton";
import styles from "./login.module.css";

const geist = localFont({ src: "../../../public/fonts/GeistVF.woff" });

export const metadata: Metadata = {
  title: "Sign in | LittleHorse Business Console",
  description:
    "Sign in to LittleHorse Business Console to manage your tasks and keep work moving.",
};

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{
    callbackUrl?: string;
    error?: string;
  }>;
}) {
  const { callbackUrl = "/", error } = await searchParams;
  // Explicit local preview opt-in; all other hosts retain reduced-motion defaults.
  const host = (await headers()).get("host");
  const previewMotion =
    process.env.LH_LOGIN_PREVIEW_MOTION === "true" &&
    ["localhost:3000", "127.0.0.1:3000", "[::1]:3000"].includes(host ?? "");

  async function handleSignIn() {
    "use server";
    try {
      await signIn("keycloak", { redirectTo: callbackUrl });
    } catch (error) {
      if (error instanceof AuthError) {
        redirect(
          `/login?error=SignIn&callbackUrl=${encodeURIComponent(callbackUrl)}`,
        );
      }
      throw error;
    }
  }

  return (
    <main
      className={`${styles.page} ${geist.className} ${previewMotion ? styles.motionPreview : ""}`}
    >
      <section className={styles.story} aria-labelledby="story-title">
        <ProcessMatrix />
        <div className={styles.brand}>
          <Image
            src={horse}
            alt=""
            width={42}
            height={37}
            priority
            className={styles.themeHorse}
          />
          <span>LittleHorse</span>
        </div>

        <div className={styles.storyBody}>
          <p className={styles.eyebrow}>BUSINESS CONSOLE</p>
          <h1 id="story-title">
            Keep work
            <br />
            <span>moving.</span>
          </h1>

          <div className={styles.illustration} aria-hidden="true">
            <svg
              className={styles.connections}
              viewBox="0 0 500 260"
              fill="none"
            >
              <path d="M40 178H105Q125 178 125 158V83Q125 63 145 63H250" />
              <path d="M250 63H355Q375 63 375 83V178Q375 198 395 198H465" />
              <path d="M250 63V233" strokeDasharray="4 7" />
              <circle cx="40" cy="178" r="4" />
              <circle cx="465" cy="198" r="4" />
              <circle cx="250" cy="233" r="4" />
            </svg>
            <div className={styles.horseOrbit}>
              <div className={styles.horseDisc}>
                <Image src={horse} alt="" width={100} height={86} priority />
              </div>
            </div>
            <div className={`${styles.workflowNode} ${styles.reviewNode}`}>
              <span className={styles.previewStatus} />
              <div className={styles.previewHeader}>
                <span className={styles.previewIcon}>
                  <ClipboardList size={17} />
                </span>
                <span className={styles.previewTitles}>
                  <span className={styles.previewTitle}>UserTaskDef</span>
                  <HeroSkeleton className={styles.previewSubtitleLine} />
                </span>
              </div>
              <div className={styles.previewMetrics}>
                <div className={styles.previewMetric}>
                  <HeroSkeleton className={styles.metricLine} />
                  <HeroSkeleton className={styles.metricValue} />
                </div>
                <div className={styles.previewMetric}>
                  <span className={`${styles.metricDot} ${styles.orangeDot}`} />
                  <HeroSkeleton className={styles.metricLine} />
                  <HeroSkeleton className={styles.metricValue} />
                </div>
                <div className={styles.previewMetric}>
                  <span className={`${styles.metricDot} ${styles.blueDot}`} />
                  <HeroSkeleton className={styles.metricLine} />
                  <HeroSkeleton className={styles.metricValue} />
                </div>
              </div>
            </div>
            <div className={`${styles.workflowNode} ${styles.completeNode}`}>
              <span className={`${styles.previewIcon} ${styles.usersIcon}`}>
                <UserCheck size={18} />
              </span>
              <span className={styles.previewTitles}>
                <span className={styles.previewTitle}>Active Users</span>
                <HeroSkeleton className={styles.previewCount} />
              </span>
            </div>
          </div>
        </div>
      </section>

      <section className={styles.signIn} aria-labelledby="sign-in-title">
        <div className={styles.signInHeader}>
          <ThemeToggle />
        </div>
        <div className={styles.signInBody}>
          <div className={styles.welcomeMark}>
            <Image
              src={horse}
              alt=""
              width={36}
              height={31}
              className={styles.themeHorse}
            />
          </div>
          <p className={styles.signInEyebrow}>LITTLEHORSE BUSINESS CONSOLE</p>
          <h2 id="sign-in-title">Welcome back.</h2>
          {error && (
            <p className={styles.error} role="alert">
              We couldn’t sign you in. Please try again. If the problem
              continues, contact your administrator.
            </p>
          )}
          <form action={handleSignIn} className={styles.form}>
            <SignInButton />
          </form>
          <p className={styles.security}>
            <LockKeyhole size={14} aria-hidden="true" /> Secure single sign-on
            with Keycloak
          </p>
          <div className={styles.help}>
            <span>Need access?</span>
            <p>
              Contact your organization’s administrator
              <br className={styles.helpBreak} /> to get started.
            </p>
          </div>
        </div>
        <footer className={styles.footer}>
          <span>© {new Date().getFullYear()} LittleHorse Enterprises LLC</span>
          <ArrowRight size={15} aria-hidden="true" />
        </footer>
      </section>
    </main>
  );
}
