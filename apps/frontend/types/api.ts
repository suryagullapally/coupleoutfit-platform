export interface ApiResponse<T> {
  readonly data: T | undefined;
  readonly status: number;
  readonly requestId: string | undefined;
}

export type ApiRequestOptions = Omit<RequestInit, "body" | "headers" | "credentials"> & {
  json?: unknown;
  headers?: HeadersInit;
};
