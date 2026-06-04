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
