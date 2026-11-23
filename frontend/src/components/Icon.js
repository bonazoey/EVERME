const paths = {
  "arrow-right": "M5 12h14m-6-6 6 6-6 6",
  "refresh-single": "M20 4v5h-5M20 9a8 8 0 1 0 0 7",
  copy: "M9 8h10v13H9V8ZM15 8V3H4v13h5",
  refresh: "M20 3v5h-5M4 21v-5h5M19.5 8A8 8 0 0 0 5 6M4.5 16A8 8 0 0 0 19 18",
  edit: "m15 4 5 5M4 20l4-1L20 7a2.1 2.1 0 0 0-3-3L5 16l-1 4Z",
  trash: "M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7M14 10v7",
  robot:
    "M12 3v3M8 6h8a4 4 0 0 1 4 4v6a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4v-6a4 4 0 0 1 4-4ZM9 12v2M15 12v2",
  "chevron-up": "m6 15 6-6 6 6",
  home: "m3 10 9-7 9 7M5 9v12h14V9M9 21v-8h6v8",
  logout: "M9 4H4v16h5M13 8l4 4-4 4M8 12h13",
  chat: "M4 4h16v12H9l-5 4V4Z",
  user: "M5 21v-3a7 7 0 0 1 14 0v3M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0",
  users:
    "M3 21v-3a6 6 0 0 1 12 0v3M12 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0M16 4a4 4 0 0 1 0 8M18 15a5 5 0 0 1 3 5",
  settings:
    "M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0",
  mic: "M9 5a3 3 0 0 1 6 0v7a3 3 0 0 1-6 0V5ZM6 10v2a6 6 0 0 0 12 0v-2M12 18v4M8 22h8",
  send: "m3 3 18 9-18 9 4-9-4-9ZM7 12h14",
};
export default function Icon({ name, size = 20 }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name] || paths.chat} />
    </svg>
  );
}
