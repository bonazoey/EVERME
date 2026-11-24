export default function Notice({ title, children }) {
  return (
    <aside className="flex gap-4 bg-surface rounded-[10px] p-[20px_24px] m-[24px_0] [&_>_span]:text-green [&_>_span]:text-[20px] [&_strong]:font-semibold [&_p]:text-[14px] [&_p]:text-muted [&_p]:m-[6px_0_0]">
      <span aria-hidden="true">ⓘ</span>
      <div>
        {title && <strong>{title}</strong>}
        {children && <p>{children}</p>}
      </div>
    </aside>
  );
}
