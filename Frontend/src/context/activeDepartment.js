import { useEffect, useState } from "react";
import { useAuth } from "./useAuth";

// Staff work in their assigned department. Admin has no department, so admin
// picks one in the top bar; the choice is shared by every page (and kept for
// the browser tab) so the dashboard, queue and registration all use it.
const KEY = "mbq.adminDepartment";
const EVENT = "mbq:department";

function readChoice() {
  try {
    const value = sessionStorage.getItem(KEY);
    return value ? JSON.parse(value) : null;
  } catch {
    return null;
  }
}

export function chooseDepartment(department) {
  try {
    if (department) sessionStorage.setItem(KEY, JSON.stringify(department));
    else sessionStorage.removeItem(KEY);
  } catch {
    // storage unavailable: the choice just won't survive a reload
  }
  window.dispatchEvent(new Event(EVENT));
}

export function clearDepartmentChoice() {
  try { sessionStorage.removeItem(KEY); } catch { /* ignore */ }
}

/** The department the current user is working in: { departmentId, departmentName, facilityId, canChoose }. */
export function useActiveDepartment() {
  const { staff } = useAuth();
  const [chosen, setChosen] = useState(readChoice);

  useEffect(() => {
    const sync = () => setChosen(readChoice());
    window.addEventListener(EVENT, sync);
    return () => window.removeEventListener(EVENT, sync);
  }, []);

  const fixed = staff?.departmentId
    ? { id: staff.departmentId, name: staff.departmentName, facilityId: staff.facilityId }
    : null;
  const canChoose = !fixed && staff?.role === "ADMIN";
  const active = fixed || (canChoose ? chosen : null);

  return {
    departmentId: active?.id ?? null,
    departmentName: active?.name ?? null,
    facilityId: active?.facilityId ?? staff?.facilityId ?? null,
    canChoose,
  };
}
