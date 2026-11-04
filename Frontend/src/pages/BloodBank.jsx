import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, BellRing, CheckCircle2, Droplet, PackagePlus, RefreshCw, Send, UserPlus } from "lucide-react";
import { api } from "../services/api";
import { useAuth } from "../context/useAuth";

// Who may do what (mirrors the backend @PreAuthorize rules).
const MANAGE_ROLES = ["ADMIN", "LAB_STAFF"];            // receive stock, donors, alerts, thresholds
const ISSUE_ROLES = ["ADMIN", "LAB_STAFF", "DOCTOR"];   // issue units
const LEDGER_ROLES = ["ADMIN", "LAB_STAFF", "DOCTOR"];  // movement history

const GROUPS = ["A_POS", "A_NEG", "B_POS", "B_NEG", "AB_POS", "AB_NEG", "O_POS", "O_NEG"];
const groupLabel = group => (group || "").replace("_POS", "+").replace("_NEG", "−");

function idempotencyKey() {
  // A new key per submit, so a double-click or network retry can't receive/issue twice.
  if (typeof crypto !== "undefined" && crypto.randomUUID) return crypto.randomUUID();
  return Date.now() + "-" + Math.random().toString(36).slice(2);
}

function formatDate(value) {
  if (!value) return "—";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleDateString();
}

function formatDateTime(value) {
  if (!value) return "—";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString();
}

export function BloodBankPage() {
  const { staff } = useAuth();
  const role = staff?.role;
  const canManage = MANAGE_ROLES.includes(role);
  const canIssue = ISSUE_ROLES.includes(role);
  const canLedger = LEDGER_ROLES.includes(role);

  const [stock, setStock] = useState([]);
  const [requests, setRequests] = useState([]);
  const [donors, setDonors] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    try {
      const [stockData, requestData, donorData, alertData] = await Promise.all([
        api("/blood-bank/stock"),
        api("/blood-bank/requests"),
        canManage ? api("/blood-bank/donors") : Promise.resolve([]),
        canManage ? api("/blood-bank/alerts") : Promise.resolve([]),
      ]);
      setStock(Array.isArray(stockData) ? stockData : []);
      setRequests(Array.isArray(requestData) ? requestData : []);
      setDonors(Array.isArray(donorData) ? donorData : []);
      setAlerts(Array.isArray(alertData) ? alertData : []);
      setError("");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [canManage]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => { if (active) load(); });
    return () => { active = false; };
  }, [load]);

  const selected = stock.find(item => item.stockId === selectedId) || null;
  const lowStock = stock.filter(item => item.low);

  async function run(action, successMessage) {
    setBusy(true); setError(""); setNotice("");
    try {
      const result = await action();
      setNotice(typeof successMessage === "function" ? successMessage(result) : successMessage);
      await load();
      return true;
    } catch (err) {
      setError(err.message);
      return false;
    } finally {
      setBusy(false);
    }
  }

  const notifyDonors = item => run(
    () => api("/blood-bank/stock/" + item.stockId + "/donor-alerts", { method: "POST" }),
    result => result.donorsNotified > 0
      ? result.donorsNotified + " eligible " + groupLabel(item.bloodGroup) + " donor(s) alerted (simulated)."
        + (result.donorsSkipped ? " " + result.donorsSkipped + " already had an unanswered alert." : "")
      : "No new donors to alert for " + groupLabel(item.bloodGroup) + "."
        + (result.donorsSkipped ? " " + result.donorsSkipped + " already have an unanswered alert." : " Register eligible donors first."));

  return <>
    <div className="page-title-row">
      <div><span className="eyebrow">BLOOD BANK</span><h1>Blood bank</h1><p>Stock by blood group, expiry-aware batches, low-stock alerts and donor notifications.</p></div>
      <button className="button button-outline" onClick={load} disabled={loading || busy}><RefreshCw size={16} /> Refresh</button>
    </div>

    {error && <div className="notice notice-warning">{error}</div>}
    {notice && <div className="notice">{notice}</div>}

    {lowStock.map(item => <div className="blood-low-alert" key={item.stockId} role="alert">
      <AlertTriangle size={18} />
      <span><b>{groupLabel(item.bloodGroup)} is low:</b> {item.unitsAvailable} unit{item.unitsAvailable === 1 ? "" : "s"} left (alert at {item.lowStockThreshold} or fewer).</span>
      {canManage && <button className="button button-primary" disabled={busy} onClick={() => notifyDonors(item)}><BellRing size={15} /> Notify donors</button>}
    </div>)}

    {requests.length > 0 && <section className="panel blood-requests-panel">
      <div className="panel-heading"><div><h2>Patients needing blood</h2><p>Flagged by triage and still in the queue</p></div><Droplet size={19} /></div>
      <div className="table-wrap"><table className="data-table"><thead><tr><th>Ticket</th><th>Patient</th><th>Blood group</th><th>Department</th><th>Priority</th><th>Status</th></tr></thead><tbody>
        {requests.map(r => <tr key={r.ticketId}><td><b>{r.ticketNumber}</b></td><td>{r.patientName} <small className="queue-patient-id">Patient #{r.patientId}</small></td><td>{r.patientBloodGroup ? <b>{groupLabel(r.patientBloodGroup)}</b> : "Not recorded"}</td><td>{r.departmentName}</td><td><span className={"pill priority-" + String(r.priorityLevel).toLowerCase()}>{r.priorityLevel}</span></td><td>{String(r.status).replace("_", " ")}</td></tr>)}
      </tbody></table></div>
    </section>}

    <section className="panel">
      <div className="panel-heading"><div><h2>Stock levels</h2><p>Select a blood group to see its batches and history{canIssue || canManage ? ", or to receive and issue units" : ""}</p></div></div>
      {loading ? <div className="empty-panel">Loading stock…</div> : stock.length === 0 ? <div className="empty-panel">No blood stock records for your facility.</div> :
        <div className="blood-grid">{stock.map(item => <button key={item.stockId} className={"blood-card" + (item.low ? " blood-card-low" : "") + (item.stockId === selectedId ? " blood-card-selected" : "")} onClick={() => setSelectedId(item.stockId === selectedId ? null : item.stockId)} aria-pressed={item.stockId === selectedId}>
          <span className="blood-card-group">{groupLabel(item.bloodGroup)}</span>
          <strong>{item.unitsAvailable}<small> units</small></strong>
          <span className="blood-card-meta">{item.issuableUnits} issuable{item.expiringSoonUnits > 0 && <> · <em>{item.expiringSoonUnits} expiring ≤7 days</em></>}</span>
          {item.low ? <span className="pill priority-emergency">LOW</span> : <span className="pill priority-normal">OK</span>}
        </button>)}</div>}
    </section>

    {selected && <StockDetail key={selected.stockId} item={selected} canManage={canManage} canIssue={canIssue} canLedger={canLedger} busy={busy} run={run} />}

    {canManage && <div className="two-column">
      <DonorsPanel donors={donors} busy={busy} run={run} />
      <AlertsPanel alerts={alerts} busy={busy} run={run} />
    </div>}
  </>;
}

function StockDetail({ item, canManage, canIssue, canLedger, busy, run }) {
  const [batches, setBatches] = useState([]);
  const [movements, setMovements] = useState([]);
  const [receive, setReceive] = useState({ batchReference: "", expiryDate: "", units: "", sourceReference: "", reason: "Received from regional blood service" });
  const [issue, setIssue] = useState({ units: "", reason: "" });
  const [threshold, setThreshold] = useState(String(item.lowStockThreshold));
  const [today] = useState(() => new Date().toISOString().slice(0, 10)); // earliest allowed expiry
  const label = groupLabel(item.bloodGroup);

  const loadDetail = useCallback(async () => {
    const [batchData, movementData] = await Promise.all([
      api("/blood-bank/stock/" + item.stockId + "/batches"),
      canLedger ? api("/blood-bank/stock/" + item.stockId + "/movements") : Promise.resolve([]),
    ]);
    setBatches(Array.isArray(batchData) ? batchData : []);
    setMovements(Array.isArray(movementData) ? movementData : []);
  }, [item.stockId, canLedger]);

  // Reload batches/history whenever the stock figures change (after receive/issue).
  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => { if (active) loadDetail().catch(() => {}); });
    return () => { active = false; };
  }, [loadDetail, item.unitsAvailable]);

  async function submitReceive(event) {
    event.preventDefault();
    const ok = await run(() => api("/blood-bank/inventory/batches", {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey() },
      body: { stockId: item.stockId, batchReference: receive.batchReference.trim(), expiryDate: receive.expiryDate, units: Number(receive.units), sourceReference: receive.sourceReference.trim() || null, reason: receive.reason.trim() },
    }), result => "Received " + result.unitsChanged + " unit(s) of " + label + ". Stock is now " + result.quantityAfter + ".");
    if (ok) setReceive(r => ({ ...r, batchReference: "", expiryDate: "", units: "", sourceReference: "" }));
  }

  async function submitIssue(event) {
    event.preventDefault();
    const ok = await run(() => api("/blood-bank/inventory/issues", {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey() },
      body: { stockId: item.stockId, units: Number(issue.units), reason: issue.reason.trim() },
    }), result => "Issued " + result.unitsChanged + " unit(s) of " + label + " from " + result.allocations.map(a => a.batchReference).join(", ") + " (soonest expiry first). Stock is now " + result.quantityAfter + ".");
    if (ok) setIssue({ units: "", reason: "" });
  }

  const saveThreshold = event => {
    event.preventDefault();
    run(() => api("/blood-bank/stock/" + item.stockId + "/threshold", { method: "PATCH", body: { lowStockThreshold: Number(threshold) } }),
      label + " low-stock alert now triggers at " + threshold + " units or fewer.");
  };

  return <section className="panel blood-detail">
    <div className="panel-heading"><div><h2>{label} stock: {item.unitsAvailable} units</h2><p>{item.issuableUnits} verified, unexpired units can be issued · last updated {formatDateTime(item.lastUpdated)}</p></div>
      {canManage && <form className="threshold-form" onSubmit={saveThreshold}><label>Low-stock alert at<input type="number" min="0" max="1000" required value={threshold} onChange={e => setThreshold(e.target.value)} /></label><button className="button button-outline" disabled={busy}>Save</button></form>}
    </div>

    {(canManage || canIssue) && <div className="two-column blood-forms">
      {canManage && <form className="form-grid blood-form" onSubmit={submitReceive}>
        <h3 className="span-two"><PackagePlus size={16} /> Receive a batch</h3>
        <label>Batch reference<input required maxLength={100} value={receive.batchReference} onChange={e => setReceive({ ...receive, batchReference: e.target.value })} placeholder="e.g. ZNBTS-2026-1104" /></label>
        <label>Expiry date<input type="date" required min={today} value={receive.expiryDate} onChange={e => setReceive({ ...receive, expiryDate: e.target.value })} /></label>
        <label>Units<input type="number" min="1" required value={receive.units} onChange={e => setReceive({ ...receive, units: e.target.value })} /></label>
        <label>Source (optional)<input maxLength={255} value={receive.sourceReference} onChange={e => setReceive({ ...receive, sourceReference: e.target.value })} placeholder="Supplier or donation drive" /></label>
        <label className="span-two">Reason<input required maxLength={500} value={receive.reason} onChange={e => setReceive({ ...receive, reason: e.target.value })} /></label>
        <button className="button button-primary span-two" disabled={busy}>Receive {label} units</button>
      </form>}
      {canIssue && <form className="form-grid blood-form" onSubmit={submitIssue}>
        <h3 className="span-two"><Send size={16} /> Issue units</h3>
        <label>Units<input type="number" min="1" max={item.issuableUnits || undefined} required value={issue.units} onChange={e => setIssue({ ...issue, units: e.target.value })} /></label>
        <label>Reason<input required maxLength={500} value={issue.reason} onChange={e => setIssue({ ...issue, reason: e.target.value })} placeholder="e.g. Emergency transfusion, ticket F1-…" /></label>
        <p className="span-two blood-form-hint">Units are taken from the batch that expires soonest (FEFO). Expired and unverified units are never issued.</p>
        <button className="button button-primary span-two" disabled={busy || item.issuableUnits === 0}>Issue {label} units</button>
      </form>}
    </div>}

    <h3 className="blood-subheading">Batches</h3>
    {batches.length === 0 ? <div className="empty-panel">No batches recorded.</div> :
      <div className="table-wrap"><table className="data-table"><thead><tr><th>Batch</th><th>Expiry</th><th>Remaining</th><th>Status</th></tr></thead><tbody>
        {batches.map(b => <tr key={b.id}><td><b>{b.batchReference}</b>{b.sourceReference && <small className="queue-patient-id">{b.sourceReference}</small>}</td><td>{formatDate(b.expiryDate)}</td><td>{b.remainingQuantity} / {b.openingQuantity}</td><td>{b.expired ? <span className="pill priority-emergency">EXPIRED</span> : b.verificationStatus !== "VERIFIED" ? <span className="pill priority-urgent">UNVERIFIED</span> : b.expiringSoon ? <span className="pill priority-urgent">EXPIRING SOON</span> : <span className="pill priority-normal">OK</span>}</td></tr>)}
      </tbody></table></div>}

    {canLedger && <>
      <h3 className="blood-subheading">Recent movements</h3>
      {movements.length === 0 ? <div className="empty-panel">No movements yet.</div> :
        <div className="table-wrap"><table className="data-table"><thead><tr><th>When</th><th>Type</th><th>Units</th><th>Balance</th><th>Batch</th><th>Reason</th><th>By</th></tr></thead><tbody>
          {movements.map(m => <tr key={m.id}><td>{formatDateTime(m.createdAt)}</td><td>{m.movementType}</td><td>{m.movementType === "ISSUED" ? "−" : "+"}{m.unitsChanged}</td><td>{m.quantityBefore} → {m.quantityAfter}</td><td>{m.batchReference || "—"}</td><td>{m.reason || "—"}</td><td>{m.recordedBy || "System"}</td></tr>)}
        </tbody></table></div>}
    </>}
  </section>;
}

function DonorsPanel({ donors, busy, run }) {
  const [form, setForm] = useState({ name: "", bloodGroup: "O_NEG", contact: "", eligible: true });
  const [filter, setFilter] = useState("ALL");
  const visible = donors.filter(d => filter === "ALL" || d.bloodGroup === filter);

  async function submit(event) {
    event.preventDefault();
    const ok = await run(() => api("/blood-bank/donors", { method: "POST", body: { ...form, name: form.name.trim(), contact: form.contact.trim() || null } }),
      form.name.trim() + " registered as a " + groupLabel(form.bloodGroup) + " donor.");
    if (ok) setForm(f => ({ ...f, name: "", contact: "" }));
  }

  return <section className="panel">
    <div className="panel-heading"><div><h2>Donors</h2><p>{donors.length} registered · eligible donors receive shortage alerts</p></div><label className="donor-filter">Group <select value={filter} onChange={e => setFilter(e.target.value)}><option value="ALL">All</option>{GROUPS.map(g => <option key={g} value={g}>{groupLabel(g)}</option>)}</select></label></div>
    <form className="form-grid" onSubmit={submit}>
      <label>Full name<input required maxLength={255} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
      <label>Blood group<select value={form.bloodGroup} onChange={e => setForm({ ...form, bloodGroup: e.target.value })}>{GROUPS.map(g => <option key={g} value={g}>{groupLabel(g)}</option>)}</select></label>
      <label>Contact<input maxLength={255} value={form.contact} onChange={e => setForm({ ...form, contact: e.target.value })} placeholder="Phone number" /></label>
      <label className="checkbox-label"><input type="checkbox" checked={form.eligible} onChange={e => setForm({ ...form, eligible: e.target.checked })} /> Eligible to donate</label>
      <button className="button button-primary span-two" disabled={busy}><UserPlus size={15} /> Register donor</button>
    </form>
    <div className="patient-list donor-list">{visible.length === 0 ? <div className="empty-panel">No donors for this group.</div> : visible.map(d => <div className="patient-list-row" key={d.id}>
      <span className="patient-initials">{groupLabel(d.bloodGroup)}</span>
      <span><b>{d.name}</b><small>{d.contact || "No contact"} · {d.eligible ? "Eligible" : "Not eligible"}</small></span>
    </div>)}</div>
  </section>;
}

function AlertsPanel({ alerts, busy, run }) {
  const reply = (alert, status) => run(
    () => api("/blood-bank/alerts/" + alert.id, { method: "PATCH", body: { responseStatus: status } }),
    alert.donorName + (status === "ACCEPTED" ? " accepted the donation request." : " declined the donation request."));

  return <section className="panel">
    <div className="panel-heading"><div><h2>Donor alerts</h2><p>Simulated notifications: recorded here, no SMS is sent</p></div><BellRing size={19} /></div>
    {alerts.length === 0 ? <div className="empty-panel">No alerts sent yet. Use “Notify donors” on a low-stock warning.</div> :
      <div className="alert-list">{alerts.map(a => <div className="alert-row" key={a.id}>
        <span className="patient-initials">{groupLabel(a.bloodGroup)}</span>
        <span className="alert-row-text"><b>{a.donorName}</b><small>{a.donorContact || "No contact"} · sent {formatDateTime(a.dateSent)}</small></span>
        {a.responseStatus === "PENDING"
          ? <span className="alert-row-actions"><button className="table-action" disabled={busy} onClick={() => reply(a, "ACCEPTED")}><CheckCircle2 size={12} /> Accepted</button><button className="table-action" disabled={busy} onClick={() => reply(a, "DECLINED")}>Declined</button></span>
          : <span className={"pill " + (a.responseStatus === "ACCEPTED" ? "status-waiting" : "priority-normal")}>{a.responseStatus}</span>}
      </div>)}</div>}
  </section>;
}
