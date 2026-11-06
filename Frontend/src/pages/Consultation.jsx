import { useCallback, useEffect, useMemo, useState } from "react";
import { Activity, ClipboardList, FileText, RefreshCw, Save, Stethoscope } from "lucide-react";
import { api } from "../services/api";
import "./Consultation.css";
import { useActiveDepartment } from "../context/activeDepartment";
import { useAuth } from "../context/useAuth";

const EMPTY_FORM = {
  presentingComplaint: "",
  historyOfPresentIllness: "",
  medicalHistory: "",
  examinationFindings: "",
  diagnosis: "",
  treatmentPlan: "",
  prescriptions: "",
  investigations: "",
  followUp: "",
};

function Field({ label, name, value, onChange, rows = 3, required = false, readOnly = false }) {
  return (
    <label className="consultation-field">
      {label}{required ? " *" : ""}
      <textarea
        name={name}
        rows={rows}
        required={required}
        readOnly={readOnly}
        value={value || ""}
        onChange={event => onChange(name, event.target.value)}
        placeholder={readOnly ? "" : "Enter " + label.toLowerCase()}
      />
    </label>
  );
}

function observationSummary(observation) {
  const bp = observation.systolicBp || observation.diastolicBp
    ? (observation.systolicBp ?? "—") + "/" + (observation.diastolicBp ?? "—") + " mmHg"
    : "Not recorded";
  return [
    ["Blood pressure", bp],
    ["Heart rate", observation.heartRate == null ? "—" : observation.heartRate + " bpm"],
    ["Respiratory rate", observation.respiratoryRate == null ? "—" : observation.respiratoryRate + " /min"],
    ["Temperature", observation.temperatureC == null ? "—" : observation.temperatureC + " °C"],
    ["SpO₂", observation.spo2Percent == null ? "—" : observation.spo2Percent + "%"],
  ];
}

export function ConsultationPage() {
  const { staff } = useAuth();
  const { departmentId } = useActiveDepartment();
  const [tickets, setTickets] = useState([]);
  const [selectedId, setSelectedId] = useState("");
  const [selectedTicket, setSelectedTicket] = useState(null);
  const [observations, setObservations] = useState([]);
  const [consultation, setConsultation] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const selectedIsCompleted = consultation?.status === "COMPLETED";
  const canEdit = !selectedIsCompleted && selectedTicket?.status === "IN_PROGRESS";

  const loadTickets = useCallback(async () => {
    if (!departmentId) {
      setTickets([]);
      return;
    }
    const rows = await api("/queue/departments/" + departmentId + "/in-progress");
    const activeRows = Array.isArray(rows) ? rows : [];
    setTickets(activeRows);
    if (selectedId && !activeRows.some(row => String(row.id) === String(selectedId))) {
      setSelectedId("");
      setSelectedTicket(null);
      setConsultation(null);
      setObservations([]);
      setForm(EMPTY_FORM);
    }
  }, [departmentId, selectedId]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    if (!departmentId) {
      setTickets([]);
      setLoading(false);
      return () => { active = false; };
    }
    api("/queue/departments/" + departmentId + "/in-progress")
      .then(rows => {
        if (!active) return;
        setTickets(Array.isArray(rows) ? rows : []);
        setError("");
      })
      .catch(err => { if (active) setError(err.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [departmentId]);

  useEffect(() => {
    if (!departmentId) return undefined;
    const timer = window.setInterval(() => {
      loadTickets().catch(err => setError(err.message));
    }, 5000);
    return () => window.clearInterval(timer);
  }, [departmentId, loadTickets]);

  const selectedPatientName = useMemo(
    () => selectedTicket?.patientName || "Selected patient",
    [selectedTicket],
  );

  async function selectTicket(ticket) {
    setSelectedId(String(ticket.id));
    setSelectedTicket(ticket);
    setLoading(true);
    setError("");
    setNotice("");
    setObservations([]);
    setConsultation(null);
    setForm(EMPTY_FORM);
    try {
      const [saved, triage] = await Promise.all([
        api("/consultations/tickets/" + ticket.id),
        api("/triage/patients/" + ticket.patientId + "/observations"),
      ]);
      if (saved && typeof saved === "object") {
        setConsultation(saved);
        setForm({
          presentingComplaint: saved.presentingComplaint || "",
          historyOfPresentIllness: saved.historyOfPresentIllness || "",
          medicalHistory: saved.medicalHistory || "",
          examinationFindings: saved.examinationFindings || "",
          diagnosis: saved.diagnosis || "",
          treatmentPlan: saved.treatmentPlan || "",
          prescriptions: saved.prescriptions || "",
          investigations: saved.investigations || "",
          followUp: saved.followUp || "",
        });
      }
      setObservations(Array.isArray(triage) ? triage : []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  function updateField(name, value) {
    setForm(current => ({ ...current, [name]: value }));
  }

  async function saveDraft(event) {
    event.preventDefault();
    if (!selectedTicket) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const saved = await api("/consultations", {
        method: "POST",
        body: { ...form, queueTicketId: selectedTicket.id },
      });
      setConsultation(saved);
      setNotice("Consultation draft saved.");
      await loadTickets();
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  async function completeConsultation() {
    if (!consultation?.id) {
      setError("Save the consultation draft before completing it.");
      return;
    }
    if (!window.confirm("Complete this consultation? Completed notes become read-only and the queue ticket will be marked completed.")) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const completed = await api("/consultations/" + consultation.id + "/complete", { method: "PATCH" });
      setConsultation(completed);
      setNotice("Consultation completed and the patient has been removed from the active queue.");
      setSelectedTicket(current => current ? { ...current, status: "COMPLETED" } : current);
      setTickets(current => current.filter(ticket => ticket.id !== selectedTicket.id));
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <div className="page-title-row">
        <div>
          <span className="eyebrow">DOCTOR WORKSPACE</span>
          <h1>Consultations</h1>
          <p>Review nurse triage observations, document the clinical encounter and complete the visit.</p>
        </div>
        <button className="button button-outline" onClick={() => loadTickets().catch(err => setError(err.message))} disabled={loading}>
          <RefreshCw size={15} /> Refresh queue
        </button>
      </div>

      {error && <div className="form-error consultation-message">{error}</div>}
      {notice && <div className="notice consultation-message">{notice}</div>}
      {!departmentId && <div className="notice notice-warning">Your account has no active department selected. Ask an administrator to assign your department.</div>}

      <div className="consultation-workspace">
        <section className="panel consultation-queue-panel">
          <div className="panel-heading">
            <div><h2>Now serving</h2><p>{tickets.length} patient(s) called into this department</p></div>
            <Stethoscope size={19} />
          </div>
          {loading && tickets.length === 0
            ? <div className="empty-panel">Loading active queue…</div>
            : tickets.length === 0
              ? <div className="empty-panel">No patients are currently being seen. The nurse should call the next patient from Queue management.</div>
              : <div className="consultation-ticket-list">
                {tickets.map(ticket => (
                  <button
                    type="button"
                    key={ticket.id}
                    className={"consultation-ticket" + (String(ticket.id) === selectedId ? " selected" : "")}
                    onClick={() => selectTicket(ticket)}
                  >
                    <span className="consultation-ticket-number">{ticket.ticketNumber}</span>
                    <b>{ticket.patientName || "Patient #" + ticket.patientId}</b>
                    <small>Patient #{ticket.patientId} · {ticket.priorityLevel}</small>
                  </button>
                ))}
              </div>}
        </section>

        <section className="panel consultation-editor-panel">
          {!selectedTicket ? (
            <div className="consultation-empty">
              <FileText size={28} />
              <h2>Select a patient</h2>
              <p>Choose a patient from the Now serving list to open their triage record and consultation form.</p>
            </div>
          ) : (
            <>
              <div className="panel-heading consultation-patient-heading">
                <div>
                  <span className="eyebrow">PATIENT #{selectedTicket.patientId} · {selectedTicket.ticketNumber}</span>
                  <h2>{selectedPatientName}</h2>
                  <p>Doctor: {staff?.name || "Current clinician"} · Status: {consultation?.status || "Not started"}</p>
                </div>
                {selectedIsCompleted && <span className="pill status-completed">Completed</span>}
              </div>

              <section className="consultation-triage">
                <div className="consultation-section-title"><Activity size={16} /><h3>Nurse triage information</h3></div>
                {selectedTicket.triageNotes && <p className="consultation-triage-note">{selectedTicket.triageNotes}</p>}
                {observations.length === 0
                  ? <p className="consultation-muted">No triage observations have been recorded for this patient yet.</p>
                  : observations.map(item => (
                    <article className="consultation-observation" key={item.id}>
                      <div className="consultation-observation-heading">
                        <b>Observation #{item.id}</b>
                        <small>{item.measuredAt ? new Date(item.measuredAt).toLocaleString() : "Time not recorded"}</small>
                      </div>
                      <div className="consultation-vitals">
                        {observationSummary(item).map(([label, value]) => <div key={label}><small>{label}</small><b>{value}</b></div>)}
                      </div>
                      {item.symptoms && <p><b>Symptoms:</b> {item.symptoms}</p>}
                      {item.notes && <p><b>Nurse notes:</b> {item.notes}</p>}
                    </article>
                  ))}
              </section>

              <form className="consultation-form" onSubmit={saveDraft}>
                <div className="consultation-section-title"><ClipboardList size={16} /><h3>Clinical consultation notes</h3></div>
                <div className="consultation-form-grid">
                  <Field label="Presenting complaint" name="presentingComplaint" value={form.presentingComplaint} onChange={updateField} readOnly={!canEdit} />
                  <Field label="History of present illness" name="historyOfPresentIllness" value={form.historyOfPresentIllness} onChange={updateField} readOnly={!canEdit} />
                  <Field label="Relevant medical history" name="medicalHistory" value={form.medicalHistory} onChange={updateField} readOnly={!canEdit} />
                  <Field label="Examination findings" name="examinationFindings" value={form.examinationFindings} onChange={updateField} readOnly={!canEdit} />
                  <Field label="Diagnosis" name="diagnosis" value={form.diagnosis} onChange={updateField} readOnly={!canEdit} />
                  <Field label="Treatment plan" name="treatmentPlan" value={form.treatmentPlan} onChange={updateField} readOnly={!canEdit} />
                  <Field label="Prescriptions" name="prescriptions" value={form.prescriptions} onChange={updateField} readOnly={!canEdit} />
                  <Field label="Investigations requested" name="investigations" value={form.investigations} onChange={updateField} readOnly={!canEdit} />
                  <Field label="Follow-up instructions" name="followUp" value={form.followUp} onChange={updateField} readOnly={!canEdit} />
                </div>
                {canEdit && <div className="consultation-actions">
                  <button className="button button-outline" type="submit" disabled={saving}><Save size={15} /> {saving ? "Saving…" : "Save draft"}</button>
                  <button className="button button-primary" type="button" onClick={completeConsultation} disabled={saving || !consultation?.id}>Complete consultation</button>
                </div>}
                {selectedIsCompleted && <div className="notice consultation-readonly-notice">This consultation has been finalized and is read-only.</div>}
              </form>
            </>
          )}
        </section>
      </div>
    </>
  );
}
