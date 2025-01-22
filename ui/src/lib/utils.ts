import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function getRoles(obj: any) {
  const splitPaths = process.env.AUTHORITIES?.split(",") ?? [];
  const paths = splitPaths.map((path) => path.split(".").slice(1));

  const roles = paths.flatMap((pathArray) => {
    return pathArray.reduce(
      (current, key) => {
        if (key === "*") {
          return current.flatMap((item) => {
            if (typeof item === "object" && item !== null) {
              return Object.values(item);
            }
            return [];
          });
        } else {
          return current.flatMap((item) => item[key]).filter(Boolean);
        }
      },
      [obj],
    );
  });

  return roles;
}
