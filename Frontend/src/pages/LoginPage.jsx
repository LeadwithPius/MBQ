import { useState } from "react";
import {
  ArrowLeft,
  Eye,
  EyeOff,
  LockKeyhole,
  ShieldCheck,
} from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/useAuth";

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

  return (
    <main className="mbq-login">
      <Link className="mbq-login-back" to="/">
        <ArrowLeft size={17} />
        Back to home
      </Link>

      {/* Branding panel */}
      <section className="mbq-login-visual">
        <div className="mbq-login-visual-overlay" />

        <Link to="/" className="mbq-login-logo">
          <img src="/mbq-logo.png" alt="MBQ Healthcare" />
        </Link>

        <div className="mbq-login-message">
          <span className="mbq-login-eyebrow">
            MANAGE. CARE. SERVE.
          </span>

          <h1>
            Better hospital
            <br />
            coordination.
            <br />
            <span>Better patient care.</span>
          </h1>

          <p>
            One connected workspace supporting patient registration,
            queue coordination and clinical triage.
          </p>

          <div className="mbq-login-trust">
            <ShieldCheck size={19} />
            <span>Designed for coordinated hospital operations</span>
          </div>
        </div>

        <div className="mbq-login-visual-footer">
          MBQ · HOSPITAL OPERATIONS
        </div>
      </section>

      {/* Login panel */}
      <section className="mbq-login-form-panel">
        <div className="mbq-login-form-wrap">
          <Link to="/" className="mbq-login-mobile-logo">
            <img src="/mbq-logo.png" alt="MBQ Healthcare" />
          </Link>

          <span className="mbq-form-eyebrow">STAFF PORTAL</span>

          <h2>Welcome back.</h2>

          <p className="mbq-form-description">
            Sign in with your hospital-issued account to access
            your MBQ workspace.
          </p>

          <form onSubmit={submit} className="mbq-auth-form">
            <div className="mbq-field">
              <label htmlFor="username">Username</label>
              <input
                id="username"
                type="text"
                autoComplete="username"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                placeholder="Enter your username"
                required
              />
            </div>

            <div className="mbq-field">
              <label htmlFor="password">Password</label>

              <div className="mbq-password-input">
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="Enter your password"
                  required
                />

                <button
                  type="button"
                  onClick={() => setShowPassword((value) => !value)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <EyeOff size={19} /> : <Eye size={19} />}
                </button>
              </div>
            </div>

            {error && (
              <div className="mbq-login-error" role="alert">
                {error}
              </div>
            )}

            <button
              className="mbq-login-submit"
              type="submit"
              disabled={busy}
            >
              {busy ? (
                "Signing in..."
              ) : (
                <>
                  <LockKeyhole size={18} />
                  Sign in securely
                </>
              )}
            </button>
          </form>

          <div className="mbq-login-security">
            <ShieldCheck size={19} />

            <div>
              <strong>Secure staff access</strong>
              <span>
                Your account is protected by MBQ's authentication system.
              </span>
            </div>
          </div>

          <div className="mbq-login-bottom">
            <span>MANAGE</span>
            <i />
            <span>CARE</span>
            <i />
            <span>SERVE</span>
          </div>
        </div>
      </section>
    </main>
  );
}