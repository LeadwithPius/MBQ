import {
  Activity,
  AlertTriangle,
  ArrowRight,
  ClipboardList,
  Clock3,
  Droplet,
  FileBarChart,
  FileText,
  ShieldCheck,
  Stethoscope,
  Users,
} from "lucide-react";
import { Link } from "react-router-dom";

const CURRENT_YEAR = new Date().getFullYear();

const problems = [
  {
    icon: Clock3,
    title: "Queues without visibility",
    description: "Patients wait without a clear view of their position or what comes next.",
  },
  {
    icon: FileText,
    title: "Paper-heavy intake",
    description: "Staff time is lost rewriting details and searching through paper records.",
  },
  {
    icon: AlertTriangle,
    title: "Blood shortages found late",
    description: "Stock by blood group can be difficult to confirm quickly when every minute matters.",
  },
];

const modules = [
  {
    icon: ClipboardList,
    title: "Queues",
    benefit: "See who is waiting, in order.",
    description:
      "Digital patient registration creates timestamped queue tickets assigned to a department. Staff can monitor the live queue and call the next patient.",
  },
  {
    icon: Stethoscope,
    title: "Triage",
    benefit: "Get urgent cases seen first.",
    description:
      "Nurses can record observations, flag urgent or emergency cases, and capture triage notes to support prioritisation.",
  },
  {
    icon: Droplet,
    title: "Blood Bank",
    benefit: "Know your stock before you need it.",
    description:
      "Track units by blood group, set low-stock thresholds, and keep a record of donor alerts when stock is insufficient for an urgent request.",
  },
];

const workflow = [
  {
    number: "01",
    title: "Register",
    description: "The receptionist records the patient and a timestamped queue ticket is issued.",
  },
  {
    number: "02",
    title: "Queue",
    description: "The patient's position appears on the department's live dashboard.",
  },
  {
    number: "03",
    title: "Triage",
    description: "The nurse records observations and flags emergencies for immediate attention.",
  },
  {
    number: "04",
    title: "Coordinate blood",
    description: "If blood is needed, the blood bank checks availability. If stock is insufficient, a donor alert can be logged.",
  },
  {
    number: "05",
    title: "Consult and close",
    description: "The doctor records the outcome before the patient is discharged or referred.",
  },
];

export default function LandingPage() {
  return (
    <main className="public-page mbq-public-page">
      <header className="public-nav mbq-public-nav">
        <Link className="mbq-public-brand" to="/">
          <img src="/mbq-logo.png" alt="MBQ Healthcare" />
          <span>
            <b>MBQ</b>
            <small>Hospital Operations</small>
          </span>
        </Link>

        <nav aria-label="Main navigation">
          <a href="#platform">Platform</a>
          <a href="#workflow">How it works</a>
          <a href="#about">About</a>
        </nav>

        <Link className="button mbq-nav-button" to="/login">
          Staff sign in <ArrowRight size={16} />
        </Link>
      </header>

      <section className="mbq-hero">
        <div className="mbq-hero-copy">
          <span className="mbq-section-label">
            <span />
            CONNECTED HOSPITAL OPERATIONS
          </span>

          <h1>
            Manage better.
            <br />
            <span>Care better.</span>
            <br />
            Serve better.
          </h1>

          <p>
            Bring patient intake, department queues, clinical triage and blood
            stock coordination into one organised hospital workspace.
          </p>

          <div className="mbq-hero-actions">
            <Link className="mbq-primary-button" to="/login">
              Access staff portal <ArrowRight size={18} />
            </Link>
            <a className="mbq-secondary-link" href="#platform">
              Explore the platform
            </a>
          </div>

          <div className="mbq-hero-security">
            <ShieldCheck size={19} />
            <span>Secure, role-based staff access</span>
          </div>
        </div>

        <div className="mbq-hero-art" aria-label="MBQ Healthcare platform">
          <div className="mbq-hero-ring" />
          <div className="mbq-hero-logo-card">
            <img src="/mbq-logo.png" alt="MBQ Healthcare emblem" />
          </div>
          <div className="mbq-hero-caption">
            <span className="mbq-caption-pulse" />
            <div>
              <strong>MANAGE · CARE · SERVE</strong>
              <small>One connected hospital workspace</small>
            </div>
          </div>
        </div>
      </section>

      <section className="mbq-problem-section" aria-labelledby="problem-title">
        <div className="mbq-content-width">
          <div className="mbq-problem-intro">
            <span className="mbq-section-label"><span />THE CHALLENGE</span>
            <h2 id="problem-title">Built for the front desk, not just the filing cabinet.</h2>
            <p>
              Patient intake in many public hospitals still relies on paper
              registers, while blood stock may only be counted periodically.
              That can mean long, unpredictable queues and slower responses
              when a patient urgently needs blood. MBQ brings these essential
              workflows into one coordinated workspace.
            </p>
          </div>
          <div className="mbq-problem-grid">
            {problems.map(({ icon: Icon, title, description }) => (
              <article className="mbq-problem-card" key={title}>
                <span className="mbq-problem-icon"><Icon size={21} /></span>
                <h3>{title}</h3>
                <p>{description}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="feature-section mbq-features" id="platform">
        <div className="section-intro">
          <span className="mbq-section-label"><span />THE MBQ PLATFORM</span>
          <h2>Three essential workflows.<br />One connected platform.</h2>
          <p>
            Give hospital teams a shared view of patient flow, clinical
            priorities and blood stock coordination.
          </p>
        </div>

        <div className="mbq-module-grid">
          {modules.map(({ icon: Icon, title, benefit, description }, index) => (
            <article className="feature-card mbq-feature-card mbq-module-card" key={title}>
              <span className="mbq-feature-icon"><Icon size={23} /></span>
              <span className="mbq-module-number">0{index + 1}</span>
              <h3>{title}</h3>
              <strong>{benefit}</strong>
              <p>{description}</p>
            </article>
          ))}
        </div>

        <article className="mbq-reports-card">
          <span className="mbq-reports-icon"><FileBarChart size={21} /></span>
          <div>
            <h3>Reports for administrators</h3>
            <p>Review daily patient volumes and blood-stock movements to support operational oversight.</p>
          </div>
        </article>
      </section>

      <section className="workflow-section mbq-workflow" id="workflow">
        <div className="mbq-workflow-intro">
          <span className="mbq-section-label"><span />HOW IT WORKS</span>
          <h2>From reception to clinical review.</h2>
          <p>A coordinated journey that helps each team understand what needs to happen next.</p>
        </div>

        <div className="mbq-workflow-steps">
          {workflow.map(({ number, title, description }) => (
            <article className="mbq-workflow-step" key={number}>
              <span className="mbq-step-number">{number}</span>
              <div className="mbq-step-content">
                <h3>{title}</h3>
                <p>{description}</p>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="mbq-public-cta" id="about">
        <div>
          <span>MBQ HOSPITAL OPERATIONS</span>
          <h2>Manage. Care. Serve.</h2>
          <p>Access your hospital workspace using your authorised staff account.</p>
        </div>
        <Link to="/login">
          Staff sign in <ArrowRight size={18} />
        </Link>
      </section>

      <footer className="mbq-public-footer">
        <Link className="mbq-public-brand" to="/">
          <img src="/mbq-logo.png" alt="MBQ Healthcare" />
          <span>
            <b>MBQ</b>
            <small>Hospital Operations</small>
          </span>
        </Link>
        <span>© {CURRENT_YEAR} MBQ. Manage · Care · Serve.</span>
      </footer>
    </main>
  );
}
