export type AuthUser = {
  id: string;
  username: string;
  email: string;
};

export type AuthResult = {
  accessToken: string;
  user: AuthUser;
};

const LOCAL_SESSION: AuthResult = {
  accessToken: "local-user",
  user: {
    id: "local-user",
    username: "本地用户",
    email: "",
  },
};

export function saveSession(_: AuthResult) {
  // 本地单用户模式不保存登录会话。
}

export function readSession(): AuthResult {
  return LOCAL_SESSION;
}

export function clearSession() {
  // 本地单用户模式没有可清理的登录会话。
}
