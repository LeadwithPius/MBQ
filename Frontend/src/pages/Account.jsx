import { useState } from "react";
import { KeyRound } from "lucide-react";
import { api } from "../services/api";
import { useAuth } from "../context/useAuth";

/** Signed-in staff change their own password. */
export function AccountPage() {
  const { staff } = useAuth();
  const [form, setForm] = useState({ currentPassword: "", newPassword: "", confirm: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  async function submit(event) {
    event.preventDefault();
    setError(""); setNotice("");
    if (form.newPassword.length < 8) return setError("The new password must be at least 8 characters.");
    if (form.newPassword !== form.confirm) return setError("The new passwords do not match.");
    setBusy(true);
    try {
      await api("/auth/change-password", { method: "POST", body: { currentPassword: form.currentPassword, newPassword: form.newPassword } });
      setForm({ currentPassword: "", newPassword: "", confirm: "" });
      setNotice("Password changed. Use the new password next time you sign in.");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return <>
    <div className="page-title-row"><div><span className="eyebrow">ACCOUNT</span><h1>My account</h1><p>{staff?.name} · {staff?.username} · {staff?.role}{staff?.departmentName ? " · " + staff.departmentName : ""}</p></div></div>
    {error && <div className="notice notice-warning">{error}</div>}
    {notice && <div className="notice">{notice}</div>}
    <section className="panel account-panel">
      <div className="panel-heading"><div><h2>Change password</h2><p>At least 8 characters, different from your current password</p></div><KeyRound size={19} /></div>
      <form className="form-grid" onSubmit={submit}>
        <label className="span-two">Current password<input type="password" autoComplete="current-password" required value={form.currentPassword} onChange={e => setForm({ ...form, currentPassword: e.target.value })} /></label>
        <label>New password<input type="password" autoComplete="new-password" required minLength={8} maxLength={100} value={form.newPassword} onChange={e => setForm({ ...form, newPassword: e.target.value })} /></label>
        <label>Confirm new password<input type="password" autoComplete="new-password" required value={form.confirm} onChange={e => setForm({ ...form, confirm: e.target.value })} /></label>
        <button className="button button-primary span-two" disabled={busy}>Change password</button>
      </form>
    </section>
  </>;
}
