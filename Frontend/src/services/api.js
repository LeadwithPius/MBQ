const API_BASE = (import.meta.env.VITE_API_BASE_URL || "/api").replace(/\/+$/, "");
const SESSION_KEY = "mbq.session";

export function readSession() {
  try {
    const value = sessionStorage.getItem(SESSION_KEY);
    if (!value) return null;
    const session = JSON.parse(value);
    if (!session?.accessToken || !session?.expiresAt || Date.now() >= session.expiresAt) {
      sessionStorage.removeItem(SESSION_KEY);
      return null;
    }
    return session;
  } catch {
    sessionStorage.removeItem(SESSION_KEY);
    return null;
  }
}

export function saveSession(session) {
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function clearSession() {
  sessionStorage.removeItem(SESSION_KEY);
  sessionStorage.removeItem("mbq.adminDepartment");
}

export async function api(path, options = {}) {
  const { auth = true, headers: suppliedHeaders, ...requestOptions } = options;
  const session = readSession();
  const headers = new Headers(suppliedHeaders || {});
  const isForm = typeof FormData !== "undefined" && requestOptions.body instanceof FormData;
  if (requestOptions.body && !isForm) headers.set("Content-Type", "application/json");
  if (auth && session?.accessToken) headers.set("Authorization", "Bearer " + session.accessToken);

  let response;
  try {
    response = await fetch(API_BASE + path, {
      ...requestOptions,
      headers,
      body: requestOptions.body && typeof requestOptions.body !== "string" && !isForm
        ? JSON.stringify(requestOptions.body)
        : requestOptions.body,
    });
  } catch {
    throw new Error("Unable to reach the MBQ server. Check that the backend is running.");
  }

  if (response.status === 401 && auth) {
    clearSession();
    window.dispatchEvent(new Event("mbq:unauthorized"));
  }

  if (response.status === 204) return null;
  const contentType = response.headers.get("content-type") || "";
  const payload = contentType.includes("application/json")
    ? await response.json()
    : await response.text();

  if (!response.ok) {
    const message = typeof payload === "string"
      ? payload
      : payload?.detail || payload?.message || payload?.error || "Request failed (" + response.status + ")";
    throw new Error(message);
  }
  return payload;
}
