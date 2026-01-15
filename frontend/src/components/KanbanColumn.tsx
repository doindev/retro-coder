import { FeatureCard } from './FeatureCard'
import { Plus, Sparkles, RotateCcw, Bug } from 'lucide-react'
import type { Feature } from '../lib/types'

interface KanbanColumnProps {
  title: string
  count: number
  features: Feature[]
  color: 'pending' | 'progress' | 'done'
  onFeatureClick: (feature: Feature) => void
  onAddFeature?: () => void
  onReportBug?: () => void
  onExpandProject?: () => void
  showExpandButton?: boolean
  onResetInProgress?: () => void
  showResetButton?: boolean
}

const colorMap = {
  pending: 'var(--color-neo-pending)',
  progress: 'var(--color-neo-progress)',
  done: 'var(--color-neo-done)',
}

export function KanbanColumn({
  title,
  count,
  features,
  color,
  onFeatureClick,
  onAddFeature,
  onReportBug,
  onExpandProject,
  showExpandButton,
  onResetInProgress,
  showResetButton,
}: KanbanColumnProps) {
  return (
    <div
      className="neo-card overflow-hidden flex flex-col h-[90vh]"
      style={{ borderColor: colorMap[color] }}
    >
      {/* Header */}
      <div
        className="px-4 py-3 border-b-3 border-[var(--color-neo-border)]"
        style={{ backgroundColor: colorMap[color] }}
      >
        <div className="flex items-center justify-between">
          <h2 className="font-display text-lg font-bold uppercase flex items-center gap-2 text-[var(--color-neo-text)]">
            {title}
            <span className="neo-badge bg-white text-[var(--color-neo-text)]">{count}</span>
          </h2>
          {(onAddFeature || onReportBug || onExpandProject || (onResetInProgress && showResetButton)) && (
            <div className="flex items-center gap-2">
              {onAddFeature && (
                <button
                  onClick={onAddFeature}
                  className="neo-btn neo-btn-primary text-sm py-1.5 px-2"
                  title="Add new feature (N)"
                >
                  <Plus size={16} />
                </button>
              )}
              {onReportBug && (
                <button
                  onClick={onReportBug}
                  className="neo-btn bg-red-500 text-white text-sm py-1.5 px-2"
                  title="Report a bug (B)"
                >
                  <Bug size={16} />
                </button>
              )}
              {onExpandProject && showExpandButton && (
                <button
                  onClick={onExpandProject}
                  className="neo-btn bg-[var(--color-neo-progress)] text-black text-sm py-1.5 px-2"
                  title="Expand project with AI (E)"
                >
                  <Sparkles size={16} />
                </button>
              )}
              {onResetInProgress && showResetButton && (
                <button
                  onClick={onResetInProgress}
                  className="neo-btn bg-[var(--color-neo-pending)] text-black text-sm py-1.5 px-2"
                  title="Reset to pending"
                >
                  <RotateCcw size={16} />
                </button>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Cards */}
      <div className="p-4 space-y-3 flex-1 min-h-0 overflow-y-auto bg-[var(--color-neo-bg)]">
        {features.length === 0 ? (
          <div className="text-center py-8 text-[var(--color-neo-text-secondary)]">
            No features
          </div>
        ) : (
          features.map((feature, index) => (
            <div
              key={feature.id}
              className="animate-slide-in"
              style={{ animationDelay: `${index * 50}ms` }}
            >
              <FeatureCard
                feature={feature}
                onClick={() => onFeatureClick(feature)}
                isInProgress={color === 'progress'}
              />
            </div>
          ))
        )}
      </div>
    </div>
  )
}
