/**
 * Converts an object to URLSearchParams
 * @param params - Object to convert to URLSearchParams
 * @returns URLSearchParams instance
 */
export function objectToURLSearchParams(
  params: Record<string, any>,
): URLSearchParams {
  const queryParams = new URLSearchParams();

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      queryParams.append(key, String(value));
    }
  });

  return queryParams;
}
