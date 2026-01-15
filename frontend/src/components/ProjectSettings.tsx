import { useState, useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { Settings, Loader2 } from 'lucide-react'
import type { ProjectSummary } from '../lib/types'
import { useUpdateProjectSettings, useAvailableModels, useSettings } from '../hooks/useProjects'

interface ProjectSettingsProps {
  project: ProjectSummary
  onOpenChange?: (isOpen: boolean) => void
  onMenuHover?: (isHovering: boolean) => void
  forceClose?: boolean
}

export function ProjectSettings({ project, onOpenChange, onMenuHover, forceClose }: ProjectSettingsProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [menuPosition, setMenuPosition] = useState<{ top: number; right: number } | null>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)

  // Handle force close from parent
  useEffect(() => {
    if (forceClose && isOpen) {
      setIsOpen(false)
      setMenuPosition(null)
      onOpenChange?.(false)
    }
  }, [forceClose, isOpen, onOpenChange])

  const handleOpenChange = (open: boolean) => {
    if (open && buttonRef.current) {
      // Calculate position synchronously before opening
      const rect = buttonRef.current.getBoundingClientRect()
      setMenuPosition({
        top: rect.bottom + 8, // 8px gap below button
        right: window.innerWidth - rect.right,
      })
    } else {
      setMenuPosition(null)
    }
    setIsOpen(open)
    onOpenChange?.(open)
  }

  // Handle click outside to close menu
  useEffect(() => {
    if (!isOpen) return

    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node
      const menuEl = document.getElementById(`project-settings-menu-${project.name}`)
      const buttonEl = buttonRef.current

      if (menuEl && !menuEl.contains(target) && buttonEl && !buttonEl.contains(target)) {
        handleOpenChange(false)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [isOpen, project.name])
  const { data: modelsData } = useAvailableModels()
  const { data: globalSettings } = useSettings()
  const updateSettings = useUpdateProjectSettings(project.name)

  const models = modelsData?.models ?? []
  const isSaving = updateSettings.isPending

  // Get effective settings (project-specific or fallback to global)
  const effectiveYoloMode = project.yolo_mode ?? globalSettings?.yolo_mode ?? false
  const effectiveModel = project.model ?? globalSettings?.model ?? 'claude-opus-4-5-20251101'

  // Check if using project-specific settings
  const hasProjectSettings = project.yolo_mode !== null || project.model !== null

  const handleYoloToggle = () => {
    if (!isSaving) {
      updateSettings.mutate({ yolo_mode: !effectiveYoloMode })
    }
  }

  const handleModelChange = (modelId: string) => {
    if (!isSaving) {
      updateSettings.mutate({ model: modelId })
    }
  }

  return (
    <div className="relative">
      {/* Trigger Button */}
      <button
        ref={buttonRef}
        onClick={() => handleOpenChange(!isOpen)}
        className={`neo-btn text-sm py-2 px-3 ${
          hasProjectSettings ? 'bg-[var(--color-neo-accent)] text-white' : ''
        }`}
        title="Project Settings"
        aria-label="Project Settings"
      >
        {isSaving ? (
          <Loader2 size={18} className="animate-spin" />
        ) : (
          <Settings size={18} />
        )}
      </button>

      {/* Dropdown Menu - Rendered via Portal (only when position is calculated) */}
      {isOpen && menuPosition && createPortal(
        <>
          {/* Menu */}
          <div
            id={`project-settings-menu-${project.name}`}
            className="fixed w-64 neo-dropdown p-4"
            style={{
              zIndex: 9999,
              top: menuPosition.top,
              right: menuPosition.right,
            }}
            onMouseEnter={() => onMenuHover?.(true)}
            onMouseLeave={() => onMenuHover?.(false)}
          >
            <h4 className="font-display font-bold text-sm mb-4 uppercase">
              Project Settings
            </h4>

            {/* YOLO Mode Toggle */}
            <div className="mb-4">
              <div className="flex items-center justify-between">
                <div>
                  <label className="font-display font-bold text-sm">
                    YOLO Mode
                  </label>
                  <p className="text-xs text-[var(--color-neo-text-secondary)]">
                    Skip testing
                  </p>
                </div>
                <button
                  onClick={handleYoloToggle}
                  disabled={isSaving}
                  className={`relative w-12 h-6 rounded-none border-2 border-[var(--color-neo-border)] transition-colors ${
                    effectiveYoloMode
                      ? 'bg-[var(--color-neo-pending)]'
                      : 'bg-white'
                  } ${isSaving ? 'opacity-50 cursor-not-allowed' : ''}`}
                  role="switch"
                  aria-checked={effectiveYoloMode}
                >
                  <span
                    className={`absolute top-0.5 w-4 h-4 bg-[var(--color-neo-border)] transition-transform ${
                      effectiveYoloMode ? 'left-6' : 'left-0.5'
                    }`}
                  />
                </button>
              </div>
            </div>

            {/* Model Selection */}
            <div>
              <label className="font-display font-bold text-sm block mb-2">
                Model
              </label>
              <div className="space-y-1">
                {models.map((model) => (
                  <button
                    key={model.id}
                    onClick={() => handleModelChange(model.id)}
                    disabled={isSaving}
                    className={`w-full text-left px-3 py-2 text-sm border-2 border-[var(--color-neo-border)] transition-colors ${
                      effectiveModel === model.id
                        ? 'bg-[var(--color-neo-accent)] text-white font-bold'
                        : 'bg-white hover:bg-gray-100'
                    } ${isSaving ? 'opacity-50 cursor-not-allowed' : ''}`}
                  >
                    {model.name}
                  </button>
                ))}
              </div>
            </div>

            {/* Indicator for project-specific settings */}
            {hasProjectSettings && (
              <div className="mt-4 pt-3 border-t-2 border-[var(--color-neo-border)]">
                <p className="text-xs text-[var(--color-neo-accent)]">
                  Using project-specific settings
                </p>
              </div>
            )}
          </div>
        </>,
        document.body
      )}
    </div>
  )
}
