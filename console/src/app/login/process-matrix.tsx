import styles from "./login.module.css";

const planes = [
  { id: "far", width: 101, height: 94, size: 30, className: styles.matrixFar },
  {
    id: "middle",
    width: 137,
    height: 121,
    size: 46,
    className: styles.matrixMiddle,
  },
  {
    id: "near",
    width: 183,
    height: 150,
    size: 68,
    className: styles.matrixNear,
  },
] as const;

export default function ProcessMatrix() {
  return (
    <div className={styles.processMatrix} aria-hidden="true">
      <div className={styles.matrixTilt}>
        {planes.map(({ id, width, height, size, className }) => (
          <div key={id} className={`${styles.matrixMotion} ${className}`}>
            <svg
              className={styles.matrixTexture}
              width="100%"
              height="100%"
              fill="none"
              focusable="false"
            >
              <defs>
                <pattern
                  id={`business-matrix-${id}`}
                  width={width}
                  height={height}
                  patternUnits="userSpaceOnUse"
                >
                  <g className={styles.matrixEdges}>
                    <path d={`M${size} ${size / 3}H${width}`} />
                    <path d={`M${size / 2} ${(size * 2) / 3}V${height}`} />
                  </g>
                  <g transform={`scale(${size / 60})`}>
                    <rect
                      x="0.5"
                      y="0.5"
                      width="59"
                      height="39"
                      rx="7"
                      fill="var(--card)"
                      stroke="var(--muted-foreground)"
                      strokeWidth="0.8"
                    />
                    <rect
                      x="9"
                      y="11"
                      width="15"
                      height="17"
                      rx="4"
                      fill="var(--primary)"
                      fillOpacity="0.12"
                      stroke="var(--amber-600)"
                      strokeOpacity="0.7"
                      strokeWidth="0.7"
                    />
                    <path
                      d="m13 19 3 3 5-6"
                      stroke="var(--amber-600)"
                      strokeWidth="1.4"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                    <path
                      d="M31 18h16M31 24h10"
                      stroke="var(--muted-foreground)"
                      strokeOpacity="0.8"
                      strokeWidth="1.5"
                      strokeLinecap="round"
                    />
                    <circle cx="49" cy="11" r="2.5" fill="var(--primary)" />
                  </g>
                </pattern>
              </defs>
              <rect
                width="100%"
                height="100%"
                fill={`url(#business-matrix-${id})`}
              />
            </svg>
          </div>
        ))}
      </div>
    </div>
  );
}
