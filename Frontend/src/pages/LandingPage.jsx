import { Activity, ArrowRight, ClipboardList, Droplet, ShieldCheck, Stethoscope, Users } from "lucide-react";
import { Link } from "react-router-dom";

const features = [
  [Users, "Patient management", "Capture patient details and keep registration connected to the facility workflow."],
  [ClipboardList, "Queue coordination", "See waiting patients, call the next patient and track ticket status."],
  [Stethoscope, "Clinical triage", "Record observations and surface explainable clinical risk recommendations for review."],
  [Droplet, "Blood bank readiness", "A dedicated workspace prepared for blood-stock and movement APIs."],
];

export default function LandingPage() {
  return (
    <main className="public-page">
      <header className="public-nav">
        <Link className="public-brand" to="/"><span className="brand-symbol"><Activity size={22} /></span><span><b>MBQ</b><small>Hospital Operations</small></span></Link>
        <nav><a href="#features">Platform</a><a href="#workflow">How it works</a><a href="#about">About</a></nav>
        <Link className="button button-primary" to="/login">Staff sign in <ArrowRight size={16} /></Link>
      </header>

      <section className="hero-section">
        <div className="hero-copy">
          <span className="eyebrow">CONNECTED HOSPITAL OPERATIONS</span>
          <h1>Better coordination.<br /><em>More focused care.</em></h1>
          <p>One workspace for patient registration, queue coordination and clinical triage—designed to help hospital teams work with clearer information.</p>
          <div className="hero-actions"><Link className="button button-primary" to="/login">Access staff portal <ArrowRight size={17} /></Link><a className="text-link" href="#features">Explore the platform</a></div>
          <div className="hero-trust"><ShieldCheck size={17} /><span>Secure staff access with role-based permissions</span></div>
        </div>
        <div className="hero-visual">
          <div className="visual-glow" />
          <div className="overview-card">
            <div className="overview-head"><span className="live-dot" /> OPERATIONS WORKSPACE <span className="overview-date">MBQ</span></div>
            <div className="overview-title">A clearer view of care delivery</div>
            <div className="overview-grid">
              <div><span className="overview-icon"><Users size={18} /></span><b>Patients</b><small>Registration</small></div>
              <div><span className="overview-icon teal"><ClipboardList size={18} /></span><b>Queue</b><small>Patient flow</small></div>
              <div><span className="overview-icon violet"><Stethoscope size={18} /></span><b>Triage</b><small>Clinical review</small></div>
              <div><span className="overview-icon amber"><Droplet size={18} /></span><b>Blood bank</b><small>Resource readiness</small></div>
            </div>
            <div className="overview-foot"><span>Designed around connected workflows</span><Activity size={18} /></div>
          </div>
          <div className="floating-note"><span className="note-check"><ShieldCheck size={16} /></span><span><b>One coordinated workspace</b><small>For hospital teams</small></span></div>
        </div>
      </section>

      <section className="feature-section" id="features">
        <div className="section-intro"><span className="eyebrow">THE MBQ PLATFORM</span><h2>Essential workflows,<br />working together.</h2><p>Bring key operational tasks into one consistent staff experience.</p></div>
        <div className="feature-grid">{features.map(([Icon, title, description]) => <article className="feature-card" key={title}><span className="feature-icon"><Icon size={21} /></span><h3>{title}</h3><p>{description}</p></article>)}</div>
      </section>

      <section className="workflow-section" id="workflow"><span className="eyebrow">A CONNECTED WORKFLOW</span><h2>From reception to clinical review.</h2><div className="workflow-steps"><div><b>01</b><h3>Register</h3><p>Capture the patient record at reception.</p></div><div><b>02</b><h3>Coordinate</h3><p>Create and manage the department queue.</p></div><div><b>03</b><h3>Assess</h3><p>Record observations and review risk recommendations.</p></div></div></section>

      <section className="public-cta" id="about"><div><span className="eyebrow">MBQ HOSPITAL OPERATIONS</span><h2>Give your team a more connected way to work.</h2><p>Sign in with your hospital-issued staff account to access the workspace.</p></div><Link className="button button-light" to="/login">Staff sign in <ArrowRight size={17} /></Link></section>
      <footer className="public-footer"><Link className="public-brand" to="/"><span className="brand-symbol"><Activity size={20} /></span><span><b>MBQ</b><small>Hospital Operations</small></span></Link><span>© {new Date().getFullYear()} MBQ. Hospital operations platform.</span></footer>
    </main>
  );
}
