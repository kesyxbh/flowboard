import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

// Route guard: wrap the routes that need a login in <Route element={<RequireAuth />}>. Anonymous visitors are
// sent to the login page, and the place they wanted to go is remembered so they can be brought back afterwards.
export function RequireAuth() {
  const { session } = useAuth()
  const location = useLocation()

  if (!session) return <Navigate to="/login" replace state={{ from: location }} />
  return <Outlet />
}
