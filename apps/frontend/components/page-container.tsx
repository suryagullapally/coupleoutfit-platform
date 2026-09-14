import type { ReactNode } from "react";

export function PageContainer({ children }: { children: ReactNode }) {
  return <div className="mx-auto w-full max-w-content px-6 sm:px-10 lg:px-16">{children}</div>;
}
