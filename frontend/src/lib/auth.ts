export type AuthUser = {
  id: string;
  username: string;
  email: string;
};

export type AuthResult = {
  accessToken: string;
  user: AuthUser;
};

const TOKEN_KEY = "projectflow_access_token";
const USER_KEY = "projectflow_user";

export function saveSession(auth: AuthResult) {
  window.localStorage.setItem(TOKEN_KEY, auth.accessToken);
  window.localStorage.setItem(USER_KEY, JSON.stringify(auth.user));
}

export function readSession(): AuthResult | null {
  const accessToken = window.localStorage.getItem(TOKEN_KEY);
  const userJson = window.localStorage.getItem(USER_KEY);
  if (!accessToken || !userJson) {
    return null;
  }

  try {
    if (isJwtExpired(accessToken)) {
      clearSession();
      return null;
    }
    return {
      accessToken,
      user: JSON.parse(userJson) as AuthUser,
    };
  } catch {
    clearSession();
    return null;
  }
}

export function clearSession() {
  window.localStorage.removeItem(TOKEN_KEY);
  window.localStorage.removeItem(USER_KEY);
}

function isJwtExpired(token: string) {
  const [, payloadSegment] = token.split(".");
  if (!payloadSegment) {
    return true;
  }
  try {
    const normalized = payloadSegment.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    const payload = JSON.parse(window.atob(padded)) as { exp?: number };
    if (!payload.exp) {
      return true;
    }
    return payload.exp * 1000 <= Date.now();
  } catch {
    return true;
  }
}
