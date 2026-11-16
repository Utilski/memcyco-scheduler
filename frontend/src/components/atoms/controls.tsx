import type { ComponentProps, ReactNode } from "react";

export function Button({
  variant = "secondary",
  className = "",
  ...props
}: ComponentProps<"button"> & {
  variant?: "primary" | "secondary" | "danger" | "quiet";
}) {
  return (
    <button
      type="button"
      className={`button button-${variant} ${className}`}
      {...props}
    />
  );
}
export function Input(props: ComponentProps<"input">) {
  return <input {...props} />;
}
export function Select(props: ComponentProps<"select">) {
  return <select {...props} />;
}
export function Textarea(props: ComponentProps<"textarea">) {
  return <textarea rows={3} {...props} />;
}
export function Badge({
  children,
  tone = "neutral",
}: {
  children: ReactNode;
  tone?: "neutral" | "positive" | "muted";
}) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}
