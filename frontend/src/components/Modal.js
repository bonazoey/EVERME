import modalStyles from "./Modal.module.css";
import classNames from "../lib/classNames.js";
import Link from "next/link";
const styles = {
  ...modalStyles,
};
export default function Modal({ title, closeHref, children, drawer = false }) {
  return (
    <div className={classNames("overlay", styles)}>
      <section
        className={classNames(drawer ? "modal drawer" : "modal", styles)}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <header>
          <h2>{title}</h2>
          <Link
            className={classNames("icon-button", styles)}
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
