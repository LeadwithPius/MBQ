import {
  Activity,
  ArrowRight,
  ClipboardList,
  Droplet,
  ShieldCheck,
  Stethoscope,
  Users,
} from "lucide-react";
import { Link } from "react-router-dom";

const CURRENT_YEAR = new Date().getFullYear();

const features = [
  {
    icon: Users,
    title: "Patient management",
    description:
      "Register patients and maintain organised patient records.",
  },
  {
    icon: ClipboardList,
    title: "Queue coordination",
    description:
      "Coordinate waiting patients and improve department workflow.",
  },
  {
    icon: Stethoscope,
    title: "Clinical triage",
    description:
      "Record observations and support clinical review.",
  },
  {
    icon: Droplet,
    title: "Blood bank",
    description:
      "Support blood-stock visibility and resource management.",
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

        <nav>
          <a href="#features">Platform</a>
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
            A connected hospital operations platform bringing patient
            registration, queue coordination and clinical triage into
            one organised workspace.
          </p>

          <div className="mbq-hero-actions">
            <Link className="mbq-primary-button" to="/login">
              Access staff portal <ArrowRight size={18} />
            </Link>

            <a className="mbq-secondary-link" href="#features">
              Explore MBQ
            </a>
          </div>

          <div className="mbq-hero-security">
            <ShieldCheck size={19} />
            <span>Secure, role-based staff access</span>
          </div>
        </div>

        <div className="mbq-hero-art">
          <div className="mbq-hero-ring" />
          <div className="mbq-hero-logo-card">
            <img src="/mbq-logo.png" alt="MBQ Healthcare eagle emblem" />
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

      <section className="mbq-brand-strip">
        <div>
          <Users size={25} />
          <span>QUEUES</span>
        </div>
        <i />
        <div>
          <Activity size={25} />
          <span>TRIAGE</span>
        </div>
        <i />
        <div>
          <Droplet size={25} />
          <span>BLOOD BANK</span>
        </div>
      </section>

      <section className="feature-section mbq-features" id="features">
        <div className="section-intro">
          <span className="mbq-section-label">
            <span />
            THE MBQ PLATFORM
          </span>

          <h2>
            Essential workflows.
            <br />
            One connected platform.
          </h2>

          <p>
            Give hospital teams a consistent place to coordinate
            essential operational tasks.
          </p>
        </div>

        <div className="feature-grid">
          {features.map(({ icon: Icon, title, description }) => (
            <article className="feature-card mbq-feature-card" key={title}>
              <span className="mbq-feature-icon">
                <Icon size={23} />
              </span>

              <h3>{title}</h3>
              <p>{description}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="workflow-section mbq-workflow" id="workflow">
        <span className="mbq-section-label">
          <span />
          A CONNECTED WORKFLOW
        </span>

        <h2>From reception to clinical review.</h2>

        <div className="workflow-steps">
          <div>
            <b>01</b>
            <h3>Register</h3>
            <p>Capture patient details and establish the patient record.</p>
          </div>

          <div>
            <b>02</b>
            <h3>Coordinate</h3>
            <p>Manage department queues and coordinate patient flow.</p>
          </div>

          <div>
            <b>03</b>
            <h3>Assess</h3>
            <p>Record clinical observations and review assessments.</p>
          </div>
        </div>
      </section>

      <section className="mbq-public-cta" id="about">
        <div>
          <span>MBQ HOSPITAL OPERATIONS</span>
          <h2>Manage. Care. Serve.</h2>
          <p>
            Access your hospital workspace using your authorised
            staff account.
          </p>
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

        <span>
          © {CURRENT_YEAR} MBQ. Manage · Care · Serve.
        </span>
      </footer>
    </main>
  );
}