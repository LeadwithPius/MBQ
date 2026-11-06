import { useCallback, useEffect, useState } from "react";
import { KeyRound, RefreshCw, UserPlus } from "lucide-react";
import { api } from "../services/api";
import { useAuth } from "../context/useAuth";

const ROLES = ["DOCTOR", "NURSE", "RECEPTIONIST", "LAB_STAFF", "ADMIN"];
const roleLabel = role => String(role).replace("_", " ").toLowerCase().replace(/^\w/, c => c.toUpperCase());

/** Admin: create staff accounts, deactivate/reactivate them, reset passwords. */
export function StaffPage() {
  const { staff: me } = useAuth();
  const [staff, setStaff] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [form, setForm] = useState({ name: "", username: "", role: "NURSE", departmentId: "", temporaryPassword: "" });
  const [reset, setReset] = useState({ id: null, password: "" });
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    try {
      const [staffData, deptData] = await Promise.all([api("/admin/staff"), api("/departments")]);
      setStaff(Array.isArray(staffData) ? staffData : []);
      setDepartments(Array.isArray(deptData) ? deptData : []);
      setError("");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => { if (active) load(); });
    return () => { active = false; };
  }, [load]);

  async function run(action, message) {
    setBusy(true); setError(""); setNotice("");
    try {
      const result = await action();
      setNotice(typeof message === "function" ? message(result) : message);
      await load();
      return true;
    } catch (err) {
      setError(err.message);
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function create(event) {
    event.preventDefault();
    const ok = await run(() => api("/admin/staff", { method: "POST", body: {
      name: form.name.trim(), username: form.username.trim(), role: form.role,
      departmentId: form.role === "ADMIN" || !form.departmentId ? null : Number(form.departmentId),
      temporaryPassword: form.temporaryPassword,
    } }), created => "Account " + created.username + " created. Give them the temporary password and ask them to change it under My account.");
    if (ok) setForm({ name: "", username: "", role: "NURSE", departmentId: "", temporaryPassword: "" });
  }

  async function submitReset(s) {
    const ok = await run(() => api("/admin/staff/" + s.id + "/password", { method: "PATCH", body: { newPassword: reset.password } }),
      s.username + "'s password was reset.");
    if (ok) setReset({ id: null, password: "" });
  }

  const toggle = s => run(() => api("/admin/staff/" + s.id + "/active", { method: "PATCH", body: { active: !s.active } }),
    s.username + (s.active ? " deactivated. They can no longer sign in." : " reactivated."));

  return <>
    <div className="page-title-row">
      <div><span className="eyebrow">ADMINISTRATION</span><h1>Staff accounts</h1><p>Create accounts, deactivate staff who leave, and reset forgotten passwords. Accounts are never deleted.</p></div>
      <button className="button button-outline" onClick={load} disabled={loading || busy}><RefreshCw size={16} /> Refresh</button>
    </div>
    {error && <div className="notice notice-warning">{error}</div>}
    {notice && <div className="notice">{notice}</div>}

    <section className="panel">
      <div className="panel-heading"><div><h2>New account</h2><p>Non-admin staff must belong to a department</p></div><UserPlus size={19} /></div>
      <form className="form-grid" onSubmit={create}>
        <label>Full name<input required maxLength={255} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
        <label>Username<input required pattern="[A-Za-z0-9._\-]{3,50}" title="3-50 letters, numbers, dots, dashes or underscores" value={form.username} onChange={e => setForm({ ...form, username: e.target.value })} placeholder="e.g. grace.mwale" /></label>
        <label>Role<select value={form.role} onChange={e => setForm({ ...form, role: e.target.value })}>{ROLES.map(r => <option key={r} value={r}>{roleLabel(r)}</option>)}</select></label>
        <label>Department<select required={form.role !== "ADMIN"} disabled={form.role === "ADMIN"} value={form.role === "ADMIN" ? "" : form.departmentId} onChange={e => setForm({ ...form, departmentId: e.target.value })}>
          <option value="">{form.role === "ADMIN" ? "None (system-wide)" : "Select a department"}</option>
          {departments.map(d => <option key={d.id} value={d.id}>{d.name}{d.facilityName ? " · " + d.facilityName : ""}</option>)}
        </select></label>
        <label className="span-two">Temporary password<input type="text" required minLength={8} maxLength={100} value={form.temporaryPassword} onChange={e => setForm({ ...form, temporaryPassword: e.target.value })} placeholder="At least 8 characters" /></label>
        <button className="button button-primary span-two" disabled={busy}><UserPlus size={15} /> Create account</button>
      </form>
    </section>

    <section className="panel">
      <div className="panel-heading"><div><h2>All staff</h2><p>{staff.filter(s => s.active).length} active · {staff.filter(s => !s.active).length} deactivated</p></div></div>
      {loading ? <div className="empty-panel">Loading staff…</div> :
        <div className="table-wrap"><table className="data-table"><thead><tr><th>Name</th><th>Username</th><th>Role</th><th>Department</th><th>Status</th><th>Actions</th></tr></thead><tbody>
          {staff.map(s => <tr key={s.id}>
            <td><b>{s.name}</b></td><td>{s.username}</td><td>{roleLabel(s.role)}</td><td>{s.departmentName || "—"}</td>
            <td>{s.active ? <span className="pill priority-normal">ACTIVE</span> : <span className="pill priority-emergency">DEACTIVATED</span>}</td>
            <td><span className="alert-row-actions">
              {reset.id === s.id
                ? <><input className="table-input" type="text" minLength={8} value={reset.password} onChange={e => setReset({ id: s.id, password: e.target.value })} placeholder="New password (8+)" /><button className="table-action" disabled={busy || reset.password.length < 8} onClick={() => submitReset(s)}>Save</button><button className="table-action" onClick={() => setReset({ id: null, password: "" })}>Cancel</button></>
                : <button className="table-action" disabled={busy} onClick={() => setReset({ id: s.id, password: "" })}><KeyRound size={12} /> Reset password</button>}
              {s.id !== me?.id && <button className={"table-action" + (s.active ? " table-action-blood" : "")} disabled={busy} onClick={() => toggle(s)}>{s.active ? "Deactivate" : "Reactivate"}</button>}
            </span></td>
          </tr>)}
        </tbody></table></div>}
    </section>
  </>;
}
