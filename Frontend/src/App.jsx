import { useState } from "react";
import {
  Activity,
  ArrowDownRight,
  ArrowUpRight,
  Bell,
  ChevronDown,
  ClipboardList,
  Clock3,
  Droplet,
  LayoutDashboard,
  Menu,
  Plus,
  Search,
  Settings,
  ShieldAlert,
  Stethoscope,
  Users,
  X,
} from "lucide-react";
import "./App.css";

const initialPatients = [
  {
    id: "MBQ-1001",
    name: "James Phiri",
    department: "General Medicine",
    arrival: "08:42 AM",
    priority: "Emergency",
    status: "In Progress",
  },
  {
    id: "MBQ-1002",
    name: "Mary Banda",
    department: "Paediatrics",
    arrival: "08:49 AM",
    priority: "Urgent",
    status: "Waiting",
  },
  {
    id: "MBQ-1003",
    name: "Peter Zulu",
    department: "General Medicine",
    arrival: "09:03 AM",
    priority: "Normal",
    status: "Waiting",
  },
  {
    id: "MBQ-1004",
    name: "Grace Mwansa",
    department: "Outpatient",
    arrival: "09:11 AM",
    priority: "Urgent",
    status: "Waiting",
  },
  {
    id: "MBQ-1005",
    name: "Daniel Tembo",
    department: "General Medicine",
    arrival: "09:18 AM",
    priority: "Normal",
    status: "Completed",
  },
];

const navigation = [
  { label: "Dashboard", icon: LayoutDashboard },
  { label: "Patients", icon: Users },
  { label: "Queue Management", icon: ClipboardList },
  { label: "Triage", icon: Stethoscope },
  { label: "Blood Bank", icon: Droplet },
  { label: "Reports", icon: Activity },
];

function MetricCard({ title, value, change, icon: Icon, tone, trend }) {
  return (
    <article className="metric-card">
      <div className="metric-top">
        <span className="metric-title">{title}</span>
        <span className={`metric-icon ${tone}`}>
          <Icon size={20} />
        </span>
      </div>

      <div className="metric-value">{value}</div>

      <div className="metric-footer">
        <span className={`metric-trend ${trend}`}>
          {trend === "up" ? (
            <ArrowUpRight size={15} />
          ) : (
            <ArrowDownRight size={15} />
          )}
          {change}
        </span>
        <span className="metric-caption">vs. previous period</span>
      </div>
    </article>
  );
}

function App() {
  const [activePage, setActivePage] = useState("Dashboard");
  const [patients, setPatients] = useState(initialPatients);
  const [search, setSearch] = useState("");
  const [showRegistration, setShowRegistration] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [newPatient, setNewPatient] = useState({
    name: "",
    department: "General Medicine",
    priority: "Normal",
  });

  const waiting = patients.filter(
    (patient) => patient.status === "Waiting",
  ).length;

  const inProgress = patients.filter(
    (patient) => patient.status === "In Progress",
  ).length;

  const completed = patients.filter(
    (patient) => patient.status === "Completed",
  ).length;

  const urgent = patients.filter(
    (patient) =>
      patient.priority === "Urgent" ||
      patient.priority === "Emergency",
  ).length;

  const filteredPatients = patients.filter((patient) =>
    [
      patient.name,
      patient.id,
      patient.department,
      patient.priority,
      patient.status,
    ]
      .join(" ")
      .toLowerCase()
      .includes(search.toLowerCase()),
  );

  function registerPatient(event) {
    event.preventDefault();

    const patient = {
      id: `MBQ-${1000 + patients.length + 1}`,
      name: newPatient.name.trim(),
      department: newPatient.department,
      arrival: new Date().toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
      }),
      priority: newPatient.priority,
      status: "Waiting",
    };

    if (!patient.name) return;

    setPatients((current) => [patient, ...current]);
    setNewPatient({
      name: "",
      department: "General Medicine",
      priority: "Normal",
    });
    setShowRegistration(false);
    setActivePage("Queue Management");
  }

  function updateStatus(patientId, status) {
    setPatients((current) =>
      current.map((patient) =>
        patient.id === patientId ? { ...patient, status } : patient,
      ),
    );
  }

  function renderPageContent() {
    if (
      activePage === "Dashboard" ||
      activePage === "Patients" ||
      activePage === "Queue Management"
    ) {
      return (
        <>
          <section className="metrics-grid">
            <MetricCard
              title="Patients Waiting"
              value={waiting.toString().padStart(2, "0")}
              change="Live demo"
              icon={Users}
              tone="blue"
              trend="up"
            />
            <MetricCard
              title="In Progress"
              value={inProgress.toString().padStart(2, "0")}
              change="Live demo"
              icon={Activity}
              tone="purple"
              trend="up"
            />
            <MetricCard
              title="Urgent Cases"
              value={urgent.toString().padStart(2, "0")}
              change="Needs attention"
              icon={ShieldAlert}
              tone="orange"
              trend="up"
            />
            <MetricCard
              title="Completed Visits"
              value={completed.toString().padStart(2, "0")}
              change="Live demo"
              icon={ClipboardList}
              tone="green"
              trend="up"
            />
          </section>

          <section className="content-card queue-card">
            <div className="card-heading">
              <div>
                <h2>
                  {activePage === "Patients"
                    ? "Patient Directory"
                    : activePage === "Queue Management"
                      ? "Queue Management"
                      : "Current Patient Queue"}
                </h2>
                <p>
                  Demonstration records — not connected to the hospital
                  database yet.
                </p>
              </div>

              <button
                className="primary-button"
                onClick={() => setShowRegistration(true)}
              >
                <Plus size={17} />
                Register Patient
              </button>
            </div>

            <div className="table-toolbar">
              <div className="search-box">
                <Search size={17} />
                <input
                  aria-label="Search patients"
                  placeholder="Search patients, departments..."
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                />
              </div>

              <span className="record-count">
                {filteredPatients.length} records
              </span>
            </div>

            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Patient</th>
                    <th>Department</th>
                    <th>Arrival</th>
                    <th>Priority</th>
                    <th>Status</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredPatients.map((patient) => (
                    <tr key={patient.id}>
                      <td>
                        <div className="patient-cell">
                          <span className="patient-avatar">
                            {patient.name
                              .split(" ")
                              .map((part) => part[0])
                              .slice(0, 2)
                              .join("")}
                          </span>
                          <div>
                            <strong>{patient.name}</strong>
                            <small>{patient.id}</small>
                          </div>
                        </div>
                      </td>
                      <td>{patient.department}</td>
                      <td>{patient.arrival}</td>
                      <td>
                        <span
                          className={`badge priority-${patient.priority.toLowerCase()}`}
                        >
                          {patient.priority}
                        </span>
                      </td>
                      <td>
                        <span
                          className={`badge status-${patient.status
                            .toLowerCase()
                            .replace(" ", "-")}`}
                        >
                          {patient.status}
                        </span>
                      </td>
                      <td>
                        <select
                          className="status-select"
                          aria-label={`Change status for ${patient.name}`}
                          value={patient.status}
                          onChange={(event) =>
                            updateStatus(patient.id, event.target.value)
                          }
                        >
                          <option>Waiting</option>
                          <option>In Progress</option>
                          <option>Completed</option>
                          <option>Cancelled</option>
                        </select>
                      </td>
                    </tr>
                  ))}

                  {filteredPatients.length === 0 && (
                    <tr>
                      <td colSpan="6" className="empty-state">
                        No patients match your search.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </section>

          <section className="bottom-grid">
            <article className="content-card info-card">
              <div className="small-card-heading">
                <div className="section-icon blue">
                  <Clock3 size={19} />
                </div>
                <div>
                  <h3>Queue Overview</h3>
                  <p>Current demonstration workload</p>
                </div>
              </div>

              <div className="overview-row">
                <span>Waiting for service</span>
                <strong>{waiting}</strong>
              </div>
              <div className="progress-track">
                <div
                  className="progress-fill blue-fill"
                  style={{
                    width: `${Math.max(
                      5,
                      (waiting / Math.max(patients.length, 1)) * 100,
                    )}%`,
                  }}
                />
              </div>

              <div className="overview-row">
                <span>Currently in service</span>
                <strong>{inProgress}</strong>
              </div>
              <div className="progress-track">
                <div
                  className="progress-fill purple-fill"
                  style={{
                    width: `${Math.max(
                      5,
                      (inProgress / Math.max(patients.length, 1)) * 100,
                    )}%`,
                  }}
                />
              </div>
            </article>

            <article className="content-card info-card alert-card">
              <div className="small-card-heading">
                <div className="section-icon orange">
                  <ShieldAlert size={19} />
                </div>
                <div>
                  <h3>Priority Attention</h3>
                  <p>Cases marked urgent or emergency</p>
                </div>
              </div>

              {patients
                .filter(
                  (patient) =>
                    patient.priority === "Emergency" ||
                    patient.priority === "Urgent",
                )
                .slice(0, 3)
                .map((patient) => (
                  <div className="alert-row" key={patient.id}>
                    <div>
                      <strong>{patient.name}</strong>
                      <small>{patient.department}</small>
                    </div>
                    <span
                      className={`badge priority-${patient.priority.toLowerCase()}`}
                    >
                      {patient.priority}
                    </span>
                  </div>
                ))}

              {urgent === 0 && (
                <p className="empty-alert">No priority cases in this demo.</p>
              )}
            </article>
          </section>
        </>
      );
    }

    const descriptions = {
      Triage:
        "Record patient observations and review clinical risk assessments. Clinical assessments must be made and reviewed by qualified staff.",
      "Blood Bank":
        "Review blood-stock levels and record stock movements. Stock quantities must come from the verified backend.",
      Reports:
        "Review queue activity, service times and operational summaries.",
    };

    return (
      <section className="content-card placeholder-page">
        <div className="placeholder-icon">
          {activePage === "Triage" ? (
            <Stethoscope size={30} />
          ) : activePage === "Blood Bank" ? (
            <Droplet size={30} />
          ) : (
            <Activity size={30} />
          )}
        </div>
        <h2>{activePage}</h2>
        <p>{descriptions[activePage]}</p>
        <span className="coming-soon">Module planned for next integration stage</span>
      </section>
    );
  }

  return (
    <div className="app-shell">
      {mobileMenuOpen && (
        <button
          className="mobile-overlay"
          aria-label="Close navigation"
          onClick={() => setMobileMenuOpen(false)}
        />
      )}

      <aside className={`sidebar ${mobileMenuOpen ? "sidebar-open" : ""}`}>
        <div className="brand">
          <div className="brand-mark">
            <Activity size={23} />
          </div>

          <div>
            <strong>MBQ</strong>
            <span>Hospital Operations</span>
          </div>

          <button
            className="close-menu"
            aria-label="Close menu"
            onClick={() => setMobileMenuOpen(false)}
          >
            <X size={19} />
          </button>
        </div>

        <div className="facility-label">WORKSPACE</div>

        <nav className="navigation">
          {navigation.map(({ label, icon: Icon }) => (
            <button
              key={label}
              className={`nav-item ${activePage === label ? "active" : ""}`}
              onClick={() => {
                setActivePage(label);
                setMobileMenuOpen(false);
              }}
            >
              <Icon size={19} />
              <span>{label}</span>
              {label === "Triage" && <span className="nav-dot" />}
            </button>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <div className="help-card">
            <div className="help-icon">
              <ShieldAlert size={20} />
            </div>

            <strong>Need assistance?</strong>
            <p>
              Contact your hospital system administrator for support.
            </p>
          </div>

          <button className="nav-item settings-button">
            <Settings size={19} />
            <span>Settings</span>
          </button>

          <div className="profile">
            <div className="profile-avatar">AD</div>

            <div className="profile-info">
              <strong>Hospital Admin</strong>
              <span>Demo workspace</span>
            </div>

            <ChevronDown size={17} />
          </div>
        </div>
      </aside>
  
      <main className="main-area">
        <header className="topbar">
          <div className="topbar-left">
            <button
              className="mobile-menu"
              aria-label="Open navigation"
              onClick={() => setMobileMenuOpen(true)}
            >
              <Menu size={22} />
            </button>

            <div className="breadcrumbs">
              <span>Workspace</span>
              <span className="breadcrumb-divider">/</span>
              <strong>{activePage}</strong>
            </div>
          </div>

          <div className="topbar-right">
            <span className="environment-badge">
              <span />
              DEMO ENVIRONMENT
            </span>

            <button
              className="notification-button"
              aria-label="Notifications"
            >
              <Bell size={20} />
              <span />
            </button>

            <div className="topbar-date">
              <span>Today</span>
              <strong>
                {new Date().toLocaleDateString("en-GB", {
                  day: "2-digit",
                  month: "short",
                  year: "numeric",
                })}
              </strong>
            </div>
          </div>
        </header>

        <div className="page-content">
          <section className="page-heading">
            <div>
              <span className="eyebrow">HOSPITAL MANAGEMENT SYSTEM</span>

              <h1>
                {activePage === "Dashboard"
                  ? "Hospital Operations Dashboard"
                  : activePage}
              </h1>

              <p>
                {activePage === "Dashboard"
                  ? "Here's an overview of today's hospital operations."
                  : `Manage and monitor ${activePage.toLowerCase()} activity.`}
              </p>
            </div>

            <div className="heading-actions">
              <button
                className="secondary-button"
                onClick={() => {
                  setSearch("");
                  setActivePage("Dashboard");
                }}
              >
                Reset View
              </button>

              <button
                className="primary-button"
                onClick={() => setShowRegistration(true)}
              >
                <Plus size={17} />
                New Patient
              </button>
            </div>
          </section>

          {renderPageContent()}

          <footer className="footer">
            <span>MBQ Hospital Operations</span>
            <span>Frontend prototype · Demonstration data only</span>
          </footer>
        </div>
      </main>

      {showRegistration && (
        <div
          className="modal-backdrop"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) {
              setShowRegistration(false);
            }
          }}
        >
          <section
            className="registration-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="registration-title"
          >
            <div className="modal-heading">
              <div>
                <span className="eyebrow">PATIENT INTAKE</span>
                <h2 id="registration-title">Register Patient</h2>
                <p>
                  Add a demonstration patient to the local queue.
                </p>
              </div>

              <button
                type="button"
                className="icon-button"
                aria-label="Close registration"
                onClick={() => setShowRegistration(false)}
              >
                <X size={20} />
              </button>
            </div>

            <form onSubmit={registerPatient}>
              <label>
                Patient full name

                <input
                  autoFocus
                  required
                  maxLength={120}
                  value={newPatient.name}
                  onChange={(event) =>
                    setNewPatient({
                      ...newPatient,
                      name: event.target.value,
                    })
                  }
                  placeholder="Enter patient's name"
                />
              </label>

              <label>
                Department

                <select
                  value={newPatient.department}
                  onChange={(event) =>
                    setNewPatient({
                      ...newPatient,
                      department: event.target.value,
                    })
                  }
                >
                  <option>General Medicine</option>
                  <option>Outpatient</option>
                  <option>Paediatrics</option>
                  <option>Maternity</option>
                  <option>Surgery</option>
                  <option>Emergency</option>
                </select>
              </label>

              <label>
                Queue priority

                <select
                  value={newPatient.priority}
                  onChange={(event) =>
                    setNewPatient({
                      ...newPatient,
                      priority: event.target.value,
                    })
                  }
                >
                  <option>Normal</option>
                  <option>Urgent</option>
                  <option>Emergency</option>
                </select>
              </label>

              <div className="form-notice">
                This is demonstration functionality. The patient will only
                be stored in the current browser session and will not be
                saved to the hospital database.
              </div>

              <div className="modal-actions">
                <button
                  type="button"
                  className="secondary-button"
                  onClick={() => setShowRegistration(false)}
                >
                  Cancel
                </button>

                <button type="submit" className="primary-button">
                  <Plus size={17} />
                  Add Patient
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}

export default App;