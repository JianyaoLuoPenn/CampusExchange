import axios from "axios";
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "/api/campus",
  timeout: 20000,
});
api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem("campus-token");
  if (token && !config.url?.startsWith("/auth/"))
    config.headers.Authorization = `Bearer ${token}`;
  return config;
});
api.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const config = error.config;
      const hadToken = Boolean(config?.headers.get("Authorization"));
      sessionStorage.removeItem("campus-token");
      sessionStorage.removeItem("campus-user");
      window.dispatchEvent(new Event("campus-session-expired"));
      // An expired token must not make public browsing or the mode banner look offline.
      if (
        hadToken &&
        config?.method === "get" &&
        (config.url === "/config" || config.url?.startsWith("/listings"))
      ) {
        config.headers.delete("Authorization");
        return api.request(config);
      }
    }
    return Promise.reject(error);
  },
);
export function errorText(error: unknown): string {
  return axios.isAxiosError(error)
    ? error.response?.data?.message ||
        (error.response?.status === 401
          ? "Please sign in again."
          : "Unable to complete the request. Check the server and try again.")
    : "Something went wrong. Please try again.";
}
