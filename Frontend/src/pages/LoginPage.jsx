import { useState } from "react";
import { Activity, ArrowLeft, Eye, EyeOff, LockKeyhole } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      await login(username.trim(), password);
      navigate("/app", { replace: true });
    } catch (err) {
      setError(err.message || "Unable to sign in. Check your credentials.");
    } finally {
      setBusy(false);
    }
  }

  return <main className="login-page">
    <Link className="back-home" to="/"><ArrowLeft size={16} /> Back to MBQ</Link>
    <section className="login-card">
      <Link className="login-brand" to="/"><span className="brand-symbol"><Activity size={23} /></span><span><b>MBQ</b><small>Hospital Operations</small></span></Link>
      <span className="eyebrow">STAFF ACCESS</span>
      <h1>Welcome back.</h1>
      <p className="login-subtitle">Sign in with your hospital-issued account to continue.</p>
      <form onSubmit={submit}>
        <label htmlFor="username">Username</label>
        <input id="username" autoComplete="username" value={username} onChange={e => setUsername(e.target.value)} placeholder="Enter your username" required />
        <label htmlFor="password">Password</label>
        <div className="password-field"><input id="password" type={showPassword ? "text" : "password"} autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} placeholder="Enter your password" required /><button type="button" aria-label={showPassword ? "Hide password" : "Show password"} onClick={() => setShowPassword(v => !v)}>{showPassword ? <EyeOff size={18} /> : <Eye size={18} />}</button></div>
        {error && <div className="form-error" role="alert">{error}</div>}
        <button className="button button-primary login-submit" disabled={busy}>{busy ? "Signing in..." : <><LockKeyhole size={17} /> Sign in securely</>}</button>
      </form>
      <div className="login-security"><LockKeyhole size={15} /><span>Your session is protected by secure token-based authentication.</span></div>
    </section>
    <span className="login-copyright">MBQ · Hospital Operations</span>
  </main>;
}
