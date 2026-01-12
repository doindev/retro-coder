/**
 * Spec Creation Modal Component
 *
 * A modal wrapper around SpecCreationChat for creating specs
 * for existing projects that don't have one yet.
 */

import { useState } from 'react'
import { SpecCreationChat } from './SpecCreationChat'
import { startAgent } from '../lib/api'

type InitializerStatus = 'idle' | 'starting' | 'error'

interface SpecCreationModalProps {
  projectName: string
  isOpen: boolean
  onClose: () => void
  onSpecCreated: () => void
  onNavigateToProject?: () => void
}

export function SpecCreationModal({
  projectName,
  isOpen,
  onClose,
  onSpecCreated,
  onNavigateToProject,
}: SpecCreationModalProps) {
  const [initializerStatus, setInitializerStatus] = useState<InitializerStatus>('idle')
  const [initializerError, setInitializerError] = useState<string | null>(null)
  const [yoloModeSelected, setYoloModeSelected] = useState(false)

  if (!isOpen) return null

  const handleSpecComplete = async (_specPath: string, yoloMode: boolean = false) => {
    setYoloModeSelected(yoloMode)
    setInitializerStatus('starting')
    try {
      await startAgent(projectName, yoloMode)
      // Success - close modal and notify parent
      onSpecCreated()
      handleClose()
    } catch (err) {
      setInitializerStatus('error')
      setInitializerError(err instanceof Error ? err.message : 'Failed to start agent')
    }
  }

  const handleRetryInitializer = () => {
    setInitializerError(null)
    setInitializerStatus('idle')
    handleSpecComplete('', yoloModeSelected)
  }

  const handleCancel = () => {
    handleClose()
  }

  const handleExitToProject = () => {
    // Exit without starting agent - refresh data and navigate to project
    onSpecCreated()
    handleClose()
    onNavigateToProject?.()
  }

  const handleClose = () => {
    setInitializerStatus('idle')
    setInitializerError(null)
    setYoloModeSelected(false)
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 bg-[var(--color-neo-bg)]">
      <SpecCreationChat
        projectName={projectName}
        onComplete={handleSpecComplete}
        onCancel={handleCancel}
        onExitToProject={handleExitToProject}
        initializerStatus={initializerStatus}
        initializerError={initializerError}
        onRetryInitializer={handleRetryInitializer}
      />
    </div>
  )
}
