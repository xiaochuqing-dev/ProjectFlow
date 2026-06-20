import type { ButtonHTMLAttributes, ReactNode } from "react";

/**
 * 共享 UI 原语库。
 *
 * 这些组件不是为了引入新抽象，而是提取 ProjectFlow 各页面里已经逐字重复的内联标记：
 * - Card        ← `rounded-md border border-line bg-white shadow-panel`（审计：28 处跨 9 文件）
 * - SectionHeader ← 各卡片顶部 `flex ... border-b px-5 py-4` 标题栏
 * - Button      ← `bg-slate-950 ... hover:bg-slate-800` 主操作（审计：大量）
 * - Badge       ← 合并 StatusChip(dashboard) + StatusPill(tasks/dev-logs) 两套副本
 * - Stat        ← MiniFact(dashboard) + Metric(work-sessions)
 * - Field       ← 各页重复的 `<label>span + input/textarea/select`
 * - EmptyState  ← EmptyProjectState + 4 处内联空状态
 *
 * 统一主操作色为 brand（靛蓝），替代此前的裸 slate-950。
 */

/* ------------------------------------------------------------------ */
/* Card                                                                */
/* ------------------------------------------------------------------ */

type CardProps = {
  children: ReactNode;
  className?: string;
  /** 内边距档位；none 用于内容由内部区块自行控制内边距的卡片 */
  padding?: "none" | "md" | "lg";
  /** 阴影层级：card 普通浮起，cardLg 强调浮起，none 平贴 */
  shadow?: "card" | "cardLg" | "none";
  as?: "section" | "article" | "div" | "form";
};

const cardPadding: Record<NonNullable<CardProps["padding"]>, string> = {
  none: "",
  md: "p-5",
  lg: "p-6",
};

export function Card({
  children,
  className = "",
  padding = "none",
  shadow = "card",
  as = "section",
}: CardProps) {
  const Tag = as;
  const shadowClass = shadow === "none" ? "" : shadow === "cardLg" ? "shadow-cardLg" : "shadow-card";
  return (
    <Tag className={`rounded-card border border-line bg-elevated ${shadowClass} ${cardPadding[padding]} ${className}`.trim()}>
      {children}
    </Tag>
  );
}

/* ------------------------------------------------------------------ */
/* SectionHeader                                                       */
/* ------------------------------------------------------------------ */

type SectionHeaderProps = {
  title: ReactNode;
  eyebrow?: ReactNode;
  subtitle?: ReactNode;
  icon?: ReactNode;
  actions?: ReactNode;
  className?: string;
};

export function SectionHeader({ title, eyebrow, subtitle, icon, actions, className = "" }: SectionHeaderProps) {
  return (
    <div className={`flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4 ${className}`.trim()}>
      <div className="flex min-w-0 items-center gap-2.5">
        {icon ? <span className="shrink-0 text-body">{icon}</span> : null}
        <div className="min-w-0">
          {eyebrow ? <p className="text-xs text-muted">{eyebrow}</p> : null}
          <h2 className="truncate text-base font-semibold text-ink">{title}</h2>
          {subtitle ? <p className="mt-0.5 text-sm text-muted">{subtitle}</p> : null}
        </div>
      </div>
      {actions ? <div className="flex shrink-0 items-center gap-2">{actions}</div> : null}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Button                                                              */
/* ------------------------------------------------------------------ */

type ButtonVariant = "primary" | "secondary" | "ghost" | "danger";
type ButtonSize = "sm" | "md";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  fullWidth?: boolean;
};

const buttonBase =
  "inline-flex items-center justify-center gap-2 rounded-field font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60 focus:outline-none focus-visible:shadow-focus";

const buttonVariant: Record<ButtonVariant, string> = {
  primary: "bg-brand text-white hover:bg-brand-hover",
  secondary: "border border-line bg-elevated text-body hover:bg-surfaceAlt hover:border-lineStrong",
  ghost: "text-muted hover:bg-surfaceAlt hover:text-ink",
  danger: "border border-danger/30 bg-danger/soft text-danger-fg hover:bg-danger-soft",
};

const buttonSize: Record<ButtonSize, string> = {
  sm: "h-9 px-3 text-xs",
  md: "h-10 px-4 text-sm",
};

export function Button({
  variant = "secondary",
  size = "md",
  loading = false,
  fullWidth = false,
  className = "",
  children,
  disabled,
  ...rest
}: ButtonProps) {
  return (
    <button
      className={`${buttonBase} ${buttonVariant[variant]} ${buttonSize[size]} ${fullWidth ? "w-full" : ""} ${className}`.trim().replace(/\s+/g, " ")}
      disabled={disabled || loading}
      type={rest.type ?? "button"}
      {...rest}
    >
      {children}
    </button>
  );
}

/* ------------------------------------------------------------------ */
/* Badge                                                               */
/* ------------------------------------------------------------------ */

type BadgeTone = "slate" | "brand" | "success" | "warning" | "danger";

type BadgeProps = {
  label: ReactNode;
  tone?: BadgeTone;
  dot?: boolean;
  className?: string;
};

const badgeTone: Record<BadgeTone, string> = {
  slate: "bg-surfaceAlt text-muted",
  brand: "bg-brand-soft text-brand",
  success: "bg-success-soft text-success-fg",
  warning: "bg-warning-soft text-warning-fg",
  danger: "bg-danger-soft text-danger-fg",
};

const badgeDot: Record<BadgeTone, string> = {
  slate: "bg-muted",
  brand: "bg-brand",
  success: "bg-success-fg",
  warning: "bg-warning-fg",
  danger: "bg-danger-fg",
};

export function Badge({ label, tone = "slate", dot = false, className = "" }: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-field px-2.5 py-1 text-xs font-medium ${badgeTone[tone]} ${className}`.trim().replace(/\s+/g, " ")}
    >
      {dot ? <span className={`h-1.5 w-1.5 rounded-full ${badgeDot[tone]}`} /> : null}
      {label}
    </span>
  );
}

/* ------------------------------------------------------------------ */
/* Stat                                                                */
/* ------------------------------------------------------------------ */

type StatProps = {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  tone?: BadgeTone;
  icon?: ReactNode;
};

export function Stat({ label, value, hint, tone = "slate", icon }: StatProps) {
  return (
    <div className="min-w-0 rounded-card border border-line bg-elevated p-4">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs text-muted">{label}</p>
        {icon ? <span className={`text-xs ${badgeTone[tone]}`}>{icon}</span> : null}
      </div>
      <p className="mt-1.5 break-all text-xl font-semibold leading-7 text-ink">{value}</p>
      {hint ? <p className="mt-0.5 text-xs text-muted">{hint}</p> : null}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Field                                                               */
/* ------------------------------------------------------------------ */

type FieldProps = {
  label: ReactNode;
  hint?: ReactNode;
  htmlFor?: string;
  children: ReactNode;
  className?: string;
};

export function Field({ label, hint, htmlFor, children, className = "" }: FieldProps) {
  return (
    <label htmlFor={htmlFor} className={`block ${className}`.trim()}>
      <span className="mb-1.5 block text-sm font-medium text-body">{label}</span>
      {children}
      {hint ? <span className="mt-1 block text-xs leading-5 text-muted">{hint}</span> : null}
    </label>
  );
}

/* ------------------------------------------------------------------ */
/* EmptyState                                                          */
/* ------------------------------------------------------------------ */

type EmptyStateProps = {
  icon?: ReactNode;
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
};

export function EmptyState({ icon, title, description, action, className = "" }: EmptyStateProps) {
  return (
    <div className={`grid place-items-center px-8 py-12 text-center ${className}`.trim()}>
      <div className="max-w-md">
        {icon ? <div className="mx-auto mb-3 grid h-11 w-11 place-items-center rounded-full bg-surfaceAlt text-muted">{icon}</div> : null}
        <h3 className="text-sm font-semibold text-ink">{title}</h3>
        {description ? <p className="mt-1.5 text-sm leading-6 text-muted">{description}</p> : null}
        {action ? <div className="mt-4">{action}</div> : null}
      </div>
    </div>
  );
}
