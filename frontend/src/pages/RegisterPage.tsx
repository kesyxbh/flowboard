import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { z } from 'zod'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { errorMessage } from '../lib/errors'

// The same rules the backend enforces (RegisterRequest): checking them here gives instant feedback.
const schema = z.object({
  username: z
    .string()
    .regex(/^[\p{L}\p{N}_.-]{3,50}$/u, 'Use 3-50 characters: letters, digits, "_", "." or "-"'),
  email: z.email('Please enter a valid email address').max(254),
  password: z.string().min(8, 'The password needs at least 8 characters').max(72, 'The password is too long'),
})
type FormValues = z.infer<typeof schema>

export function RegisterPage() {
  const { session, register: registerUser } = useAuth()
  const location = useLocation()
  const from = location.state?.from
  const target = from ? `${from.pathname}${from.search}` : '/boards'

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  if (session) return <Navigate to={target} replace />

  const onSubmit = handleSubmit(async (values) => {
    try {
      await registerUser(values)
    } catch (error) {
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
        // The backend reported which field is wrong: show the message next to that field.
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          if (field === 'username' || field === 'email' || field === 'password') setError(field, { message })
        }
      } else {
        setError('root', { message: errorMessage(error) })
      }
    }
  })

  return (
    <div className="form-card">
      <h1>Create an account</h1>

      <form onSubmit={onSubmit} noValidate>
        <label className="field">
          <span>Username</span>
          <input autoComplete="username" {...register('username')} />
          {errors.username && <span className="field-error">{errors.username.message}</span>}
        </label>

        <label className="field">
          <span>Email</span>
          <input type="email" autoComplete="email" {...register('email')} />
          {errors.email && <span className="field-error">{errors.email.message}</span>}
        </label>

        <label className="field">
          <span>Password</span>
          <input type="password" autoComplete="new-password" {...register('password')} />
          {errors.password && <span className="field-error">{errors.password.message}</span>}
        </label>

        {errors.root && (
          <p className="field-error" role="alert">
            {errors.root.message}
          </p>
        )}

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Creating account...' : 'Register'}
        </button>
      </form>

      <p>
        Already registered? <Link to="/login">Log in</Link>
      </p>
    </div>
  )
}
