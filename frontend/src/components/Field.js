import fieldStyles from "./Field.module.css";
import classNames from "../lib/classNames.js";
const styles = {
  ...fieldStyles,
};
export default function Field({ label, ...props }) {
  return (
    <label className={classNames("field", styles)}>
      <span>{label}</span>
      <input {...props} />
    </label>
  );
}
