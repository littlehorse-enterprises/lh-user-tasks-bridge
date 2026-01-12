export const register = async () => {
  if (
    process.env.NEXT_RUNTIME === "nodejs" &&
    process.env.LHUT_METRICS_DISABLED?.toLowerCase() !== "true"
  ) {
    await import("./instrumentation-node");
  }
};
