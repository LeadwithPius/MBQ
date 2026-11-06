import { BrowserRouter, Navigate, Outlet, Route, Routes } from "react-router-dom";
// Keep the ".jsx": on Windows "AuthContext" and "authContext" are the same file name,
// so without it the import resolves to authContext.js and the app shows a white screen.
import { AuthProvider } from "./context/AuthContext.jsx";
import { useAuth } from "./context/useAuth";
import LandingPage from "./pages/LandingPage";
import LoginPage from "./pages/LoginPage";
import { BloodBankPage } from "./pages/BloodBank";
import { ReportsPage } from "./pages/Reports";
import { ConsultationPage } from "./pages/Consultation";
import { LaboratoryPage } from "./pages/Laboratory";
import { AccountPage } from "./pages/Account";
import {
  WorkspaceLayout, DashboardPage, PatientsPage, QueuePage, TriagePage,
} from "./pages/Workspace";
import "./portal.css";
import { ROLE_ACCESS } from "./roleAccess";

function RequireAuth() {
  const { session } = useAuth();
  return session ? <Outlet /> : <Navigate to="/login" replace />;
}

function RequireRoles({ roles, children }) {
  const { staff } = useAuth();
  if (!staff) return <Navigate to="/login" replace />;
  if (!roles.includes(staff.role)) return <Navigate to="/app" replace />;
  return children;
}

// Lab staff work in the blood bank, not a department queue: start them there.
function HomePage() {
  const { staff } = useAuth();
  return staff?.role === "LAB_STAFF" ? <Navigate to="/app/laboratory" replace /> : <DashboardPage />;
}

function AppRoutes() {
  const { session } = useAuth();
  return (
    <Routes>
      <Route path="/" element={session ? <Navigate to="/app" replace /> : <LandingPage />} />
      <Route path="/login" element={session ? <Navigate to="/app" replace /> : <LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route path="/app" element={<WorkspaceLayout />}>
          <Route index element={<HomePage />} />
          <Route path="patients" element={<RequireRoles roles={ROLE_ACCESS.PATIENTS}><PatientsPage /></RequireRoles>} />
          <Route path="queue" element={<RequireRoles roles={ROLE_ACCESS.QUEUE}><QueuePage /></RequireRoles>} />
          <Route path="triage" element={<RequireRoles roles={ROLE_ACCESS.TRIAGE}><TriagePage /></RequireRoles>} />
          <Route path="consultations" element={<RequireRoles roles={ROLE_ACCESS.CONSULTATIONS}><ConsultationPage /></RequireRoles>} />
          <Route path="laboratory" element={<RequireRoles roles={ROLE_ACCESS.LABORATORY}><LaboratoryPage /></RequireRoles>} />
          <Route path="account" element={<AccountPage />} />
          <Route path="blood-bank" element={<RequireRoles roles={ROLE_ACCESS.BLOOD_BANK}><BloodBankPage /></RequireRoles>} />
          <Route path="reports" element={<RequireRoles roles={ROLE_ACCESS.REPORTS}><ReportsPage /></RequireRoles>} />
          <Route path="*" element={<Navigate to="/app" replace />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default function AppRouter() {
  return <BrowserRouter><AuthProvider><AppRoutes /></AuthProvider></BrowserRouter>;
}
