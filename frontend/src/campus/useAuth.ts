import { createContext, useContext } from "react";
import type { User } from "./types";
export const AuthContext = createContext<{
  user: User | null;
  signIn: (token: string, user: User) => void;
  signOut: () => void;
}>({ user: null, signIn: () => {}, signOut: () => {} });
export const useAuth = () => useContext(AuthContext);
