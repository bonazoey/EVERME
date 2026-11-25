"use client";

import { useEffect, useId, useRef } from "react";

export default function EvermeMascot({ animated = true, thinking = false }) {
  const clipId = useId();
  const eyesRef = useRef(null);
  const faceRef = useRef(null);
  const laughRef = useRef(null);
  const robotRef = useRef(null);
  const thoughtRef = useRef(null);

  useEffect(() => {
    if (!animated || !thinking) return;
    const preference = window.matchMedia("(prefers-reduced-motion: reduce)");
    let motion;
    function syncMotion() {
      motion?.cancel();
      if (!preference.matches) {
        motion = thoughtRef.current?.animate(
          [
            { transform: "translateY(0) scale(1, 1)" },
            { transform: "translateY(-2px) scale(1.035, 0.98)", offset: 0.4 },
            { transform: "translateY(-1px) scale(0.985, 1.025)", offset: 0.7 },
            { transform: "translateY(0) scale(1, 1)" },
          ],
          { duration: 3200, iterations: Infinity, easing: "ease-in-out" },
        );
      }
    }
    syncMotion();
    preference.addEventListener("change", syncMotion);
    return () => {
      motion?.cancel();
      preference.removeEventListener("change", syncMotion);
    };
  }, [animated, thinking]);

  useEffect(() => {
    if (!animated || thinking) return;
    const preference = window.matchMedia("(prefers-reduced-motion: reduce)");
    let timer;
    let animations = [];

    function stop() {
      window.clearTimeout(timer);
      animations.forEach((animation) => animation?.cancel());
      animations = [];
    }

    function schedule() {
      timer = window.setTimeout(
        () => {
          animations.forEach((animation) => animation?.cancel());
          const motion = Math.floor(Math.random() * 3);
          if (motion === 2) {
            const options = { duration: 1500, easing: "ease-in-out" };
            animations = [
              faceRef.current?.animate(
                [
                  { opacity: 1 },
                  { opacity: 0, offset: 0.12 },
                  { opacity: 0, offset: 0.88 },
                  { opacity: 1 },
                ],
                options,
              ),
              laughRef.current?.animate(
                [
                  { opacity: 0 },
                  { opacity: 1, offset: 0.12 },
                  { opacity: 1, offset: 0.88 },
                  { opacity: 0 },
                ],
                options,
              ),
              robotRef.current?.animate(
                [
                  { transform: "translateY(0)" },
                  { transform: "translateY(-1.5px)" },
                  { transform: "translateY(0)" },
                ],
                { duration: 300, iterations: 5, easing: "ease-in-out" },
              ),
            ];
            schedule();
            return;
          }
          const blink = motion === 0;
          const x = (Math.random() > 0.5 ? 1 : -1) * (1.5 + Math.random());
          const y = Math.random() - 0.5;
          animations = [
            eyesRef.current?.animate(
              blink
                ? [
                    { transform: "scaleY(1)", offset: 0 },
                    { transform: "scaleY(0.12)", offset: 0.4 },
                    { transform: "scaleY(0.12)", offset: 0.6 },
                    { transform: "scaleY(1)", offset: 1 },
                  ]
                : [
                    { transform: "translate(0, 0)", offset: 0 },
                    { transform: `translate(${x}px, ${y}px)`, offset: 0.2 },
                    { transform: `translate(${x}px, ${y}px)`, offset: 0.75 },
                    { transform: "translate(0, 0)", offset: 1 },
                  ],
              {
                duration: blink ? 220 : 1400 + Math.random() * 1000,
                easing: "ease-in-out",
              },
            ),
          ];
          schedule();
        },
        3000 + Math.random() * 2000,
      );
    }

    function syncMotion() {
      stop();
      if (!preference.matches && !document.hidden) schedule();
    }

    syncMotion();
    preference.addEventListener("change", syncMotion);
    document.addEventListener("visibilitychange", syncMotion);
    return () => {
      stop();
      preference.removeEventListener("change", syncMotion);
      document.removeEventListener("visibilitychange", syncMotion);
    };
  }, [animated, thinking]);
  return (
    <svg
      width="36"
      height="36"
      viewBox={thinking ? "0 -16 88 82" : "0 0 64 64"}
      className="h-auto w-full text-green"
      aria-hidden="true"
    >
      <defs>
        <clipPath id={clipId}>
          <circle cx="32" cy="32" r="30" />
        </clipPath>
      </defs>
      <circle cx="32" cy="32" r="30" className="fill-green-light" />
      <g clipPath={`url(#${clipId})`}>
        <g ref={robotRef}>
          <rect
            x="19"
            y="54"
            width="26"
            height="35"
            rx="10"
            fill="currentColor"
          />
          <path
            d="M18 57 13 69 M46 57 51 69"
            stroke="currentColor"
            strokeWidth="6"
            strokeLinecap="round"
          />
          <path
            d="M32 31V24"
            stroke="currentColor"
            strokeWidth="2.5"
            strokeLinecap="round"
          />
          <path
            d="M32 25C22 27 19 19 20 15 29 14 34 20 32 25Z"
            fill="currentColor"
          />
          <path
            d="M32 25C32 16 39 12 46 15 45 23 39 27 32 25Z"
            fill="currentColor"
            opacity="0.6"
          />
          <rect
            x="13"
            y="29"
            width="38"
            height="29"
            rx="11"
            fill="currentColor"
          />
          <rect
            x="18"
            y="35"
            width="28"
            height="17"
            rx="7"
            className="fill-surface"
          />
          <g ref={faceRef}>
            <g
              ref={eyesRef}
              fill="currentColor"
              className="[transform-box:fill-box] origin-center"
            >
              <rect
                x={thinking ? 25 : 24}
                y={thinking ? 37 : 40}
                width="3"
                height="6"
                rx="1.5"
              />
              <rect
                x={thinking ? 38 : 37}
                y={thinking ? 37 : 40}
                width="3"
                height="6"
                rx="1.5"
              />
            </g>
            <path
              d={thinking ? "M30 49Q32 47 34 48" : "M30 48Q32 50 34 48"}
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
            />
          </g>
          <g ref={laughRef} opacity="0" fill="currentColor">
            <path
              d="M23 43Q25.5 38 28 43M36 43Q38.5 38 41 43"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
            />
            <path d="M27 46H37Q37 52 32 52Q27 52 27 46Z" />
          </g>
        </g>
      </g>
      {thinking && (
        <g transform="translate(6 -5)">
          <g
            ref={thoughtRef}
            className="fill-surface origin-center [transform-box:fill-box]"
            stroke="currentColor"
            strokeWidth="1.5"
          >
            <circle cx="46" cy="29" r="1.5" />
            <circle cx="51" cy="24" r="2.5" />
            <path d="M47 9C47 3 53 0 59 2C65-2 73 2 73 7C79 10 76 18 70 18C65 23 58 20 56 18C49 20 43 14 47 9Z" />
            <g fill="currentColor" stroke="none">
              <circle cx="55" cy="10" r="1" />
              <circle cx="61" cy="10" r="1" />
              <circle cx="67" cy="10" r="1" />
            </g>
          </g>
        </g>
      )}
    </svg>
  );
}
