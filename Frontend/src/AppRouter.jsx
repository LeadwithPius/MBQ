import { BrowserRouter, Navigate, Outlet, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import { useAuth } from "./context/useAuth";
import LandingPage from "./pages/LandingPage";
import LoginPage from "./pages/LoginPage";
import {
  WorkspaceLayout, DashboardPage, PatientsPage, QueuePage, TriagePage, ComingSoonPage,
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

function AppRoutes() {
  const { session } = useAuth();
  return (
    <Routes>
      <Route path="/" element={session ? <Navigate to="/app" replace /> : <LandingPage />} />
      <Route path="/login" element={session ? <Navigate to="/app" replace /> : <LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route path="/app" element={<WorkspaceLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="patients" element={<RequireRoles roles={ROLE_ACCESS.PATIENTS}><PatientsPage /></RequireRoles>} />
          <Route path="queue" element={<RequireRoles roles={ROLE_ACCESS.QUEUE}><QueuePage /></RequireRoles>} />
          <Route path="triage" element={<RequireRoles roles={ROLE_ACCESS.TRIAGE}><TriagePage /></RequireRoles>} />
          <Route path="blood-bank" element={<RequireRoles roles={ROLE_ACCESS.BLOOD_BANK}><ComingSoonPage title="Blood bank" description="The database foundation exists, but the remote backend does not yet expose blood-stock APIs. This screen will be connected when those endpoints are implemented." /></RequireRoles>} />
          <Route path="reports" element={<RequireRoles roles={ROLE_ACCESS.REPORTS}><ComingSoonPage title="Reports" description="Reporting endpoints have not yet been exposed by the backend. No sample figures are shown here." /></RequireRoles>} />
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
