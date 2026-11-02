import { useCallback, useEffect, useState } from "react";
import { Activity, AlertTriangle, CheckCircle2, ClipboardList, Clock3, Droplet, LayoutDashboard, LogOut, Menu, Plus, RefreshCw, ShieldAlert, Stethoscope, Users, X } from "lucide-react";
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
  const [confirmLogout, setConfirmLogout] = useState(false);
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
          <small>{staff?.role || "STAFF"}</small>
        </span>
        <button className="staff-signout" onClick={() => setConfirmLogout(true)} aria-label="Sign out" title="Sign out"><LogOut size={18} /></button>
        {confirmLogout && <div className="logout-confirm" role="dialog" aria-label="Confirm sign out"><span>Sign out of this workstation?</span><button onClick={() => setConfirmLogout(false)}>Stay</button><button onClick={() => { logout(); navigate("/login", { replace: true }); }}>Sign out</button></div>}
      </div></div>
    </aside>
    <main className="workspace-main"><header className="workspace-topbar"><button className="mobile-menu-button" onClick={() => setMenuOpen(true)} aria-label="Open navigation"><Menu size={21} /></button><div><span className="topbar-overline">MBQ WORKSPACE</span><b>{staff?.departmentId ? "Department " + staff.departmentId : "Hospital operations"}</b></div><div className="topbar-user"><span className="status-pip" /> Clinical workspace</div></header><div className="workspace-content"><Outlet /></div></main>
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
  const [lastUpdated, setLastUpdated] = useState(null);

  const loadQueue = useCallback(async ({ showLoading = false } = {}) => {
    if (!departmentId) return;
    if (showLoading) setLoading(true);
    try {
      const data = await api("/queue/departments/" + departmentId + "/waiting");
      setQueue(Array.isArray(data) ? data : []);
      setError("");
      setLastUpdated(Date.now());
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [departmentId]);

  const refresh = useCallback(() => loadQueue(), [loadQueue]);

  useEffect(() => {
    if (!departmentId) return undefined;
    let active = true;
    const fetchQueue = () => {
      api("/queue/departments/" + departmentId + "/waiting")
        .then(data => {
          if (!active) return;
          setQueue(Array.isArray(data) ? data : []);
          setError("");
          setLastUpdated(Date.now());
        })
        .catch(err => { if (active) setError(err.message); })
        .finally(() => { if (active) setLoading(false); });
    };
    fetchQueue();
    const timer = window.setInterval(fetchQueue, 30000);
    return () => { active = false; window.clearInterval(timer); };
  }, [departmentId]);

  return {
    queue, loading,
    error: departmentId ? error : "Your staff account is not assigned to a department.",
    refresh, departmentId, lastUpdated,
  };
}

function useElapsedClock() {
  const [now, setNow] = useState(null);
  useEffect(() => {
    setNow(Date.now());
    const timer = window.setInterval(() => setNow(Date.now()), 15000);
    return () => window.clearInterval(timer);
  }, []);
  return now;
}

function relativeAge(timestamp, now) {
  if (!timestamp) return "Not updated yet";
  const seconds = Math.max(0, Math.floor((now - timestamp) / 1000));
  if (seconds < 5) return "Updated just now";
  if (seconds < 60) return "Updated " + seconds + "s ago";
  const minutes = Math.floor(seconds / 60);
  return "Updated " + minutes + "m ago";
}

function waitMinutes(timeIssued, now) {
  if (!timeIssued) return null;
  const parsed = new Date(timeIssued).getTime();
  if (!Number.isFinite(parsed)) return null;
  return Math.max(0, Math.floor((now - parsed) / 60000));
}

function orderedQueue(rows, sortBy = "priority") {
  return [...rows].sort((a, b) => {
    if (sortBy === "wait") return (new Date(a.timeIssued) - new Date(b.timeIssued));
    if (sortBy === "patient") return String(a.patientName || "").localeCompare(String(b.patientName || ""));
    const rank = { EMERGENCY: 0, URGENT: 1, NORMAL: 2 };
    return (rank[a.priorityLevel] ?? 3) - (rank[b.priorityLevel] ?? 3)
      || (new Date(a.timeIssued) - new Date(b.timeIssued));
  });
}

export function DashboardPage() {
  const { staff } = useAuth();
  const navigate = useNavigate();
  const { queue, loading, error, refresh, lastUpdated } = useDepartmentQueue();
  const now = useElapsedClock();
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [priorityFilter, setPriorityFilter] = useState("ALL");
  const [sortBy, setSortBy] = useState("priority");
  const canCallNext = ["ADMIN", "DOCTOR"].includes(staff?.role);
  const emergency = queue.filter(item => item.priorityLevel === "EMERGENCY").length;
  const urgent = queue.filter(item => item.priorityLevel === "URGENT").length;
  const longestWait = queue.reduce((max, item) => Math.max(max, waitMinutes(item.timeIssued, now) ?? 0), 0);
  const visibleRows = orderedQueue(queue.filter(item => priorityFilter === "ALL" || item.priorityLevel === priorityFilter), sortBy);

  async function callNext() {
    if (!staff?.departmentId || busy) return;
    setBusy(true);
    setNotice("");
    try {
      const called = await api("/queue/departments/" + staff.departmentId + "/call-next", { method: "POST" });
      setNotice(called?.patientName ? called.patientName + " has been called." : "The next patient has been called.");
      await refresh();
    } catch (err) {
      setNotice(err.message);
    } finally {
      setBusy(false);
    }
  }

  return <>
    <PageHeading eyebrow="WORKSPACE" title="Overview" description={"Good to see you, " + (staff?.name || "staff member") + ". Here is your department's live queue."} action={<div className="dashboard-heading-actions"><span className="updated-label"><span className="status-pip" />{relativeAge(lastUpdated, now)}</span><button className="button button-outline" onClick={refresh} disabled={loading} aria-label="Refresh queue"><RefreshCw size={16} /> Refresh</button></div>} />
    {error && <div className="notice notice-warning">{error}</div>}
    {notice && <div className="notice">{notice}</div>}
    <div className="dashboard-primary-action">
      <div><span className="eyebrow">DEPARTMENT QUEUE</span><strong>{queue.length} waiting patient{queue.length === 1 ? "" : "s"}</strong><small>{staff?.departmentName || (staff?.departmentId ? "Department " + staff.departmentId : "Department not assigned")}</small></div>
      <button className="button button-primary" onClick={callNext} disabled={!canCallNext || busy || loading || queue.length === 0}><Users size={17} />{busy ? "Calling…" : "Call next patient"}</button>
    </div>
    <div className="stat-grid dashboard-stat-grid">
      <Stat label="Patients waiting" value={loading ? "—" : queue.length} icon={Users} />
      <Stat label="Emergency waiting" value={loading ? "—" : emergency} icon={AlertTriangle} tone={emergency ? "red alert-active" : "red"} />
      <Stat label="Urgent waiting" value={loading ? "—" : urgent} icon={Activity} tone={urgent ? "amber alert-active" : "amber"} />
      <Stat label="Longest wait" value={loading ? "—" : longestWait + " min"} icon={Clock3} />
    </div>
    <section className="panel dashboard-queue-panel">
      <div className="panel-heading dashboard-queue-heading"><div><h2>Waiting queue</h2><p>Prioritised by clinical urgency, then waiting time</p></div><div className="queue-controls"><label>Priority <select value={priorityFilter} onChange={event => setPriorityFilter(event.target.value)}><option value="ALL">All priorities</option><option value="EMERGENCY">Emergency</option><option value="URGENT">Urgent</option><option value="NORMAL">Normal</option></select></label><label>Sort by <select value={sortBy} onChange={event => setSortBy(event.target.value)}><option value="priority">Priority & wait</option><option value="wait">Longest waiting</option><option value="patient">Patient name</option></select></label></div></div>
      <QueueTable rows={visibleRows} loading={loading} now={now} onPatient={row => navigate("/app/patients?patientId=" + row.patientId)} />
    </section>
    <section className="dashboard-lower-grid">
      <article className="panel dashboard-info-panel"><div className="panel-heading"><div><h2>Queue attention</h2><p>Operational items requiring awareness</p></div><ShieldAlert size={19} /></div>{emergency > 0 ? <p className="attention-message emergency-message"><AlertTriangle size={17} /> {emergency} emergency-priority patient{emergency === 1 ? "" : "s"} waiting. Review immediately under facility protocol.</p> : <p className="attention-message"><CheckCircle2 size={17} /> No emergency-priority patients currently waiting.</p>}{urgent > 0 && <p className="attention-message urgent-message"><AlertTriangle size={17} /> {urgent} urgent-priority patient{urgent === 1 ? "" : "s"} waiting.</p>}</article>
      <article className="panel dashboard-info-panel"><div className="panel-heading"><div><h2>Workspace details</h2><p>Current staff session</p></div><ClipboardList size={19} /></div><dl className="workspace-details"><div><dt>Staff member</dt><dd>{staff?.name || "Not available"}</dd></div><div><dt>Role</dt><dd>{staff?.role || "Not available"}</dd></div><div><dt>Department</dt><dd>{staff?.departmentName || (staff?.departmentId ? "Department " + staff.departmentId : "Not assigned")}</dd></div></dl></article>
    </section>
  </>;
}

function Stat({ label, value, icon: Icon, tone = "blue" }) {
  return <article className={"stat-card " + (tone.includes("alert-active") ? "stat-alert-active" : "")}><span className={"stat-icon " + tone}><Icon size={20} /></span><div><small>{label}</small><strong>{value}</strong></div></article>;
}

function PriorityBadge({ priority }) {
  const config = {
    EMERGENCY: { icon: AlertTriangle, label: "Emergency", className: "priority-emergency" },
    URGENT: { icon: AlertTriangle, label: "Urgent", className: "priority-urgent" },
    NORMAL: { icon: CheckCircle2, label: "Normal", className: "priority-normal" },
  }[priority] || { icon: ClipboardList, label: priority || "Unassigned", className: "priority-normal" };
  const Icon = config.icon;
  return <span className={"pill priority-badge " + config.className}><Icon size={13} aria-hidden="true" />{config.label}</span>;
}

function QueueTable({ rows, loading, now, onPatient }) {
  if (loading && !rows.length) return <div className="queue-skeleton" aria-label="Loading queue">{[1,2,3,4].map(item => <div className="skeleton-row" key={item}><i /><i /><i /><i /></div>)}</div>;
  if (!rows.length) return <div className="queue-empty-state"><span><ClipboardList size={24} /></span><h3>No patients waiting</h3><p>New queue tickets will appear here when patients are registered.</p></div>;
  return <div className="table-wrap"><table className="data-table dashboard-queue-table"><thead><tr><th>Ticket</th><th>Patient</th><th>Priority</th><th>Waiting</th><th>Status</th><th>Open</th></tr></thead><tbody>{rows.map(row => {
    const minutes = waitMinutes(row.timeIssued, now);
    const waitTone = minutes === null ? "" : minutes >= 30 ? "wait-critical" : minutes >= 15 ? "wait-warning" : "wait-normal";
    return <tr key={row.id} className="queue-data-row"><td><b>{row.ticketNumber}</b></td><td><button className="patient-row-link" onClick={() => onPatient?.(row)}>{row.patientName || "Patient record"}</button><small className="queue-patient-id">Patient #{row.patientId}</small></td><td><PriorityBadge priority={row.priorityLevel} /></td><td><span className={"wait-time " + waitTone}>{minutes === null ? "—" : "Waiting " + minutes + " min"}</span></td><td><span className={"pill queue-status status-" + String(row.status).toLowerCase()}>{row.status?.replace("_", " ")}</span></td><td><button className="table-action" onClick={() => onPatient?.(row)}>View patient</button></td></tr>;
  })}</tbody></table></div>;
}

export function QueuePage() {
  const { staff } = useAuth();
  const navigate = useNavigate();
  const { queue, loading, error, refresh } = useDepartmentQueue();
  const now = useElapsedClock();
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  async function callNext() {
    setBusy(true); setMessage("");
    try { await api("/queue/departments/" + staff.departmentId + "/call-next", { method: "POST" }); setMessage("The next patient has been called."); await refresh(); }
    catch (err) { setMessage(err.message); }
    finally { setBusy(false); }
  }
  return <><PageHeading title="Queue management" description="Monitor waiting tickets and coordinate patient flow." action={(staff?.role === "ADMIN" || staff?.role === "DOCTOR") ? <button className="button button-primary" disabled={busy || !staff?.departmentId} onClick={callNext}>Call next patient</button> : null} />{error && <div className="notice notice-warning">{error}</div>}{message && <div className="notice">{message}</div>}<section className="panel"><div className="panel-heading"><div><h2>Waiting patients</h2><p>{queue.length} ticket(s) waiting in your department</p></div><button className="button button-outline" onClick={refresh}>Refresh</button></div><QueueTable rows={queue} loading={loading || busy} now={now} onPatient={() => navigate("/app/patients")} /></section></>;
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
  useEffect(() => {
    let active = true;

    api("/patients")
      .then(data => {
        if (!active) return;
        setPatients(Array.isArray(data) ? data : []);
        setError("");
      })
      .catch(err => {
        if (active) setError(err.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);
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
