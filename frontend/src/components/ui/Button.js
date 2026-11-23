import Link from "next/link";
export default function Button({
  href,
  children,
  variant = "primary",
  className = "",
  ...props
}) {
  const styles = `button button--${variant} ${className}`;
  return href ? (
    <Link className={styles} href={href} {...props}>
      {children}
    </Link>
  ) : (
    <button className={styles} type="button" {...props}>
      {children}
    </button>
  );
}
