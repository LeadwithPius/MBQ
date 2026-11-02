import { useCallback, useEffect, useState } from "react";
import { Activity, ClipboardList, Droplet, LayoutDashboard, LogOut, Menu, Plus, ShieldAlert, Stethoscope, Users, X } from "lucide-react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { api } from "../services/api";
import { useAuth } from "../context/useAuth";
import { ROLE_ACCESS } from "../roleAccess";

const links = [
  { to: "/app", label: "Overview", icon: LayoutDashboard, end: true, roles: ROLE_ACCESS.WORKSPACE },
  { to: "/app/patients", label: "Patients", icon: Users, roles: ROLE_ACCESS.PATIENTS },
  { to: "/app/queue", label: "Queue management", icon: ClipboardList, roles: ROLE_ACCESS.QUEUE },
  { to: "/app/triage", label: "Clinical triage", icon: Stethoscope, roles: ROLE_ACCESS.TRIAGE },
  { to: "/app/blood-bank", label: "Blood bank", icon: Droplet, roles: ROLE_ACCESS.BLOOD_BANK },
  { to: "/app/reports", label: "Reports", icon: Activity, roles: ROLE_ACCESS.REPORTS },
];

export function WorkspaceLayout() {
  const { staff, logout } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const navigate = useNavigate();
  const visibleLinks = links.filter(item => item.roles.includes(staff?.role));
  return <div className="workspace">
    {menuOpen && <button className="sidebar-scrim" aria-label="Close navigation" onClick={() => setMenuOpen(false)} />}
    <aside className={"workspace-sidebar " + (menuOpen ? "sidebar-visible" : "")}>
      <div className="workspace-brand">
  <img
    className="workspace-logo-image"
    src="/mbq-logo.png"
    alt="MBQ Healthcare"
  />

  <span>
    <b>MBQ</b>
    <small>Hospital Operations</small>
  </span>

  <button
    className="sidebar-close"
    onClick={() => setMenuOpen(false)}
    aria-label="Close menu"
  >
    <X size={19} />
  </button>
</div>
      <span className="sidebar-caption">WORKSPACE</span>
      <nav className="workspace-nav">{visibleLinks.map(({ to, label, icon: Icon, end }) => <NavLink key={to} to={to} end={end} onClick={() => setMenuOpen(false)} className={({ isActive }) => "workspace-nav-link" + (isActive ? " active" : "")}><Icon size={18} /><span>{label}</span></NavLink>)}</nav>
      <div className="sidebar-bottom"><div className="sidebar-help"><ShieldAlert size={19} /><b>Clinical responsibility</b><p>Use the system alongside your facility's approved clinical procedures.</p></div><div className="staff-profile">
        <span className="staff-avatar">{(staff?.name || "S").split(" ").map(part => part[0]).slice(0, 2).join("").toUpperCase()}</span>
        <span className="staff-profile-details">
          <b>{staff?.name || "Staff member"}</b>
          <small><span className="profile-status-dot" />{staff?.role || "STAFF"}</small>
        </span>
        <button className="staff-signout" onClick={() => { logout(); navigate("/login", { replace: true }); }} aria-label="Sign out" title="Sign out"><LogOut size={18} /></button>
      </div></div>
    </aside>
    <main className="workspace-main"><header className="workspace-topbar"><button className="mobile-menu-button" onClick={() => setMenuOpen(true)} aria-label="Open navigation"><Menu size={21} /></button><div><span className="topbar-overline">MBQ WORKSPACE</span><b>{staff?.departmentId ? "Department " + staff.departmentId : "Hospital operations"}</b></div><div className="topbar-user"><span className="status-pip" /> Secure staff session <span className="topbar-role">{staff?.role}</span></div></header><div className="workspace-content"><Outlet /></div></main>
  </div>;
}

function PageHeading({ eyebrow = "HOSPITAL OPERATIONS", title, description, action }) {
  return <div className="page-title-row"><div><span className="eyebrow">{eyebrow}</span><h1>{title}</h1><p>{description}</p></div>{action}</div>;
}

function useDepartmentQueue() {
  const { staff } = useAuth();
  const departmentId = staff?.departmentId;
  const [queue, setQueue] = useState([]);
  const [loading, setLoading] = useState(Boolean(departmentId));
  const [error, setError] = useState("");

  const loadQueue = useCallback(async () => {
    if (!departmentId) return;
    try {
      const data = await api("/queue/departments/" + departmentId + "/waiting");
      setQueue(Array.isArray(data) ? data : []);
      setError("");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [departmentId]);

  const refresh = useCallback(async () => {
    if (!departmentId) return;
    setLoading(true);
    await loadQueue();
  }, [departmentId, loadQueue]);

  useEffect(() => {
    if (!departmentId) return undefined;
    void loadQueue();
    const timer = window.setInterval(() => { void loadQueue(); }, 30000);
    return () => window.clearInterval(timer);
  }, [departmentId, loadQueue]);

  return {
    queue,
    loading,
    error: departmentId ? error : "Your staff account is not assigned to a department.",
    refresh,
    departmentId,
  };
}

export function DashboardPage() {
  const { staff } = useAuth();
  const { queue, loading, error, refresh } = useDepartmentQueue();
  const emergency = queue.filter(item => item.priorityLevel === "EMERGENCY").length;
  const urgent = queue.filter(item => item.priorityLevel === "URGENT").length;
  return <><PageHeading title="Hospital operations" description={"Welcome, " + (staff?.name || "staff member") + ". Here is the current department queue."} action={<button className="button button-outline" onClick={refresh}>Refresh queue</button>} />
    {error && <div className="notice notice-warning">{error}</div>}
    <div className="stat-grid"><Stat label="Patients waiting" value={loading ? "—" : queue.length} icon={Users} /><Stat label="Emergency priority" value={loading ? "—" : emergency} icon={ShieldAlert} tone="red" /><Stat label="Urgent priority" value={loading ? "—" : urgent} icon={Activity} tone="amber" /><Stat label="Department" value={staff?.departmentId || "Not assigned"} icon={ClipboardList} /></div>
    <section className="panel"><div className="panel-heading"><div><h2>Waiting queue</h2><p>Live data from your assigned department</p></div><span className="live-label"><span className="status-pip" /> Auto-refresh 30s</span></div><QueueTable rows={queue.slice(0,6)} loading={loading} /></section>
  </>;
}

function Stat({ label, value, icon: Icon, tone = "blue" }) {
  return <article className="stat-card"><span className={"stat-icon " + tone}><Icon size={20} /></span><div><small>{label}</small><strong>{value}</strong></div></article>;
}

function QueueTable({ rows, loading, onCall, onComplete }) {
  if (loading) return <div className="empty-panel">Loading queue records…</div>;
  if (!rows.length) return <div className="empty-panel">No patients are currently waiting in this department.</div>;
  return <div className="table-wrap"><table className="data-table"><thead><tr><th>Ticket</th><th>Patient</th><th>Priority</th><th>Received</th><th>Status</th>{(onCall || onComplete) && <th>Action</th>}</tr></thead><tbody>{rows.map(row => <tr key={row.id}><td><b>{row.ticketNumber}</b></td><td>{row.patientName}</td><td><span className={"pill priority-" + String(row.priorityLevel).toLowerCase()}>{row.priorityLevel}</span></td><td>{row.timeIssued ? new Date(row.timeIssued).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "—"}</td><td><span className="pill status-waiting">{row.status}</span></td><td>{onCall && <button className="table-action" onClick={() => onCall(row)}>Call next</button>}{onComplete && <button className="table-action" onClick={() => onComplete(row)}>Complete</button>}</td></tr>)}</tbody></table></div>;
}

export function QueuePage() {
  const { staff } = useAuth();
  const { queue, loading, error, refresh } = useDepartmentQueue();
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  async function callNext() {
    setBusy(true); setMessage("");
    try { await api("/queue/departments/" + staff.departmentId + "/call-next", { method: "POST" }); setMessage("The next patient has been called."); await refresh(); }
    catch (err) { setMessage(err.message); }
    finally { setBusy(false); }
  }
  async function complete(row) {
    setBusy(true); setMessage("");
    try { await api("/queue/tickets/" + row.id + "/status", { method: "PATCH", body: { status: "COMPLETED", notes: "Completed by staff" } }); await refresh(); }
    catch (err) { setMessage(err.message); }
    finally { setBusy(false); }
  }
  return <><PageHeading title="Queue management" description="Monitor waiting tickets and coordinate patient flow." action={(staff?.role === "ADMIN" || staff?.role === "DOCTOR") ? <button className="button button-primary" disabled={busy || !staff?.departmentId} onClick={callNext}>Call next patient</button> : null} />{error && <div className="notice notice-warning">{error}</div>}{message && <div className="notice">{message}</div>}<section className="panel"><div className="panel-heading"><div><h2>Waiting patients</h2><p>{queue.length} ticket(s) waiting in your department</p></div><button className="button button-outline" onClick={refresh}>Refresh</button></div><QueueTable rows={queue} loading={loading || busy} onComplete={staff?.role === "ADMIN" || staff?.role === "DOCTOR" ? complete : null} /></section></>;
}

export function PatientsPage() {
  const { staff } = useAuth();
  const [patients, setPatients] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({ name: "", age: "", sex: "", contact: "", bloodGroup: "", facilityId: staff?.facilityId || "" });
  const load = useCallback(async () => {
    try { const data = await api("/patients"); setPatients(Array.isArray(data) ? data : []); setError(""); }
    catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { load(); }, [load]);
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError(""); setNotice("");
    try {
      const patient = await api("/patients", { method: "POST", body: { ...form, age: form.age === "" ? null : Number(form.age), bloodGroup: form.bloodGroup || null, facilityId: Number(form.facilityId || staff?.facilityId) } });
      try {
        if (!staff?.departmentId) throw new Error("Patient registered, but no department is assigned to your account, so a queue ticket was not created.");
        await api("/queue/tickets", { method: "POST", body: { patientId: patient.id, departmentId: staff.departmentId, priorityLevel: "NORMAL", requiresBlood: false } });
        setNotice("Patient registered and added to your department queue.");
      } catch (ticketError) { setNotice("Patient registered successfully. Queue ticket not created: " + ticketError.message); }
      setForm({ name: "", age: "", sex: "", contact: "", bloodGroup: "", facilityId: staff?.facilityId || "" });
      await load();
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }
  return <><PageHeading title="Patient registration" description="Register a patient and, when assigned to a department, create their queue ticket." /><div className="two-column">{(["ADMIN", "RECEPTIONIST"].includes(staff?.role)) ? <section className="panel form-panel"><div className="panel-heading"><div><h2>New patient</h2><p>Enter the patient's details</p></div></div><form className="form-grid" onSubmit={submit}>{staff?.role === "ADMIN" && !staff?.facilityId && <label className="span-two">Facility ID<input type="number" min="1" required value={form.facilityId} onChange={e => setForm({ ...form, facilityId: e.target.value })} /></label>}<label className="span-two">Full name<input required maxLength={255} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label><label>Age<input type="number" min="0" max="130" value={form.age} onChange={e => setForm({ ...form, age: e.target.value })} /></label><label>Sex<input maxLength={255} value={form.sex} onChange={e => setForm({ ...form, sex: e.target.value })} placeholder="Optional" /></label><label>Contact<input maxLength={255} value={form.contact} onChange={e => setForm({ ...form, contact: e.target.value })} placeholder="Phone or other contact" /></label><label>Blood group<select value={form.bloodGroup} onChange={e => setForm({ ...form, bloodGroup: e.target.value })}><option value="">Not recorded</option>{["A_POS","A_NEG","B_POS","B_NEG","AB_POS","AB_NEG","O_POS","O_NEG"].map(v => <option key={v}>{v}</option>)}</select></label>{error && <div className="form-error span-two">{error}</div>}{notice && <div className="notice span-two">{notice}</div>}<button className="button button-primary span-two" disabled={busy || !(form.facilityId || staff?.facilityId)}>{busy ? "Saving patient…" : <><Plus size={16} /> Register patient</>}</button></form></section> : <section className="panel"><div className="empty-panel">Your staff role does not have permission to register patients. You can still view your facility's patient directory.</div></section>}<section className="panel"><div className="panel-heading"><div><h2>Patient directory</h2><p>Patients registered at your facility</p></div><button className="button button-outline" onClick={load}>Refresh</button></div>{loading ? <div className="empty-panel">Loading patients…</div> : patients.length ? <div className="patient-list">{patients.map(p => <div className="patient-list-row" key={p.id}><span className="patient-initials">{p.name.split(" ").map(s => s[0]).slice(0,2).join("").toUpperCase()}</span><span><b>{p.name}</b><small>Patient #{p.id} · {p.age ?? "Age not recorded"}</small></span></div>)}</div> : <div className="empty-panel">No patient records returned.</div>}</section></div></>;
}

export function TriagePage() {
  const { staff } = useAuth();
  const canRecord = staff?.role === "ADMIN" || staff?.role === "NURSE";
  const canReview = staff?.role === "ADMIN" || staff?.role === "DOCTOR";
  const [form, setForm] = useState({ patientId: "", queueTicketId: "", systolicBp: "", diastolicBp: "", heartRate: "", respiratoryRate: "", temperatureC: "", spo2Percent: "", symptoms: "", notes: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [assessment, setAssessment] = useState(null);
  const [lookupId, setLookupId] = useState("");
  const [records, setRecords] = useState([]);
  const [lookupBusy, setLookupBusy] = useState(false);
  const [reviewNotes, setReviewNotes] = useState({});
  const [reviewPriority, setReviewPriority] = useState({});
  const [reviewBusy, setReviewBusy] = useState(null);
  const [notice, setNotice] = useState("");

  function update(key, value) { setForm(current => ({ ...current, [key]: value })); }

  async function submit(event) {
    event.preventDefault(); setBusy(true); setError(""); setAssessment(null); setNotice("");
    const body = { patientId: Number(form.patientId), queueTicketId: form.queueTicketId ? Number(form.queueTicketId) : null, assessedById: staff.id, measuredAt: new Date().toISOString(), systolicBp: numberOrNull(form.systolicBp), diastolicBp: numberOrNull(form.diastolicBp), heartRate: numberOrNull(form.heartRate), respiratoryRate: numberOrNull(form.respiratoryRate), temperatureC: form.temperatureC === "" ? null : Number(form.temperatureC), spo2Percent: numberOrNull(form.spo2Percent), symptoms: form.symptoms, notes: form.notes };
    try { const result = await api("/triage/observations", { method: "POST", body }); setAssessment(result); setNotice("Observation recorded. The recommendation requires clinician review."); }
    catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }

  async function loadPatientRecords(event) {
    if (event) event.preventDefault();
    setLookupBusy(true); setError(""); setNotice(""); setRecords([]);
    try {
      const observations = await api("/triage/patients/" + Number(lookupId) + "/observations");
      const enriched = await Promise.all(observations.map(async observation => ({
        observation,
        assessments: await api("/triage/observations/" + observation.id + "/assessments"),
      })));
      setRecords(enriched);
      if (!enriched.length) setNotice("No triage observations were found for this patient.");
    } catch (err) { setError(err.message); }
    finally { setLookupBusy(false); }
  }

  async function review(assessmentId, status) {
    const notes = (reviewNotes[assessmentId] || "").trim();
    if (!notes) { setError("Clinician notes are required before submitting a review."); return; }
    if (status === "OVERRIDDEN" && !reviewPriority[assessmentId]) { setError("Choose a confirmed priority when overriding a recommendation."); return; }
    setReviewBusy(assessmentId); setError(""); setNotice("");
    try {
      await api("/triage/assessments/" + assessmentId + "/review", {
        method: "PATCH",
        body: { reviewedById: staff.id, reviewStatus: status, confirmedPriority: status === "OVERRIDDEN" ? reviewPriority[assessmentId] : null, clinicianNotes: notes },
      });
      setNotice("Clinical assessment review saved.");
      await loadPatientRecords();
    } catch (err) { setError(err.message); }
    finally { setReviewBusy(null); }
  }

  return <>
    <PageHeading title="Clinical triage" description="Record observations and review preliminary risk assessments. Clinical decisions remain with qualified staff." />
    {canRecord && <section className="panel form-panel">
      <div className="panel-heading"><div><h2>Record observations</h2><p>Enter the patient and available vital signs</p></div></div>
      <form className="form-grid" onSubmit={submit}>
        <label>Patient ID<input type="number" min="1" required value={form.patientId} onChange={e => update("patientId", e.target.value)} /></label>
        <label>Queue ticket ID<input type="number" min="1" value={form.queueTicketId} onChange={e => update("queueTicketId", e.target.value)} /></label>
        <label>Systolic BP (mmHg)<input type="number" min="1" value={form.systolicBp} onChange={e => update("systolicBp", e.target.value)} /></label>
        <label>Diastolic BP (mmHg)<input type="number" min="1" value={form.diastolicBp} onChange={e => update("diastolicBp", e.target.value)} /></label>
        <label>Heart rate (bpm)<input type="number" min="1" max="300" value={form.heartRate} onChange={e => update("heartRate", e.target.value)} /></label>
        <label>Respiratory rate (/min)<input type="number" min="1" max="100" value={form.respiratoryRate} onChange={e => update("respiratoryRate", e.target.value)} /></label>
        <label>Temperature (°C)<input type="number" min="25" max="45" step="0.1" value={form.temperatureC} onChange={e => update("temperatureC", e.target.value)} /></label>
        <label>SpO₂ (%)<input type="number" min="0" max="100" value={form.spo2Percent} onChange={e => update("spo2Percent", e.target.value)} /></label>
        <label className="span-two">Symptoms<textarea rows="3" value={form.symptoms} onChange={e => update("symptoms", e.target.value)} /></label>
        <label className="span-two">Clinical notes<textarea rows="3" value={form.notes} onChange={e => update("notes", e.target.value)} /></label>
        <button className="button button-primary span-two" disabled={busy}>{busy ? "Submitting observations…" : "Submit for risk assessment"}</button>
      </form>
    </section>}
    {canReview && <section className="panel form-panel">
      <div className="panel-heading"><div><h2>Clinician review</h2><p>Load a patient's previous triage observations and review pending recommendations.</p></div></div>
      <form className="lookup-form" onSubmit={loadPatientRecords}>
        <label>Patient ID<input type="number" min="1" required value={lookupId} onChange={e => setLookupId(e.target.value)} /></label>
        <button className="button button-outline" disabled={lookupBusy || !lookupId}>{lookupBusy ? "Loading…" : "Load assessments"}</button>
      </form>
      {records.map(({ observation, assessments }) => <article className="review-record" key={observation.id}>
        <div className="review-record-heading"><b>Observation #{observation.id}</b><small>{observation.measuredAt ? new Date(observation.measuredAt).toLocaleString() : "Date not recorded"}</small></div>
        <p className="review-vitals">BP {observation.systolicBp ?? "—"}/{observation.diastolicBp ?? "—"} · HR {observation.heartRate ?? "—"} · SpO₂ {observation.spo2Percent ?? "—"}%</p>
        {assessments.map(item => <div className="review-assessment" key={item.id}>
          <div><span className={"pill priority-" + String(item.recommendedPriority).toLowerCase()}>{item.recommendedPriority}</span><span className="review-status">{item.reviewStatus}</span></div>
          <p>{(item.findings || []).join(" · ") || "No additional findings returned."}</p>
          {item.reviewStatus === "PENDING" ? <div className="review-controls">
            <label>Clinician notes<textarea rows="2" value={reviewNotes[item.id] || ""} onChange={e => setReviewNotes(v => ({ ...v, [item.id]: e.target.value }))} /></label>
            <label>Confirmed priority (required only when overriding)<select value={reviewPriority[item.id] || ""} onChange={e => setReviewPriority(v => ({ ...v, [item.id]: e.target.value }))}><option value="">Select if overriding</option><option value="NORMAL">Normal</option><option value="URGENT">Urgent</option><option value="EMERGENCY">Emergency</option></select></label>
            <div className="review-buttons"><button className="button button-outline" disabled={reviewBusy === item.id} onClick={() => review(item.id, "CONFIRMED")}>Confirm recommendation</button><button className="button button-primary" disabled={reviewBusy === item.id} onClick={() => review(item.id, "OVERRIDDEN")}>Override priority</button></div>
          </div> : <p className="review-notes">Review notes: {item.clinicianNotes || "No notes supplied."}</p>}
        </div>)}
      </article>)}
    </section>}
    {error && <div className="form-error">{error}</div>}
    {notice && <div className="notice">{notice}</div>}
    {assessment && <section className="panel assessment-panel"><span className="eyebrow">PRELIMINARY CLINICAL RISK ASSESSMENT</span><h2>Recommended priority: <span className={"pill priority-" + String(assessment.recommendedPriority).toLowerCase()}>{assessment.recommendedPriority}</span></h2><p>{(assessment.findings || []).join(" · ") || "No additional findings returned."}</p><div className="notice notice-warning">This is a decision-support recommendation, not a diagnosis or a substitute for clinical judgement. Review according to facility protocol.</div></section>}
  </>;
}

function numberOrNull(value) { return value === "" ? null : Number(value); }

export function ComingSoonPage({ title, description }) {
  return <><PageHeading title={title} description={description} /><section className="panel placeholder-panel"><span className="placeholder-mark"><Droplet size={28} /></span><h2>Backend integration pending</h2><p>This module is intentionally not displaying fabricated records or metrics.</p></section></>;
}
