import type { AuthResult } from "./auth";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

type ApiResponse<T> = {
  data: T;
  message: string;
};

type ApiErrorResponse = {
  error?: {
    code?: string;
    message?: string;
  };
};

async function postJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });

  const payload = (await response.json()) as ApiResponse<T> & ApiErrorResponse;
  if (!response.ok) {
    throw new Error(payload.error?.message ?? "请求失败，请稍后重试");
  }
  return payload.data;
}

export function login(email: string, password: string): Promise<AuthResult> {
  return postJson<AuthResult>("/auth/login", { email, password });
}

export function register(username: string, email: string, password: string): Promise<AuthResult> {
  return postJson<AuthResult>("/auth/register", { username, email, password });
}
