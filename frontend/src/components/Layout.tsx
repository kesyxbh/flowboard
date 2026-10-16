import { startTransition } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ChatWidget } from './ChatWidget'

export function Layout() {
  const { session, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    // Navigating and clearing the session must take effect together, so both go in one startTransition.
    // Otherwise React first renders "logged out but still on a protected page", <RequireAuth> wins the race and
    // redirects to the login page, and that redirect overrides the navigation we wanted.
    startTransition(() => {
      navigate('/login')
      logout()
    })
  }

  return (
    <>
      <header className="site-header">
        <strong className="brand">FlowBoard</strong>
        <nav>{session && <NavLink to="/boards">Boards</NavLink>}</nav>
        <div className="user-area">
          {session ? (
            <>
              <span>{session.user.username}</span>
              <button type="button" className="secondary" onClick={handleLogout}>
                Log out
              </button>
            </>
          ) : (
            <>
              <NavLink to="/login">Log in</NavLink>
              <NavLink to="/register">Register</NavLink>
            </>
          )}
        </div>
      </header>
      <main>
        <Outlet />
      </main>
      {/* Keyed by user: logging in as someone else starts a fresh conversation. */}
      <ChatWidget key={session?.user.id ?? 'guest'} />
    </>
  )
}
