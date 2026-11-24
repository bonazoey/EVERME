export default function Card({ children, className = "" }) {
  return (
    <section
      className={`ui-card [border:1px_solid_var(--line)] rounded-xl p-6 bg-white m-[24px_0] [&_:where(h2)]:mb-6 [@media(max-width:_760px)]:p-5 ${className}`}
    >
      {children}
    </section>
  );
}
