export default function Notice({ title, children }) {
  return (
    <aside className="notice">
      <span aria-hidden="true">ⓘ</span>
      <div>
        {title && <strong>{title}</strong>}
        {children && <p>{children}</p>}
      </div>
    </aside>
  );
}
