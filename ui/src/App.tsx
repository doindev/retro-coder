import { useState, useEffect, useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useProjects, useFeatures, useAgentStatus } from './hooks/useProjects'
import { useProjectWebSocket } from './hooks/useWebSocket'
import { useFeatureSound } from './hooks/useFeatureSound'
import { useCelebration } from './hooks/useCelebration'

const STORAGE_KEY = 'autocoder-selected-project'
const VIEW_MODE_KEY = 'autocoder-view-mode'

import { ProjectSelector } from './components/ProjectSelector'
import { KanbanBoard } from './components/KanbanBoard'
import { AgentControl } from './components/AgentControl'
import { ProgressDashboard } from './components/ProgressDashboard'
import { SetupWizard } from './components/SetupWizard'
import { AddFeatureForm } from './components/AddFeatureForm'
import { FeatureModal } from './components/FeatureModal'
import { DebugLogViewer } from './components/DebugLogViewer'
import { AgentThought } from './components/AgentThought'
import { AssistantFAB } from './components/AssistantFAB'
import { AssistantPanel } from './components/AssistantPanel'
import { ExpandProjectModal } from './components/ExpandProjectModal'
import { SettingsModal } from './components/SettingsModal'
import { DashboardView } from './components/DashboardView'
import { NewProjectModal } from './components/NewProjectModal'
import { SpecCreationModal } from './components/SpecCreationModal'
import { Loader2, Settings, FileText } from 'lucide-react'
import type { Feature } from './lib/types'

type ViewMode = 'dashboard' | 'project'

function App() {
  // Initialize selected project from localStorage
  const [selectedProject, setSelectedProject] = useState<string | null>(() => {
    try {
      return localStorage.getItem(STORAGE_KEY)
    } catch {
      return null
    }
  })

  // Initialize view mode from localStorage
  const [viewMode, setViewMode] = useState<ViewMode>(() => {
    try {
      const stored = localStorage.getItem(VIEW_MODE_KEY)
      return stored === 'project' ? 'project' : 'dashboard'
    } catch {
      return 'dashboard'
    }
  })

  const [showAddFeature, setShowAddFeature] = useState(false)
  const [showExpandProject, setShowExpandProject] = useState(false)
  const [showNewProject, setShowNewProject] = useState(false)
  const [selectedFeature, setSelectedFeature] = useState<Feature | null>(null)
  const [setupComplete, setSetupComplete] = useState(true) // Start optimistic
  const [debugOpen, setDebugOpen] = useState(false)
  const [debugPanelHeight, setDebugPanelHeight] = useState(288) // Default height
  const [assistantOpen, setAssistantOpen] = useState(false)
  const [showSettings, setShowSettings] = useState(false)
  const [showSpecCreation, setShowSpecCreation] = useState(false)

  const queryClient = useQueryClient()
  const { data: projects, isLoading: projectsLoading } = useProjects()
  const { data: features } = useFeatures(selectedProject)
  useAgentStatus(selectedProject) // Keep polling for status updates
  const wsState = useProjectWebSocket(selectedProject)

  // Play sounds when features move between columns
  useFeatureSound(features)

  // Celebrate when all features are complete
  useCelebration(features, selectedProject)

  // Persist selected project to localStorage
  const handleSelectProject = useCallback((project: string | null) => {
    setSelectedProject(project)
    try {
      if (project) {
        localStorage.setItem(STORAGE_KEY, project)
      } else {
        localStorage.removeItem(STORAGE_KEY)
      }
    } catch {
      // localStorage not available
    }
  }, [])

  // Handle view mode changes with persistence
  const handleViewModeChange = useCallback((mode: ViewMode) => {
    setViewMode(mode)
    try {
      localStorage.setItem(VIEW_MODE_KEY, mode)
    } catch {
      // localStorage not available
    }
  }, [])

  // Handle project selection from dashboard (selects project and switches to project view)
  const handleDashboardProjectSelect = useCallback((projectName: string) => {
    handleSelectProject(projectName)
    handleViewModeChange('project')
  }, [handleSelectProject, handleViewModeChange])

  // Handle new project creation from dashboard
  const handleProjectCreated = useCallback((projectName: string) => {
    handleSelectProject(projectName)
    handleViewModeChange('project')
    setShowNewProject(false)
  }, [handleSelectProject, handleViewModeChange])

  // Validate stored project exists (clear if project was deleted)
  useEffect(() => {
    if (selectedProject && projects && !projects.some(p => p.name === selectedProject)) {
      handleSelectProject(null)
    }
  }, [selectedProject, projects, handleSelectProject])

  // Keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Ignore if user is typing in an input
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) {
        return
      }

      // D : Toggle debug window
      if (e.key === 'd' || e.key === 'D') {
        e.preventDefault()
        setDebugOpen(prev => !prev)
      }

      // N : Add new feature (when project selected)
      if ((e.key === 'n' || e.key === 'N') && selectedProject) {
        e.preventDefault()
        setShowAddFeature(true)
      }

      // E : Expand project with AI (when project selected and has features)
      if ((e.key === 'e' || e.key === 'E') && selectedProject && features &&
          (features.pending.length + features.in_progress.length + features.done.length) > 0) {
        e.preventDefault()
        setShowExpandProject(true)
      }

      // A : Toggle assistant panel (when project selected)
      if ((e.key === 'a' || e.key === 'A') && selectedProject) {
        e.preventDefault()
        setAssistantOpen(prev => !prev)
      }

      // , : Open settings
      if (e.key === ',') {
        e.preventDefault()
        setShowSettings(true)
      }

      // H : Return to dashboard
      if (e.key === 'h' || e.key === 'H') {
        e.preventDefault()
        handleViewModeChange('dashboard')
      }

      // Escape : Close modals
      if (e.key === 'Escape') {
        if (showExpandProject) {
          setShowExpandProject(false)
        } else if (showSettings) {
          setShowSettings(false)
        } else if (assistantOpen) {
          setAssistantOpen(false)
        } else if (showAddFeature) {
          setShowAddFeature(false)
        } else if (selectedFeature) {
          setSelectedFeature(null)
        } else if (debugOpen) {
          setDebugOpen(false)
        }
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [selectedProject, showAddFeature, showExpandProject, selectedFeature, debugOpen, assistantOpen, features, showSettings, handleViewModeChange])

  // Combine WebSocket progress with feature data
  const progress = wsState.progress.total > 0 ? wsState.progress : {
    passing: features?.done.length ?? 0,
    total: (features?.pending.length ?? 0) + (features?.in_progress.length ?? 0) + (features?.done.length ?? 0),
    percentage: 0,
  }

  if (progress.total > 0 && progress.percentage === 0) {
    progress.percentage = Math.round((progress.passing / progress.total) * 100 * 10) / 10
  }

  if (!setupComplete) {
    return <SetupWizard onComplete={() => setSetupComplete(true)} />
  }

  return (
    <div className="min-h-screen bg-[var(--color-neo-bg)]">
      {/* Header */}
      <header className="bg-[var(--color-neo-text)] text-white border-b-4 border-[var(--color-neo-border)]">
        <div className="max-w-7xl mx-auto px-4 py-4">
          <div className="flex items-center justify-between">
            {/* Logo and Title - Clickable to return to dashboard */}
            <button
              onClick={() => handleViewModeChange('dashboard')}
              className="hover:opacity-80 transition-opacity cursor-pointer"
              title="Return to Dashboard (H)"
            >
              <h1 className="font-display text-2xl font-bold tracking-tight uppercase">
                Retro-Coder
              </h1>
            </button>

            {/* Controls - Show different controls based on view mode */}
            <div className="flex items-center gap-4">
              {viewMode === 'project' && (
                <>
                  <ProjectSelector
                    projects={projects ?? []}
                    selectedProject={selectedProject}
                    onSelectProject={handleSelectProject}
                    onCurrentProjectDeleted={() => {
                      handleSelectProject(null)
                      handleViewModeChange('dashboard')
                    }}
                    isLoading={projectsLoading}
                  />

                  {selectedProject && (
                    <>
                      {/* Create Spec button - show if project has no spec */}
                      {projects?.find(p => p.name === selectedProject)?.has_spec === false && (
                        <button
                          onClick={() => setShowSpecCreation(true)}
                          className="neo-btn text-sm py-2 px-3 bg-[var(--color-neo-pending)]"
                          title="Create App Spec"
                        >
                          <FileText size={18} />
                        </button>
                      )}

                      {/* Agent Control - only show if project has a spec */}
                      {projects?.find(p => p.name === selectedProject)?.has_spec && (
                        <AgentControl
                          projectName={selectedProject}
                          status={wsState.agentStatus}
                        />
                      )}

                      <button
                        onClick={() => setShowSettings(true)}
                        className="neo-btn text-sm py-2 px-3"
                        title="Settings (,)"
                        aria-label="Open Settings"
                      >
                        <Settings size={18} />
                      </button>
                    </>
                  )}
                </>
              )}
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main
        className="max-w-7xl mx-auto px-4 py-8"
        style={{ paddingBottom: viewMode === 'project' && debugOpen ? debugPanelHeight + 32 : undefined }}
      >
        {viewMode === 'dashboard' ? (
          /* Dashboard View */
          <DashboardView
            projects={projects ?? []}
            isLoading={projectsLoading}
            onSelectProject={handleDashboardProjectSelect}
            onNewProject={() => setShowNewProject(true)}
          />
        ) : !selectedProject ? (
          /* Project View - No project selected */
          <div className="neo-empty-state mt-12">
            <h2 className="font-display text-2xl font-bold mb-2">
              Welcome to Retro-Coder
            </h2>
            <p className="text-[var(--color-neo-text-secondary)] mb-4">
              Select a project from the dropdown above or create a new one to get started.
            </p>
          </div>
        ) : (
          /* Project View - Project selected */
          <div className="space-y-8">
            {/* Progress Dashboard */}
            <ProgressDashboard
              passing={progress.passing}
              total={progress.total}
              percentage={progress.percentage}
              isConnected={wsState.isConnected}
            />

            {/* Agent Thought - shows latest agent narrative */}
            <AgentThought
              logs={wsState.logs}
              agentStatus={wsState.agentStatus}
            />

            {/* Initializing Features State - show when agent is running but no features yet */}
            {features &&
             features.pending.length === 0 &&
             features.in_progress.length === 0 &&
             features.done.length === 0 &&
             wsState.agentStatus === 'running' && (
              <div className="neo-card p-8 text-center">
                <Loader2 size={32} className="animate-spin mx-auto mb-4 text-[var(--color-neo-progress)]" />
                <h3 className="font-display font-bold text-xl mb-2">
                  Initializing Features...
                </h3>
                <p className="text-[var(--color-neo-text-secondary)]">
                  The agent is reading your spec and creating features. This may take a moment.
                </p>
              </div>
            )}

            {/* Kanban Board */}
            <KanbanBoard
              features={features}
              onFeatureClick={setSelectedFeature}
              onAddFeature={() => setShowAddFeature(true)}
              onExpandProject={() => setShowExpandProject(true)}
            />
          </div>
        )}
      </main>

      {/* Add Feature Modal */}
      {showAddFeature && selectedProject && (
        <AddFeatureForm
          projectName={selectedProject}
          onClose={() => setShowAddFeature(false)}
        />
      )}

      {/* Feature Detail Modal */}
      {selectedFeature && selectedProject && (
        <FeatureModal
          feature={selectedFeature}
          projectName={selectedProject}
          onClose={() => setSelectedFeature(null)}
        />
      )}

      {/* Expand Project Modal - AI-powered bulk feature creation */}
      {showExpandProject && selectedProject && (
        <ExpandProjectModal
          isOpen={showExpandProject}
          projectName={selectedProject}
          onClose={() => setShowExpandProject(false)}
          onFeaturesAdded={() => {
            // Invalidate features query to refresh the kanban board
            queryClient.invalidateQueries({ queryKey: ['features', selectedProject] })
          }}
        />
      )}

      {/* Debug Log Viewer - fixed to bottom */}
      {selectedProject && (
        <DebugLogViewer
          logs={wsState.logs}
          isOpen={debugOpen}
          onToggle={() => setDebugOpen(!debugOpen)}
          onClear={wsState.clearLogs}
          onHeightChange={setDebugPanelHeight}
        />
      )}

      {/* Assistant FAB and Panel - only show on project view, hide when expand modal is open */}
      {viewMode === 'project' && selectedProject && !showExpandProject && (
        <>
          <AssistantFAB
            onClick={() => setAssistantOpen(!assistantOpen)}
            isOpen={assistantOpen}
          />
          <AssistantPanel
            projectName={selectedProject}
            isOpen={assistantOpen}
            onClose={() => setAssistantOpen(false)}
          />
        </>
      )}

      {/* Settings Modal */}
      {showSettings && (
        <SettingsModal onClose={() => setShowSettings(false)} />
      )}

      {/* New Project Modal - for dashboard view */}
      <NewProjectModal
        isOpen={showNewProject}
        onClose={() => setShowNewProject(false)}
        onProjectCreated={handleProjectCreated}
      />

      {/* Spec Creation Modal - for projects without specs */}
      {selectedProject && (
        <SpecCreationModal
          projectName={selectedProject}
          isOpen={showSpecCreation}
          onClose={() => setShowSpecCreation(false)}
          onSpecCreated={() => {
            queryClient.invalidateQueries({ queryKey: ['projects'] })
            setShowSpecCreation(false)
          }}
        />
      )}
    </div>
  )
}

export default App
