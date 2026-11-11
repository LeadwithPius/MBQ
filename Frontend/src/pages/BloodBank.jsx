import { useCallback, useEffect, useState } from "react";
import { Activity, AlertTriangle, BellRing, CheckCircle2, Droplet, HeartHandshake, Trash2, PackagePlus, RefreshCw, Send, UserPlus, XCircle } from "lucide-react";
import { api } from "../services/api";
import { useAuth } from "../context/useAuth";

// Who may do what (mirrors the backend @PreAuthorize rules).
const MANAGE_ROLES = ["ADMIN", "LAB_STAFF"];            // receive stock, donors, alerts, thresholds
const ISSUE_ROLES = ["ADMIN", "LAB_STAFF", "DOCTOR"];   // issue units
const LEDGER_ROLES = ["ADMIN", "LAB_STAFF", "DOCTOR"];  // movement history
const REQUEST_ROLES = ["DOCTOR", "NURSE"];
const REVIEW_REQUEST_ROLES = ["ADMIN", "LAB_STAFF"];

const GROUPS = ["A_POS", "A_NEG", "B_POS", "B_NEG", "AB_POS", "AB_NEG", "O_POS", "O_NEG"];
const OUTFLOW = ["ISSUED", "EXPIRED", "WASTED", "TRANSFER_OUT", "ADJUSTMENT_OUT"];
const groupLabel = group => (group || "").replace("_POS", "+").replace("_NEG", "−");
const COMPATIBLE_STOCK = {
  O_NEG: ["O_NEG"],
  O_POS: ["O_POS", "O_NEG"],
  A_NEG: ["A_NEG", "O_NEG"],
  A_POS: ["A_POS", "A_NEG", "O_POS", "O_NEG"],
  B_NEG: ["B_NEG", "O_NEG"],
  B_POS: ["B_POS", "B_NEG", "O_POS", "O_NEG"],
  AB_NEG: ["AB_NEG", "A_NEG", "B_NEG", "O_NEG"],
  AB_POS: GROUPS,
};

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
  const canRequest = REQUEST_ROLES.includes(role);
  const canReviewRequests = REVIEW_REQUEST_ROLES.includes(role);

  const [stock, setStock] = useState([]);
  const [forecasts, setForecasts] = useState([]);
  const [demoResult, setDemoResult] = useState(null);
  const [demoLoading, setDemoLoading] = useState(false);
  const [requests, setRequests] = useState([]); // triage flags on active queue tickets
  const [bloodRequests, setBloodRequests] = useState([]);
  const [patients, setPatients] = useState([]);
  const [donors, setDonors] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [donations, setDonations] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    try {
      const [stockData, requestData, bloodRequestData, patientData, donorData, alertData, forecastData, donationData] = await Promise.all([
        api("/blood-bank/stock"),
        api("/blood-bank/requests"),
        api("/blood-bank/blood-requests"),
        canRequest ? api("/patients") : Promise.resolve([]),
        canManage ? api("/blood-bank/donors") : Promise.resolve([]),
        canManage ? api("/blood-bank/alerts") : Promise.resolve([]),
        api("/blood-intelligence/forecast"),
        canManage ? api("/blood-bank/donations") : Promise.resolve([]),
      ]);
      setDonations(Array.isArray(donationData) ? donationData : []);
      setStock(Array.isArray(stockData) ? stockData : []);
      setForecasts(Array.isArray(forecastData) ? forecastData : []);
      setRequests(Array.isArray(requestData) ? requestData : []);
      setBloodRequests(Array.isArray(bloodRequestData) ? bloodRequestData : []);
      setPatients(Array.isArray(patientData) ? patientData : []);
      setDonors(Array.isArray(donorData) ? donorData : []);
      setAlerts(Array.isArray(alertData) ? alertData : []);
      setError("");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [canManage, canRequest]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => { if (active) load(); });
    // Stock levels and request status are shared operational data. Poll so
    // changes made by another staff member appear without a manual refresh.
    const timer = window.setInterval(async () => {
      try {
        const [stockData, requestData] = await Promise.all([
          api("/blood-bank/stock"),
          api("/blood-bank/blood-requests"),
        ]);
        if (!active) return;
        setStock(Array.isArray(stockData) ? stockData : []);
        setBloodRequests(Array.isArray(requestData) ? requestData : []);
      } catch {
        // Keep the last successful snapshot visible during temporary outages.
      }
    }, 10000);
    return () => { active = false; window.clearInterval(timer); };
  }, [load]);

  async function runSyntheticDemo() {
    setDemoLoading(true);
    setError("");
    try {
      const result = await api("/blood-intelligence/demo-forecast");
      setDemoResult(result);
    } catch (err) {
      setError("Synthetic demo could not run: " + err.message);
    } finally {
      setDemoLoading(false);
    }
  }

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
      <div className="button-row"><button className="button button-outline" onClick={runSyntheticDemo} disabled={demoLoading}><Activity size={16} /> {demoLoading ? "Running demo…" : "Run synthetic ML demo"}</button><button className="button button-outline" onClick={load} disabled={loading || busy}><RefreshCw size={16} /> Refresh</button></div>
    </div>

    {error && <div className="notice notice-warning">{error}</div>}
    {notice && <div className="notice">{notice}</div>}

    <section className="panel">
      <div className="panel-heading"><div><h2>Blood intelligence</h2><p>XGBoost demand forecasting with a validated 28-day baseline fallback · planning support only</p></div><Activity size={19} /></div>
      {forecasts.length === 0 ? <div className="empty-panel">No stock records available for forecasting.</div> :
        <div className="table-wrap"><table className="data-table"><thead><tr><th>Group</th><th>Forecast model</th><th>Usable</th><th>Avg daily demand</th><th>7-day demand</th><th>14-day demand</th><th>Stock-out estimate</th><th>Risk</th><th>Replenish</th></tr></thead><tbody>
          {forecasts.map(f => <tr key={f.stockId}><td><b>{groupLabel(f.bloodGroup)}</b></td><td><span className={"pill " + (f.forecastMethod === "XGBOOST" ? "status-waiting" : "priority-normal")}>{f.forecastMethod === "XGBOOST" ? "XGBoost" : "Baseline"}</span>{f.validationMae != null && <small className="queue-patient-id">Validation MAE: {Number(f.validationMae).toFixed(2)}</small>}</td><td>{f.usableUnits}</td><td>{f.forecastAvailable ? f.averageDailyDemand : "Insufficient data"}</td><td>{f.forecastAvailable ? f.predictedDemand7Days : "—"}</td><td>{f.forecastAvailable ? f.predictedDemand14Days : "—"}</td><td>{f.estimatedDaysUntilStockout == null ? "—" : f.estimatedDaysUntilStockout + " days"}</td><td><span className={"pill " + (f.riskLevel === "CRITICAL" || f.riskLevel === "HIGH" ? "priority-emergency" : f.riskLevel === "MODERATE" ? "priority-urgent" : "priority-normal")}>{f.riskLevel.replaceAll("_", " ")}</span></td><td>{f.forecastAvailable ? f.suggestedReplenishmentUnits : "—"}</td></tr>)}
        </tbody></table></div>}
      <p className="report-note">XGBoost uses up to 365 days of daily issue history and is shown only when it beats the moving-average baseline on a chronological holdout. Otherwise, the dashboard uses the transparent 28-day moving-average fallback. The ledger reflects fulfilled issues, not unmet requests. Forecasts are planning support, not clinical decisions.</p>
    </section>

    {demoResult && <section className="panel">
      <div className="panel-heading"><div><h2>Synthetic XGBoost demonstration</h2><p>Generated sample data · separate from live inventory and patient records</p></div><span className="pill priority-urgent">DEMO DATA</span></div>
      <div className="notice notice-warning" role="note">{demoResult.notice || "Synthetic data only. Not actual hospital demand."}</div>
      <div className="stats-grid">
        <div className="stat-card"><span>Generated series</span><strong>{demoResult.seriesCount ?? "—"}</strong></div>
        <div className="stat-card"><span>Facilities simulated</span><strong>{demoResult.facilityCount ?? "—"}</strong></div>
        <div className="stat-card"><span>History</span><strong>{demoResult.historyDays ?? "—"} days</strong></div>
        <div className="stat-card"><span>Model result</span><strong>{demoResult.status || "Unavailable"}</strong></div>
      </div>
      <p className="report-note">{demoResult.message || "No model message returned."} {demoResult.validationMae != null && <>XGBoost holdout MAE: <b>{Number(demoResult.validationMae).toFixed(2)}</b>. </>}{demoResult.baselineMae != null && <>Baseline MAE: <b>{Number(demoResult.baselineMae).toFixed(2)}</b>.</>}</p>
      {Array.isArray(demoResult.forecasts) && demoResult.forecasts.length > 0
        ? <div className="table-wrap"><table className="data-table"><thead><tr><th>Demo stock ID</th><th>7-day demand</th><th>14-day demand</th><th>Method</th></tr></thead><tbody>
          {demoResult.forecasts.slice(0, 12).map(row => <tr key={row.stockId}><td>{row.stockId}</td><td>{row.predictedDemand7Days}</td><td>{row.predictedDemand14Days}</td><td>{demoResult.status === "TRAINED" ? "XGBoost" : "Baseline comparison"}</td></tr>)}
        </tbody></table></div>
        : <div className="empty-panel">The generated dataset ran, but XGBoost did not pass the validation gate. The result is still useful for demonstrating why a baseline comparison matters.</div>}
      <p className="report-note">This demo is reproducible and does not create blood-stock movements, batches, donors, patient records or Neon database rows.</p>
    </section>}

    {lowStock.map(item => <div className="blood-low-alert" key={item.stockId} role="alert">
      <AlertTriangle size={18} />
      <span><b>{groupLabel(item.bloodGroup)} is low:</b> {item.unitsAvailable} unit{item.unitsAvailable === 1 ? "" : "s"} left (alert at {item.lowStockThreshold} or fewer).</span>
      {canManage && <button className="button button-primary" disabled={busy} onClick={() => notifyDonors(item)}><BellRing size={15} /> Notify donors</button>}
    </div>)}

    {(canRequest || canReviewRequests || bloodRequests.length > 0) && <BloodRequestsPanel
      requests={bloodRequests}
      patients={patients}
      stock={stock}
      canRequest={canRequest}
      canReview={canReviewRequests}
      busy={busy}
      run={run}
    />}

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

    {selected && <StockDetail key={selected.stockId} item={selected} patients={requests} canManage={canManage} canIssue={canIssue} canLedger={canLedger} busy={busy} run={run} />}

    {canManage && <div className="two-column">
      <DonorsPanel donors={donors} busy={busy} run={run} />
      <AlertsPanel alerts={alerts} busy={busy} run={run} />
    </div>}

    {canManage && <DonationsPanel donors={donors} donations={donations} busy={busy} run={run} />}
  </>;
}


function BloodRequestsPanel({ requests, patients, stock, canRequest, canReview, busy, run }) {
  const [form, setForm] = useState({ patientId: "", units: "1", priorityLevel: "NORMAL", reason: "" });
  const [stockChoices, setStockChoices] = useState({});
  const [reviewNotes, setReviewNotes] = useState({});

  async function submit(event) {
    event.preventDefault();
    const patient = patients.find(item => String(item.id) === form.patientId);
    const ok = await run(
      () => api("/blood-bank/blood-requests", {
        method: "POST",
        body: {
          patientId: Number(form.patientId),
          units: Number(form.units),
          priorityLevel: form.priorityLevel,
          reason: form.reason.trim(),
        },
      }),
      "Blood request submitted for " + (patient?.name || "the selected patient") + ". It is now awaiting blood-bank review."
    );
    if (ok) setForm({ patientId: "", units: "1", priorityLevel: "NORMAL", reason: "" });
  }

  const review = (request, status) => run(
    () => api("/blood-bank/blood-requests/" + request.id + "/review", {
      method: "PATCH",
      body: { status, notes: (reviewNotes[request.id] || "").trim() || null },
    }),
    "Blood request #" + request.id + " " + status.toLowerCase() + "."
  );

  const fulfil = request => {
    const stockId = Number(stockChoices[request.id]);
    if (!stockId) return;
    const selectedStock = stock.find(item => item.stockId === stockId);
    return run(
      () => api("/blood-bank/blood-requests/" + request.id + "/fulfil", {
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey() },
        body: { stockId },
      }),
      result => "Request #" + request.id + " fulfilled. " + result.unitsChanged + " unit(s) issued from " +
        (selectedStock ? groupLabel(selectedStock.bloodGroup) : "selected stock") + "."
    );
  };

  return <section className="panel blood-requests-panel">
    <div className="panel-heading">
      <div><h2>Blood requests</h2><p>Clinical requests, blood-bank approval and controlled fulfilment · updates automatically every 10 seconds</p></div>
      <Droplet size={19} />
    </div>
    {canRequest && <form className="form-grid blood-form" onSubmit={submit}>
      <h3 className="span-two">Request blood for a patient</h3>
      <label>Patient<select required value={form.patientId} onChange={e => setForm({ ...form, patientId: e.target.value })}>
        <option value="">Select patient</option>
        {patients.map(patient => <option key={patient.id} value={patient.id}>
          {patient.name} · #{patient.id} · {patient.bloodGroup ? groupLabel(patient.bloodGroup) : "Blood group unknown"}
        </option>)}
      </select></label>
      <label>Units<input type="number" min="1" max="10" required value={form.units} onChange={e => setForm({ ...form, units: e.target.value })} /></label>
      <label>Priority<select value={form.priorityLevel} onChange={e => setForm({ ...form, priorityLevel: e.target.value })}>
        <option value="NORMAL">Normal</option><option value="URGENT">Urgent</option><option value="EMERGENCY">Emergency</option>
      </select></label>
      <label className="span-two">Clinical reason<input required maxLength={500} value={form.reason} onChange={e => setForm({ ...form, reason: e.target.value })} placeholder="e.g. Ongoing blood loss following trauma" /></label>
      <p className="span-two blood-form-hint">Requests are linked to a patient and facility. The blood bank must approve them before units can be issued. Compatibility is checked again during fulfilment.</p>
      <button className="button button-primary span-two" disabled={busy || !patients.length || !form.patientId}>Submit blood request</button>
    </form>}

    <h3 className="blood-subheading">Request register ({requests.length})</h3>
    {requests.length === 0 ? <div className="empty-panel">No formal blood requests have been submitted.</div> :
      <div className="table-wrap"><table className="data-table"><thead><tr>
        <th>Patient</th><th>Group</th><th>Units</th><th>Priority</th><th>Reason</th><th>Requested by</th><th>Status</th>{canReview && <th>Blood-bank action</th>}
      </tr></thead><tbody>{requests.map(request => <tr key={request.id}>
        <td><b>{request.patientName}</b><small className="queue-patient-id">Patient #{request.patientId}</small></td>
        <td>{request.bloodGroup ? <b>{groupLabel(request.bloodGroup)}</b> : "Unknown"}</td>
        <td>{request.units}</td>
        <td><span className={"pill priority-" + String(request.priorityLevel).toLowerCase()}>{request.priorityLevel}</span></td>
        <td>{request.reason}</td><td>{request.requestedBy}<small className="queue-patient-id">{formatDateTime(request.requestedAt)}</small></td>
        <td><span className={"pill " + (request.status === "FULFILLED" ? "status-waiting" : request.status === "REJECTED" ? "priority-emergency" : "priority-urgent")}>{request.status}</span>{request.reviewNotes && <small className="queue-patient-id">{request.reviewNotes}</small>}</td>
        {canReview && <td>{request.status === "PENDING" ? <div className="blood-request-actions">
          <input className="table-input" maxLength={500} placeholder="Review note (optional)" value={reviewNotes[request.id] || ""} onChange={e => setReviewNotes(current => ({ ...current, [request.id]: e.target.value }))} />
          <button className="table-action" disabled={busy} onClick={() => review(request, "APPROVED")}>Approve</button>
          <button className="table-action table-action-blood" disabled={busy} onClick={() => review(request, "REJECTED")}>Reject</button>
        </div> : request.status === "APPROVED" ? <div className="blood-request-actions">
          <select aria-label={"Stock for request " + request.id} value={stockChoices[request.id] || ""} onChange={e => setStockChoices(current => ({ ...current, [request.id]: e.target.value }))}>
            <option value="">Select compatible stock</option>
            {stock.filter(item => item.issuableUnits > 0 && (COMPATIBLE_STOCK[request.bloodGroup] || ["O_NEG"]).includes(item.bloodGroup)).map(item => <option key={item.stockId} value={item.stockId}>{groupLabel(item.bloodGroup)} · {item.issuableUnits} issuable</option>)}
          </select>
          <button className="table-action" disabled={busy || !stockChoices[request.id]} onClick={() => fulfil(request)}>Fulfil & issue</button>
        </div> : request.status === "FULFILLED" ? <small>Issued from stock #{request.stockId}</small> : "—"}</td>}
      </tr>)}</tbody></table></div>}
  </section>;
}

function StockDetail({ item, patients = [], canManage, canIssue, canLedger, busy, run }) {
  const [batches, setBatches] = useState([]);
  const [movements, setMovements] = useState([]);
  const [receive, setReceive] = useState({ batchReference: "", expiryDate: "", units: "", sourceReference: "", reason: "Received from regional blood service" });
  const [issue, setIssue] = useState({ units: "", reason: "", patientId: "" });
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
      body: { stockId: item.stockId, units: Number(issue.units), reason: issue.reason.trim(), patientId: issue.patientId ? Number(issue.patientId) : null },
    }), result => "Issued " + result.unitsChanged + " unit(s) of " + label + " from " + result.allocations.map(a => a.batchReference).join(", ") + " (soonest expiry first). Stock is now " + result.quantityAfter + ".");
    if (ok) setIssue({ units: "", reason: "", patientId: "" });
  }

  const [waste, setWaste] = useState({ batchId: "", units: "", reason: "" });
  const wasteBatch = batches.find(b => String(b.id) === waste.batchId);

  const writeOffExpired = b => run(() => api("/blood-bank/inventory/write-offs", {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey() },
    body: { batchId: b.id, type: "EXPIRED", units: null, reason: "Expired on " + b.expiryDate },
  }), result => "Wrote off " + result.unitsChanged + " expired unit(s) from " + b.batchReference + ". Stock is now " + result.quantityAfter + ".");

  async function submitWaste(event) {
    event.preventDefault();
    const ok = await run(() => api("/blood-bank/inventory/write-offs", {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey() },
      body: { batchId: Number(waste.batchId), type: "WASTED", units: Number(waste.units), reason: waste.reason.trim() },
    }), result => "Recorded " + result.unitsChanged + " wasted unit(s). Stock is now " + result.quantityAfter + ".");
    if (ok) setWaste({ batchId: "", units: "", reason: "" });
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
        <label className="span-two">For patient (compatibility check)<select value={issue.patientId} onChange={e => setIssue({ ...issue, patientId: e.target.value })}>
          <option value="">Not for a listed patient</option>
          {patients.map(p => <option key={p.ticketId} value={p.patientId}>{p.patientName} · {p.ticketNumber} · {p.patientBloodGroup ? groupLabel(p.patientBloodGroup) : "group unknown"}</option>)}
        </select></label>
        <p className="span-two blood-form-hint">Choosing a patient blocks incompatible blood (ABO/RhD); an unknown group only allows O−. Units are taken from the batch that expires soonest (FEFO). Expired and unverified units are never issued.</p>
        <button className="button button-primary span-two" disabled={busy || item.issuableUnits === 0}>Issue {label} units</button>
      </form>}
    </div>}

    <h3 className="blood-subheading">Batches</h3>
    {batches.length === 0 ? <div className="empty-panel">No batches recorded.</div> :
      <div className="table-wrap"><table className="data-table"><thead><tr><th>Batch</th><th>Expiry</th><th>Remaining</th><th>Status</th></tr></thead><tbody>
        {batches.map(b => <tr key={b.id}><td><b>{b.batchReference}</b>{b.sourceReference && <small className="queue-patient-id">{b.sourceReference}</small>}</td><td>{formatDate(b.expiryDate)}</td><td>{b.remainingQuantity} / {b.openingQuantity}</td><td>{b.expired ? <><span className="pill priority-emergency">EXPIRED</span>{canManage && b.remainingQuantity > 0 && <button className="table-action table-action-blood" disabled={busy} onClick={() => writeOffExpired(b)}><Trash2 size={12} /> Write off</button>}</> : b.verificationStatus !== "VERIFIED" ? <><span className="pill priority-urgent">{b.verificationStatus.replaceAll("_", " ")}</span>{canManage && b.verificationStatus === "PENDING_VERIFICATION" && <button className="table-action" disabled={busy} onClick={() => run(() => api("/blood-bank/inventory/batches/" + b.id + "/verify", { method: "PATCH" }), "Batch " + b.batchReference + " verified.")}>Verify batch</button>}</> : b.expiringSoon ? <span className="pill priority-urgent">EXPIRING SOON</span> : <span className="pill priority-normal">OK</span>}</td></tr>)}
      </tbody></table></div>}

    {canManage && batches.some(b => b.remainingQuantity > 0) && <form className="form-grid blood-form waste-form" onSubmit={submitWaste}>
      <h3 className="span-two"><Trash2 size={16} /> Record wastage</h3>
      <label>Batch<select required value={waste.batchId} onChange={e => setWaste({ ...waste, batchId: e.target.value })}>
        <option value="">Select a batch</option>
        {batches.filter(b => b.remainingQuantity > 0).map(b => <option key={b.id} value={b.id}>{b.batchReference} · {b.remainingQuantity} left</option>)}
      </select></label>
      <label>Units<input type="number" min="1" max={wasteBatch?.remainingQuantity || undefined} required value={waste.units} onChange={e => setWaste({ ...waste, units: e.target.value })} /></label>
      <label className="span-two">Reason<input required maxLength={500} value={waste.reason} onChange={e => setWaste({ ...waste, reason: e.target.value })} placeholder="e.g. Bag punctured, cold chain broken" /></label>
      <button className="button button-outline span-two" disabled={busy}>Record wasted units</button>
    </form>}

    {canLedger && <>
      <h3 className="blood-subheading">Recent movements</h3>
      {movements.length === 0 ? <div className="empty-panel">No movements yet.</div> :
        <div className="table-wrap"><table className="data-table"><thead><tr><th>When</th><th>Type</th><th>Units</th><th>Balance</th><th>Batch</th><th>Reason</th><th>By</th></tr></thead><tbody>
          {movements.map(m => <tr key={m.id}><td>{formatDateTime(m.createdAt)}</td><td>{m.movementType}</td><td>{OUTFLOW.includes(m.movementType) ? "−" : "+"}{m.unitsChanged}</td><td>{m.quantityBefore} → {m.quantityAfter}</td><td>{m.batchReference || "—"}</td><td>{m.reason || "—"}</td><td>{m.recordedBy || "System"}</td></tr>)}
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

/** Workflow C: record a donation, then screen it. Only a passed donation becomes stock (as a batch awaiting verification). */
function DonationsPanel({ donors, donations, busy, run }) {
  const eligible = donors.filter(d => d.eligible);
  const [form, setForm] = useState({ donorId: "", units: "1" });
  const [notes, setNotes] = useState({});
  const awaiting = donations.filter(d => d.status === "COLLECTED");
  const screened = donations.filter(d => d.status !== "COLLECTED").slice(0, 15);

  async function submit(event) {
    event.preventDefault();
    const donor = donors.find(d => String(d.id) === form.donorId);
    const ok = await run(() => api("/blood-bank/donations", { method: "POST", body: { donorId: Number(form.donorId), units: Number(form.units) } }),
      "Donation from " + (donor?.name || "donor") + " recorded. It is not in stock until it passes screening.");
    if (ok) setForm({ donorId: "", units: "1" });
  }

  const screen = (d, passed) => run(
    () => api("/blood-bank/donations/" + d.id + "/screen", { method: "PATCH", body: { passed, notes: (notes[d.id] || "").trim() || null } }),
    result => passed
      ? d.donorName + "'s donation passed. Batch " + result.batchReference + " (" + result.units + " " + groupLabel(result.bloodGroup) + ") was added and must be verified before it can be issued."
      : d.donorName + "'s donation failed screening and was discarded. Stock was not changed.");

  return <section className="panel">
    <div className="panel-heading"><div><h2>Donations</h2><p>Collected blood enters stock only after it passes screening</p></div><HeartHandshake size={19} /></div>
    <form className="form-grid" onSubmit={submit}>
      <label>Donor<select required value={form.donorId} onChange={e => setForm({ ...form, donorId: e.target.value })}>
        <option value="">Select an eligible donor</option>
        {eligible.map(d => <option key={d.id} value={d.id}>{d.name} · {groupLabel(d.bloodGroup)}</option>)}
      </select></label>
      <label>Units<select value={form.units} onChange={e => setForm({ ...form, units: e.target.value })}><option value="1">1</option><option value="2">2</option></select></label>
      <p className="span-two blood-form-hint">A donor must wait 56 days between donations. Ineligible donors cannot be selected.</p>
      <button className="button button-primary span-two" disabled={busy || !form.donorId}><HeartHandshake size={15} /> Record donation</button>
    </form>

    <h3 className="blood-subheading">Awaiting screening ({awaiting.length})</h3>
    {awaiting.length === 0 ? <div className="empty-panel">No donations waiting for screening.</div> :
      <div className="table-wrap"><table className="data-table"><thead><tr><th>Donor</th><th>Group</th><th>Units</th><th>Collected</th><th>Screening notes</th><th>Outcome</th></tr></thead><tbody>
        {awaiting.map(d => <tr key={d.id}><td><b>{d.donorName}</b></td><td>{groupLabel(d.bloodGroup)}</td><td>{d.units}</td><td>{formatDateTime(d.collectedAt)}</td>
          <td><input className="table-input" maxLength={1000} value={notes[d.id] || ""} onChange={e => setNotes({ ...notes, [d.id]: e.target.value })} placeholder="e.g. TTI screen negative" /></td>
          <td><span className="alert-row-actions"><button className="table-action" disabled={busy} onClick={() => screen(d, true)}><CheckCircle2 size={12} /> Passed</button><button className="table-action table-action-blood" disabled={busy} onClick={() => screen(d, false)}><XCircle size={12} /> Failed</button></span></td></tr>)}
      </tbody></table></div>}

    {screened.length > 0 && <>
      <h3 className="blood-subheading">Recently screened</h3>
      <div className="table-wrap"><table className="data-table"><thead><tr><th>Donor</th><th>Group</th><th>Units</th><th>Result</th><th>Batch</th><th>Notes</th></tr></thead><tbody>
        {screened.map(d => <tr key={d.id}><td><b>{d.donorName}</b><small className="queue-patient-id">{formatDateTime(d.screenedAt)} · {d.screenedByName}</small></td><td>{groupLabel(d.bloodGroup)}</td><td>{d.units}</td>
          <td>{d.status === "PASSED" ? <span className="pill priority-normal">PASSED</span> : <span className="pill priority-emergency">FAILED</span>}</td>
          <td>{d.batchReference ? <>{d.batchReference} <small className="queue-patient-id">{String(d.batchVerificationStatus || "").replaceAll("_", " ")}</small></> : "—"}</td><td>{d.screeningNotes || "—"}</td></tr>)}
      </tbody></table></div>
    </>}
  </section>;
}
