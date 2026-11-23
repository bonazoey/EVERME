const badgeTones = {
  amber: "ui-badge-amber",
  blue: "ui-badge-blue",
};
export default function Badge({ children, tone = "" }) {
  return (
    <span
      className={`ui-badge inline-flex items-center justify-center bg-green-light text-green text-[13px] rounded-[7px] p-[4px_12px] whitespace-nowrap [&.ui-badge-amber]:[background:#fff6e7] [&.ui-badge-amber]:[color:#a16b17] [&.ui-badge-blue]:[background:#edf2ff] [&.ui-badge-blue]:[color:#496aa8] ${badgeTones[tone] ?? ""}`}
    >
      {children}
    </span>
  );
}
