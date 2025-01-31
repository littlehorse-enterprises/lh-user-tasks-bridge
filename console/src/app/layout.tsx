import type { Metadata } from "next";
import "./globals.css";

import Providers from "@/app/providers";
import { Toaster } from "@/components/ui/sonner";
import { cn } from "@/lib/utils";
import { Inter } from "next/font/google";

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
    <html lang="en">
      <body className={cn(inter.variable, "flex flex-col min-h-screen")}>
        <Providers>{children}</Providers>
        <Toaster position="top-center" richColors />
      </body>
    </html>
  );
}
