import noticeStyles from "./Notice.module.css";
import classNames from "../lib/classNames.js";
const styles = {
  ...noticeStyles,
};
export default function Notice({ title, children }) {
  return (
    <aside className={classNames("notice", styles)}>
      <span aria-hidden="true">ⓘ</span>
      <div>
        {title && <strong>{title}</strong>}
        {children && <p>{children}</p>}
      </div>
    </aside>
  );
}
