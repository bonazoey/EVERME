import Link from "next/link";
export default function Modal({ title, closeHref, children, drawer = false }) {
  return (
    <div className="overlay">
      <section
        className={drawer ? "modal drawer" : "modal"}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <header>
          <h2>{title}</h2>
          <Link
            className="icon-button"
            href={closeHref}
            aria-label="닫기"
            autoFocus
          >
            ×
          </Link>
        </header>
        {children}
      </section>
    </div>
  );
}
