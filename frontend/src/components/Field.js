export default function Field({ label, error, ...props }) {
  return (
    <label className="ui-field flex flex-col gap-3 m-[22px_0] text-[14px] [&_input]:w-full [&_input]:h-12.5 [&_input]:[border:1px_solid_var(--line)] [&_input]:rounded-lg [&_input]:bg-white [&_input]:p-[0_16px] [&_input]:text-ink [&_select]:w-full [&_select]:h-12.5 [&_select]:[border:1px_solid_var(--line)] [&_select]:rounded-lg [&_select]:bg-white [&_select]:p-[0_16px] [&_select]:text-ink">
      <span>{label}</span>
      <input {...props} aria-invalid={error ? true : undefined} aria-describedby={error ? `${props.name}-error` : undefined} />
      {error && <span id={`${props.name}-error`} role="alert" className="text-sm text-red-600">{error}</span>}
    </label>
  );
}
