// Due dates are plain "yyyy-MM-dd" strings with no time and no zone. They are compared as strings, which is
// correct for this format and avoids the classic trap of new Date("2026-10-14"), which means midnight UTC and
// can show up as the previous day in a time zone west of Greenwich.

export type DueStatus = 'none' | 'overdue' | 'today' | 'soon' | 'later'

/** How many days ahead still count as "due soon". Same number as the backend's stand-up summary uses. */
export const DUE_SOON_DAYS = 3

/** The date of the given moment in the browser's local time zone, as "yyyy-MM-dd". */
export function toIsoDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/** "yyyy-MM-dd" plus a number of days, calculated in UTC so daylight-saving changes cannot shift the result. */
export function addDays(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  const shifted = new Date(Date.UTC(year, month - 1, day + days))
  return shifted.toISOString().slice(0, 10)
}

export function dueStatus(dueDate: string | null, today: string): DueStatus {
  if (!dueDate) return 'none'
  if (dueDate < today) return 'overdue'
  if (dueDate === today) return 'today'
  if (dueDate <= addDays(today, DUE_SOON_DAYS)) return 'soon'
  return 'later'
}

/** "Oct 14" style label for a due date, in English and without any time-zone conversion. */
export function formatDueDate(dueDate: string): string {
  const [year, month, day] = dueDate.split('-').map(Number)
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  return `${months[month - 1]} ${day}, ${year}`
}
