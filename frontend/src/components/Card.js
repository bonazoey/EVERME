import cardStyles from "./Card.module.css";
import classNames from "../lib/classNames.js";
const styles = {
  ...cardStyles,
};
export default function Card({ children, className = "" }) {
  return (
    <section className={classNames(`card ${className}`, styles)}>
      {children}
    </section>
  );
}
