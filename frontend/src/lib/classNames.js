// Keep stable hooks only where a parent module styles another component.
const componentHooks = new Set([
  "badge",
  "brand",
  "button",
  "field",
  "muted",
  "card",
  "avatar",
  "text-link",
]);
export default function classNames(value, styles) {
  return String(value ?? "")
    .split(/\s+/)
    .filter(Boolean)
    .flatMap((name) => {
      const scoped = styles[name];
      return scoped
        ? componentHooks.has(name)
          ? [name, scoped]
          : [scoped]
        : [name];
    })
    .join(" ");
}
