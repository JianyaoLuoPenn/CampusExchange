import { createContext, useContext, useState } from 'react';
import type { ReactNode } from 'react';
import type { User } from './types';
const Context = createContext<{user:User|null; signIn:(token:string,user:User)=>void; signOut:()=>void}>({user:null,signIn:()=>{},signOut:()=>{}});
export const useAuth = () => useContext(Context);
export function AuthProvider({children}:{children:ReactNode}) {
  const [user,setUser]=useState<User|null>(()=>{try{return JSON.parse(sessionStorage.getItem('campus-user')||'null');}catch{return null;}});
  return <Context.Provider value={{user,signIn:(token,u)=>{sessionStorage.setItem('campus-token',token);sessionStorage.setItem('campus-user',JSON.stringify(u));setUser(u);},signOut:()=>{sessionStorage.removeItem('campus-token');sessionStorage.removeItem('campus-user');setUser(null);}}}>{children}</Context.Provider>;
}
