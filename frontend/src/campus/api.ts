import axios from "axios";
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "http://localhost:8080/api/campus",
  timeout: 20000,
});
api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem("campus-token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
export function errorText(error: unknown): string {
  return axios.isAxiosError(error)
    ? error.response?.data?.message ||
        (error.response?.status === 401
          ? "Please sign in again."
          : "Unable to complete the request. Check the server and try again.")
    : "Something went wrong. Please try again.";
}
