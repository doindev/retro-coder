import { Wifi, WifiOff } from 'lucide-react'

interface ProgressDashboardProps {
  passing: number
  total: number
  percentage: number
  isConnected: boolean
}

export function ProgressDashboard({
  passing,
  total,
  percentage,
  isConnected,
}: ProgressDashboardProps) {
  return (
    <div className="neo-card p-4">
      <div className="flex items-center justify-between">
        <h2 className="font-display text-lg font-bold uppercase">
          Progress
        </h2>
        <div className="flex items-center gap-2">
          {isConnected ? (
            <>
              <Wifi size={14} className="text-[var(--color-neo-done)]" />
              <span className="text-xs text-[var(--color-neo-done)]">Live</span>
            </>
          ) : (
            <>
              <WifiOff size={14} className="text-[var(--color-neo-danger)]" />
              <span className="text-xs text-[var(--color-neo-danger)]">Offline</span>
            </>
          )}
        </div>
      </div>

      {/* Large Percentage */}
      <div className="text-center mb-3">
        <span className="font-display text-5xl font-bold">
          {percentage.toFixed(1)}
        </span>
        <span className="font-display text-2xl font-bold text-[var(--color-neo-text-secondary)]">
          %
        </span>
      </div>

      {/* Progress Bar */}
      <div className="neo-progress mb-2">
        <div
          className="neo-progress-fill"
          style={{ width: `${percentage}%` }}
        />
      </div>

      {/* Stats */}
      <div className="flex justify-center gap-6 text-center">
        <div>
          <span className="font-mono text-2xl font-bold text-[var(--color-neo-done)]">
            {passing}
          </span>
          <span className="block text-xs text-[var(--color-neo-text-secondary)] uppercase">
            Passing
          </span>
        </div>
        <div className="text-3xl text-[var(--color-neo-text-secondary)]">/</div>
        <div>
          <span className="font-mono text-2xl font-bold">
            {total}
          </span>
          <span className="block text-xs text-[var(--color-neo-text-secondary)] uppercase">
            Total
          </span>
        </div>
      </div>
    </div>
  )
}
