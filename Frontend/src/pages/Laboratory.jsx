import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, CheckCircle2, FlaskConical, Play, RefreshCw, XCircle } from "lucide-react";
import { api } from "../services/api";

const VIEWS = [
  { key: "OPEN", label: "Open tests", query: "" },
  { key: "COMPLETED", label: "Completed", query: "?status=COMPLETED" },
  { key: "CANCELLED", label: "Cancelled", query: "?status=CANCELLED" },
];

function formatDateTime(value) {
  if (!value) return "—";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString();
}

/** Lab worklist (Workflow B): tests ordered by doctors, processed and resulted by lab staff. */
export function LaboratoryPage() {
  const [view, setView] = useState("OPEN");
  const [rows, setRows] = useState([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [resulting, setResulting] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await api("/lab/requests" + VIEWS.find(v => v.key === view).query);
      setRows(Array.isArray(data) ? data : []);
      setError("");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [view]);

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

  const term = search.trim().toLowerCase();
  const visible = rows.filter(r => !term || [r.patientName, r.testName, r.ticketNumber, String(r.patientId)].some(v => String(v || "").toLowerCase().includes(term)));
  const urgentOpen = rows.filter(r => r.priority === "URGENT" && (r.status === "REQUESTED" || r.status === "IN_PROGRESS")).length;

  const [cancelling, setCancelling] = useState({ id: null, reason: "" });
  const confirmCancel = async r => {
    const reason = cancelling.reason.trim();
    if (!reason) return setError("Give a reason for cancelling the test.");
    const ok = await run(() => api("/lab/requests/" + r.id + "/cancel", { method: "PATCH", body: { reason } }), r.testName + " for " + r.patientName + " cancelled.");
    if (ok) setCancelling({ id: null, reason: "" });
  };

  return <>
    <div className="page-title-row">
      <div><span className="eyebrow">LABORATORY</span><h1>Laboratory</h1><p>Tests requested from consultations. Urgent requests are listed first.</p></div>
      <button className="button button-outline" onClick={load} disabled={loading || busy}><RefreshCw size={16} /> Refresh</button>
    </div>

    {error && <div className="notice notice-warning">{error}</div>}
    {notice && <div className="notice">{notice}</div>}
    {view === "OPEN" && urgentOpen > 0 && <div className="blood-low-alert" role="alert"><AlertTriangle size={18} /><span><b>{urgentOpen} urgent test{urgentOpen === 1 ? "" : "s"}</b> waiting.</span></div>}

    <section className="panel">
      <div className="panel-heading">
        <div className="lab-tabs" role="tablist">{VIEWS.map(v => <button key={v.key} role="tab" aria-selected={view === v.key} className={"button " + (view === v.key ? "button-primary" : "button-outline")} onClick={() => { setResulting(null); setView(v.key); }}>{v.label}</button>)}</div>
        <label className="donor-filter">Search <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Patient, test or ticket" /></label>
      </div>

      {loading ? <div className="empty-panel">Loading tests…</div> : visible.length === 0 ? <div className="empty-panel">{view === "OPEN" ? "No open tests. New requests from doctors appear here." : "Nothing to show."}</div> :
        <div className="table-wrap"><table className="data-table"><thead><tr><th>Requested</th><th>Patient</th><th>Test</th><th>Priority</th><th>Status</th><th>{view === "OPEN" ? "Action" : view === "COMPLETED" ? "Result" : "Reason"}</th></tr></thead><tbody>
          {visible.map(r => <tr key={r.id}>
            <td>{formatDateTime(r.requestedAt)}<small className="queue-patient-id">by {r.requestedByName}</small></td>
            <td><b>{r.patientName}</b><small className="queue-patient-id">Patient #{r.patientId}{r.ticketNumber ? " · " + r.ticketNumber : ""}{r.departmentName ? " · " + r.departmentName : ""}</small></td>
            <td><b>{r.testName}</b>{r.clinicalNotes && <small className="queue-patient-id">{r.clinicalNotes}</small>}</td>
            <td><span className={"pill " + (r.priority === "URGENT" ? "priority-emergency" : "priority-normal")}>{r.priority}</span></td>
            <td>{r.status.replace("_", " ")}{r.startedByName && r.status === "IN_PROGRESS" && <small className="queue-patient-id">{r.startedByName}</small>}</td>
            <td>{view === "OPEN" ? <span className="alert-row-actions">
                {r.status === "REQUESTED" && <button className="table-action" disabled={busy} onClick={() => run(() => api("/lab/requests/" + r.id + "/start", { method: "PATCH" }), r.testName + " for " + r.patientName + " started.")}><Play size={12} /> Start</button>}
                <button className="table-action" disabled={busy} onClick={() => setResulting(r)}><CheckCircle2 size={12} /> Enter result</button>
                {cancelling.id === r.id
                  ? <><input className="table-input" autoFocus maxLength={500} value={cancelling.reason} onChange={e => setCancelling({ id: r.id, reason: e.target.value })} placeholder="Reason" /><button className="table-action table-action-blood" disabled={busy} onClick={() => confirmCancel(r)}>Confirm cancel</button><button className="table-action" onClick={() => setCancelling({ id: null, reason: "" })}>Keep</button></>
                  : <button className="table-action table-action-blood" disabled={busy} onClick={() => setCancelling({ id: r.id, reason: "" })}><XCircle size={12} /> Cancel</button>}
              </span>
              : view === "COMPLETED" ? <>{r.abnormal && <span className="pill priority-emergency">ABNORMAL</span>} {r.resultText}<small className="queue-patient-id">{r.completedByName} · {formatDateTime(r.completedAt)}</small></>
              : r.cancelledReason}</td>
          </tr>)}
        </tbody></table></div>}
    </section>

    {resulting && <ResultForm key={resulting.id} request={resulting} busy={busy} onCancel={() => setResulting(null)}
      onSubmit={async body => { const ok = await run(() => api("/lab/requests/" + resulting.id + "/complete", { method: "PATCH", body }), resulting.testName + " result saved for " + resulting.patientName + ". The doctor can now see it."); if (ok) setResulting(null); }} />}
  </>;
}

function ResultForm({ request, busy, onCancel, onSubmit }) {
  const [form, setForm] = useState({ resultText: "", resultNotes: "", abnormal: false });
  return <section className="panel">
    <div className="panel-heading"><div><h2>Result: {request.testName}</h2><p>{request.patientName} · requested by {request.requestedByName}. Results cannot be edited after saving.</p></div><FlaskConical size={19} /></div>
    <form className="form-grid" onSubmit={e => { e.preventDefault(); onSubmit({ resultText: form.resultText.trim(), resultNotes: form.resultNotes.trim() || null, abnormal: form.abnormal }); }}>
      <label className="span-two">Result<textarea required maxLength={4000} rows={3} value={form.resultText} onChange={e => setForm({ ...form, resultText: e.target.value })} placeholder="e.g. Hb 11.8 g/dL; WBC 6.2 ×10⁹/L" /></label>
      <label className="span-two">Notes (optional)<input maxLength={2000} value={form.resultNotes} onChange={e => setForm({ ...form, resultNotes: e.target.value })} /></label>
      <label className="checkbox-label"><input type="checkbox" checked={form.abnormal} onChange={e => setForm({ ...form, abnormal: e.target.checked })} /> Flag as abnormal</label>
      <span />
      <button type="button" className="button button-outline" onClick={onCancel} disabled={busy}>Back</button>
      <button className="button button-primary" disabled={busy || !form.resultText.trim()}>Save result</button>
    </form>
  </section>;
}
