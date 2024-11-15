import type { Metadata } from "next";
import "./globals.css";

import Providers from "@/app/providers";
import { Toaster } from "@/components/ui/sonner";
import { cn } from "@/lib/utils";
import { Inter } from "next/font/google";
import Footer from "./components/footer";
import Header from "./components/header";

export const metadata: Metadata = {
  title: "LittleHorse UserTasks",
  description: "LittleHorse UserTasks",
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
        <Providers>
          <Header />
          <main className="px-4 md:px-8 lg:px-16 py-4">{children}</main>
          <Footer />
        </Providers>
        <Toaster position="top-center" richColors />
      </body>
    </html>
  );
}
