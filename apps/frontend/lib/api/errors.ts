export class ApiError extends Error {
  readonly status: number;
  readonly requestId: string | undefined;
  readonly code: string | undefined;

  constructor(status: number, requestId?: string, code?: string) {
    super("The request could not be completed.");
    this.name = "ApiError";
    this.status = status;
    this.requestId = requestId;
    this.code = code;
  }
}

export class NetworkError extends Error {
  constructor() {
    super("Unable to reach the service. Please try again.");
    this.name = "NetworkError";
  }
}

export class ApiResponseError extends Error {
  readonly requestId: string | undefined;
  constructor(requestId?: string) {
    super("The service returned an unreadable response.");
    this.name = "ApiResponseError";
    this.requestId = requestId;
  }
}
