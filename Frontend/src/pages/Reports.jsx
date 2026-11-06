import { useCallback, useEffect, useState } from "react";
import { Activity, AlertTriangle, CheckCircle2, Clock3, Droplet, FlaskConical, Printer, RefreshCw, Users } from "lucide-react";
import { api } from "../services/api";

const groupLabel = group => (group || "").replace("_POS", "+").replace("_NEG", "−");
const prettyLabel = label => String(label || "").replace("_", " ").toLowerCase().replace(/^\w/, c => c.toUpperCase());

function localToday() {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}

function minutes(value) {
  return value === null || value === undefined ? "—" : value + " min";
}

/** Daily report (FR8): queue activity, waiting times and blood bank activity for one day. Admin only. */
export function ReportsPage() {
  const [date, setDate] = useState(localToday);
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async day => {
    setLoading(true);
    try {
      setReport(await api("/reports/daily?date=" + encodeURIComponent(day)));
      setError("");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => { if (active) load(date); });
    return () => { active = false; };
  }, [date, load]);

  const maxPriority = report ? Math.max(1, ...report.byPriority.map(r => r.count)) : 1;

  return <>
    <div className="page-title-row">
      <div><span className="eyebrow">REPORTS</span><h1>Daily report</h1><p>Queue activity, waiting times and blood bank activity for one day, across all facilities.</p></div>
      <div className="report-actions">
        <label className="report-date">Day<input type="date" value={date} max={localToday()} onChange={e => e.target.value && setDate(e.target.value)} /></label>
        <button className="button button-outline" onClick={() => load(date)} disabled={loading}><RefreshCw size={16} /> Refresh</button>
        <button className="button button-primary" onClick={() => window.print()} disabled={!report}><Printer size={16} /> Print</button>
      </div>
    </div>

    {error && <div className="notice notice-warning">{error}</div>}
    {loading && !report && <div className="panel empty-panel">Loading report…</div>}

    {report && <div className={loading ? "report-loading" : ""}>
      <p className="report-meta">Report for <b>{new Date(report.date + "T00:00:00").toLocaleDateString(undefined, { weekday: "long", year: "numeric", month: "long", day: "numeric" })}</b> · generated {new Date(report.generatedAt).toLocaleTimeString()}</p>

      <div className="stat-grid">
        <ReportStat label="Tickets issued" value={report.totalTickets} icon={Users} />
        <ReportStat label="Completed" value={report.completed} icon={CheckCircle2} tone="teal" />
        <ReportStat label="Average wait" value={minutes(report.averageWaitMinutes)} icon={Clock3} hint={report.patientsCalled + " patient(s) called"} />
        <ReportStat label="Longest wait" value={minutes(report.longestWaitMinutes)} icon={AlertTriangle} tone="amber" />
      </div>

      <div className="two-column">
        <section className="panel">
          <div className="panel-heading"><div><h2>Tickets by priority</h2><p>How urgent the day's patients were</p></div><Activity size={19} /></div>
          {report.byPriority.map(row => <div className="report-bar-row" key={row.label}>
            <span className={"pill priority-" + row.label.toLowerCase()}>{prettyLabel(row.label)}</span>
            <span className="report-bar"><i style={{ width: (row.count / maxPriority * 100) + "%" }} className={"report-bar-" + row.label.toLowerCase()} /></span>
            <b>{row.count}</b>
          </div>)}
          <h3 className="report-subheading">By status</h3>
          <div className="report-chips">{report.byStatus.map(row => <span className="report-chip" key={row.label}>{prettyLabel(row.label)} <b>{row.count}</b></span>)}</div>
          <p className="report-note">{report.triageObservations} triage observation(s) recorded · {report.bloodRequests} ticket(s) flagged as needing blood</p>
        </section>

        <section className="panel">
          <div className="panel-heading"><div><h2>Blood bank activity</h2><p>Units moved and donor alerts on this day</p></div><Droplet size={19} /></div>
          <dl className="report-figures">
            <div><dt>Units received</dt><dd>{report.unitsReceived}</dd></div>
            <div><dt>Units issued</dt><dd>{report.unitsIssued}</dd></div>
            <div><dt>Units written off</dt><dd>{report.unitsWrittenOff ?? 0}</dd></div>
            <div><dt>Donations collected</dt><dd>{report.donationsCollected ?? 0}</dd></div>
            <div><dt>Donations passed / failed</dt><dd>{report.donationsPassed ?? 0} / {report.donationsFailed ?? 0}</dd></div>
            <div><dt>Donor alerts sent</dt><dd>{report.donorAlertsSent}</dd></div>
            <div><dt>Accepted / declined</dt><dd>{report.donorAlertsAccepted} / {report.donorAlertsDeclined}</dd></div>
          </dl>
        </section>
      </div>

      <section className="panel">
        <div className="panel-heading"><div><h2>Laboratory</h2><p>Tests ordered from consultations on this day</p></div><FlaskConical size={19} /></div>
        <dl className="report-figures">
          <div><dt>Tests requested</dt><dd>{report.labTestsRequested ?? 0}</dd></div>
          <div><dt>Results completed</dt><dd>{report.labTestsCompleted ?? 0}</dd></div>
        </dl>
      </section>

      <section className="panel">
        <div className="panel-heading"><div><h2>By department</h2><p>Average wait = time from ticket issued to first being called</p></div></div>
        {report.byDepartment.length === 0 ? <div className="empty-panel">No tickets were issued on this day.</div> :
          <div className="table-wrap"><table className="data-table"><thead><tr><th>Department</th><th>Tickets</th><th>Completed</th><th>Cancelled</th><th>Still waiting</th><th>Average wait</th></tr></thead><tbody>
            {report.byDepartment.map(d => <tr key={d.departmentId}><td><b>{d.departmentName}</b></td><td>{d.tickets}</td><td>{d.completed}</td><td>{d.cancelled}</td><td>{d.stillWaiting}</td><td>{minutes(d.averageWaitMinutes)}</td></tr>)}
          </tbody></table></div>}
      </section>

      <section className="panel">
        <div className="panel-heading"><div><h2>Blood stock now</h2><p>Current units per blood group (live snapshot, not historical)</p></div></div>
        <div className="report-stock">{report.stock.map(s => <div key={s.facilityId + s.bloodGroup} className={"report-stock-cell" + (s.low ? " report-stock-low" : "")}>
          <b>{groupLabel(s.bloodGroup)}</b><span>{s.unitsAvailable}</span>{s.low && <small>LOW</small>}
        </div>)}</div>
      </section>
    </div>}
  </>;
}

function ReportStat({ label, value, icon: Icon, tone = "blue", hint }) {
  return <article className="stat-card"><span className={"stat-icon " + tone}><Icon size={20} /></span><div><small>{label}</small><strong>{value}</strong>{hint && <small>{hint}</small>}</div></article>;
}
