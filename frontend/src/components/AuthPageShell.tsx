import { AuthPanel } from "./AuthPanel";

type AuthPageShellProps = {
  mode: "login" | "register";
};

export function AuthPageShell({ mode }: AuthPageShellProps) {
  return (
    <main className="relative min-h-screen overflow-hidden bg-[#020817] text-white">
      <div
        className="absolute inset-0 bg-cover bg-center"
        style={{ backgroundImage: "url('/assets/login-background.png')" }}
      />
      <div className="absolute inset-0 bg-gradient-to-r from-transparent via-[#03142a]/10 to-[#020817]/30" />

      <section className="relative z-10 flex min-h-screen items-center justify-end px-8 py-10 lg:px-20">
        <AuthPanel mode={mode} />
      </section>
    </main>
  );
}
