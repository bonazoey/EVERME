import buttonStyles from "./Button.module.css";
import classNames from "../lib/classNames.js";
import Link from "next/link";
const styles = {
  ...buttonStyles,
};
export default function Button({
  href,
  children,
  variant = "primary",
  className = "",
  ...props
}) {
  const buttonClasses = `button button--${variant} ${className}`;
  return href ? (
    <Link className={classNames(buttonClasses, styles)} href={href} {...props}>
      {children}
    </Link>
  ) : (
    <button
      className={classNames(buttonClasses, styles)}
      type="button"
      {...props}
    >
      {children}
    </button>
  );
}
