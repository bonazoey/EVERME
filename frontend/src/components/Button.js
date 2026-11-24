const buttonVariants = {
  danger: "border-transparent bg-[#b83232] text-white",
  primary:
    "border-transparent bg-green text-white hover:bg-[color-mix(in_srgb,var(--green)_85%,black)]",
  secondary: "border-line bg-white text-green hover:bg-green-light",
};
import Link from "next/link";
export default function Button({
  href,
  children,
  variant = "primary",
  className = "",
  ...props
}) {
  return href ? (
    <Link
      className={`ui-button inline-flex items-center justify-center min-h-11.5 p-[10px_20px] border rounded-lg text-[15px] font-semibold cursor-pointer gap-2 [transition:background_0.15s] whitespace-nowrap ${buttonVariants[variant]} ${className}`}
      href={href}
      {...props}
    >
      {children}
    </Link>
  ) : (
    <button
      className={`ui-button inline-flex items-center justify-center min-h-11.5 p-[10px_20px] border rounded-lg text-[15px] font-semibold cursor-pointer gap-2 [transition:background_0.15s] whitespace-nowrap ${buttonVariants[variant]} ${className}`}
      type="button"
      {...props}
    >
      {children}
    </button>
  );
}
