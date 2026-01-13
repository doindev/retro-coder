import { useState, useRef, useCallback } from 'react'
import { FolderOpen, Calendar, FileText } from 'lucide-react'
import type { ProjectSummary, AgentStatus } from '../lib/types'
import { useAgentStatus } from '../hooks/useProjects'
import { AgentControl } from './AgentControl'
import { ProjectSettings } from './ProjectSettings'

interface ProjectRowProps {
  project: ProjectSummary
  onSelect: () => void
  onCreateSpec?: () => void
}

export function ProjectRow({ project, onSelect, onCreateSpec }: ProjectRowProps) {
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [forceCloseSettings, setForceCloseSettings] = useState(false)
  const { data: agentStatusData } = useAgentStatus(project.name)
  const agentStatus: AgentStatus = agentStatusData?.status ?? 'stopped'

  // Track hover state for delayed close
  const isRowHovered = useRef(false)
  const isMenuHovered = useRef(false)
  const closeTimeoutRef = useRef<number | null>(null)
  const settingsOpenRef = useRef(settingsOpen)

  // Keep ref in sync with state
  settingsOpenRef.current = settingsOpen

  const clearCloseTimeout = useCallback(() => {
    if (closeTimeoutRef.current) {
      clearTimeout(closeTimeoutRef.current)
      closeTimeoutRef.current = null
    }
  }, [])

  const startCloseTimeout = useCallback(() => {
    clearCloseTimeout()
    closeTimeoutRef.current = window.setTimeout(() => {
      // Use ref to get current value, not stale closure
      if (!isRowHovered.current && !isMenuHovered.current && settingsOpenRef.current) {
        setForceCloseSettings(true)
        // Reset force close after a tick
        setTimeout(() => setForceCloseSettings(false), 0)
        setSettingsOpen(false)
      }
    }, 500) // 500ms delay
  }, [clearCloseTimeout])

  const handleRowMouseEnter = useCallback(() => {
    isRowHovered.current = true
    clearCloseTimeout()
  }, [clearCloseTimeout])

  const handleRowMouseLeave = useCallback(() => {
    isRowHovered.current = false
    if (settingsOpenRef.current) {
      startCloseTimeout()
    }
  }, [startCloseTimeout])

  const handleMenuHover = useCallback((isHovering: boolean) => {
    isMenuHovered.current = isHovering
    if (isHovering) {
      clearCloseTimeout()
    } else if (settingsOpenRef.current) {
      startCloseTimeout()
    }
  }, [clearCloseTimeout, startCloseTimeout])

  const handleSettingsOpenChange = useCallback((isOpen: boolean) => {
    setSettingsOpen(isOpen)
    if (!isOpen) {
      clearCloseTimeout()
    }
  }, [clearCloseTimeout])

  const { stats, created_at } = project
  const percentage = stats.total > 0 ? stats.percentage : 0

  // Format creation date with time
  const formatDate = (dateString: string | null) => {
    if (!dateString) return 'Unknown'
    try {
      const date = new Date(dateString)
      return date.toLocaleString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
      })
    } catch {
      return 'Unknown'
    }
  }

  // Determine status indicator color
  const getStatusColor = () => {
    switch (agentStatus) {
      case 'running':
        return 'bg-[var(--color-neo-progress)]'
      case 'paused':
        return 'bg-[var(--color-neo-pending)]'
      case 'crashed':
        return 'bg-[var(--color-neo-danger)]'
      default:
        return 'bg-gray-400'
    }
  }

  return (
    <div
      className={`neo-card p-4 ${
        settingsOpen ? '' : 'transition-shadow hover:shadow-[6px_6px_0px_0px_var(--color-neo-border)]'
      }`}
      onMouseEnter={handleRowMouseEnter}
      onMouseLeave={handleRowMouseLeave}
    >
      <div className="flex items-center gap-4">
        {/* Status Indicator */}
        <div
          className={`w-3 h-3 rounded-full ${getStatusColor()} ${
            agentStatus === 'running' ? 'animate-pulse' : ''
          }`}
          title={`Agent: ${agentStatus}`}
        />

        {/* Project Info - Clickable */}
        <button
          onClick={onSelect}
          className="flex-1 text-left hover:bg-[var(--color-neo-bg)] p-2 -m-2 rounded transition-colors"
        >
          <div className="flex items-center gap-3">
            <FolderOpen size={20} className="text-[var(--color-neo-text-secondary)]" />
            <div className="flex-1 min-w-0">
              <h3 className="font-display font-bold text-lg truncate">
                {project.name}
              </h3>
              <div className="flex items-center gap-2 text-sm text-[var(--color-neo-text-secondary)]">
                <Calendar size={14} />
                <span>{formatDate(created_at)}</span>
              </div>
            </div>
          </div>
        </button>

        {/* Progress Info */}
        <div className="flex items-center gap-4 min-w-[200px]">
          {/* Progress Bar */}
          <div className="flex-1">
            <div className="neo-progress h-4 mb-1">
              <div
                className="neo-progress-fill"
                style={{ width: `${percentage}%` }}
              />
            </div>
            <div className="flex justify-between text-xs text-[var(--color-neo-text-secondary)]">
              <span className="font-mono">
                {stats.passing}/{stats.total}
              </span>
              <span className="font-mono font-bold text-[var(--color-neo-done)]">
                {percentage.toFixed(0)}%
              </span>
            </div>
          </div>
        </div>

        {/* Controls */}
        <div className="flex items-center gap-2">
          {/* Create Spec button - only show if project has no spec AND agent is not running */}
          {!project.has_spec && onCreateSpec && agentStatus === 'stopped' && (
            <button
              onClick={onCreateSpec}
              className="neo-btn text-sm py-2 px-3 bg-[var(--color-neo-pending)]"
              title="Create App Spec"
            >
              <FileText size={18} />
            </button>
          )}
          {/* Agent Control - show if project has a spec OR if agent is currently running */}
          {(project.has_spec || agentStatus === 'running' || agentStatus === 'paused') && (
            <AgentControl
              projectName={project.name}
              status={agentStatus}
            />
          )}
          <ProjectSettings
            project={project}
            onOpenChange={handleSettingsOpenChange}
            onMenuHover={handleMenuHover}
            forceClose={forceCloseSettings}
          />
        </div>
      </div>
    </div>
  )
}
