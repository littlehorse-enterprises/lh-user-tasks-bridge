import Providers from "@/app/providers";
import { cn } from "@/lib/utils";
import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";

export const metadata: Metadata = {
  title: "LittleHorse SSO Workflow Bridge",
  description: "LittleHorse SSO Workflow Bridge",
};

const inter = Inter({
  subsets: ["latin"],
  display: "swap",
  variable: "--font-inter",
});

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={cn(inter.variable, "flex flex-col min-h-screen")}>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
