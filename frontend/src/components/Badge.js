import badgeStyles from "./Badge.module.css";
import classNames from "../lib/classNames.js";
const styles = {
  ...badgeStyles,
};
export default function Badge({ children, tone = "" }) {
  return (
    <span className={classNames(`badge ${tone}`, styles)}>{children}</span>
  );
}
