import { useState } from 'react';
import type { FormEvent } from 'react';
import { Alert,Button,TextField } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { api,errorText } from './api';
import { useAuth } from './Auth';
export default function SignIn(){const [signup,setSignup]=useState(false),[error,setError]=useState(''),[busy,setBusy]=useState(false);const auth=useAuth(),navigate=useNavigate();
 async function submit(e:FormEvent<HTMLFormElement>){e.preventDefault();setBusy(true);const f=new FormData(e.currentTarget);try{const r=await api.post('/auth/'+(signup?'signup':'login'),{email:f.get('email'),password:f.get('password'),...(signup?{fullName:f.get('name')}:{})});auth.signIn(r.data.token,r.data.user);navigate('/');}catch(e){setError(errorText(e));}finally{setBusy(false);}}
 return <section className="form-shell"><div className="eyebrow">BUY. SELL. BELONG.</div><h1>{signup?'Join the neighborhood':'Welcome back'}</h1><p>One account to browse, buy, and list your own items.</p>{error&&<Alert severity="error">{error}</Alert>}<form onSubmit={submit}>{signup&&<TextField label="Full name" name="name" required inputProps={{maxLength:100}}/>}<TextField label="Email" name="email" type="email" required autoComplete="email"/><TextField label="Password" name="password" type="password" required inputProps={{minLength:signup?10:1,maxLength:72}} autoComplete={signup?'new-password':'current-password'}/><Button type="submit" variant="contained" disabled={busy}>{signup?'Create account':'Sign in'}</Button><Button onClick={()=>{setSignup(!signup);setError('');}}>{signup?'Already have an account? Sign in':'New here? Create an account'}</Button></form><p className="muted">For the opt-in fictional demo, use alex@example.test / CampusDemo123!</p></section>;
}
