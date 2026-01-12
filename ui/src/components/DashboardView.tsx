import { useState, useMemo } from 'react'
import { Plus, Loader2, Search, X } from 'lucide-react'
import type { ProjectSummary } from '../lib/types'
import { ProjectRow } from './ProjectRow'
import { SpecCreationModal } from './SpecCreationModal'
import { useQueryClient } from '@tanstack/react-query'

interface DashboardViewProps {
  projects: ProjectSummary[]
  isLoading: boolean
  onSelectProject: (name: string) => void
  onNewProject: () => void
}

export function DashboardView({
  projects,
  isLoading,
  onSelectProject,
  onNewProject,
}: DashboardViewProps) {
  const [specModalOpen, setSpecModalOpen] = useState(false)
  const [specProjectName, setSpecProjectName] = useState<string | null>(null)
  const [filterValue, setFilterValue] = useState('')
  const [sortOption, setSortOption] = useState('date-desc')
  const queryClient = useQueryClient()

  // Filter projects by any property containing the filter value
  const filteredProjects = useMemo(() => {
    let result = projects

    // Apply filter
    if (filterValue.trim()) {
      const searchTerm = filterValue.toLowerCase().trim()
      result = result.filter((project) => {
        // Search in name
        if (project.name.toLowerCase().includes(searchTerm)) return true
        // Search in path
        if (project.path.toLowerCase().includes(searchTerm)) return true
        // Search in created_at
        if (project.created_at?.toLowerCase().includes(searchTerm)) return true
        // Search in model
        if (project.model?.toLowerCase().includes(searchTerm)) return true
        // Search in stats (as string)
        if (String(project.stats.total).includes(searchTerm)) return true
        if (String(project.stats.passing).includes(searchTerm)) return true
        if (String(project.stats.percentage).includes(searchTerm)) return true
        return false
      })
    }

    // Apply sorting
    result = [...result].sort((a, b) => {
      switch (sortOption) {
        case 'name-asc':
          return a.name.localeCompare(b.name)
        case 'name-desc':
          return b.name.localeCompare(a.name)
        case 'date-asc':
          return (a.created_at || '').localeCompare(b.created_at || '')
        case 'date-desc':
          return (b.created_at || '').localeCompare(a.created_at || '')
        case 'percent-asc':
          return a.stats.percentage - b.stats.percentage
        case 'percent-desc':
          return b.stats.percentage - a.stats.percentage
        default:
          return 0
      }
    })

    return result
  }, [projects, filterValue, sortOption])

  const handleCreateSpec = (projectName: string) => {
    setSpecProjectName(projectName)
    setSpecModalOpen(true)
  }

  const handleSpecCreated = () => {
    queryClient.invalidateQueries({ queryKey: ['projects'] })
    setSpecModalOpen(false)
    setSpecProjectName(null)
  }

  const handleSpecModalClose = () => {
    setSpecModalOpen(false)
    setSpecProjectName(null)
  }
  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-16">
        <Loader2 size={32} className="animate-spin text-[var(--color-neo-text-secondary)]" />
      </div>
    )
  }

  if (projects.length === 0) {
    return (
      <div className="neo-empty-state mt-12">
        <h2 className="font-display text-2xl font-bold mb-2">
          Welcome to Retro-Coder
        </h2>
        <p className="text-[var(--color-neo-text-secondary)] mb-6">
          Create your first project to get started with autonomous coding.
        </p>
        <button
          onClick={onNewProject}
          className="neo-btn neo-btn-primary text-lg py-3 px-6"
        >
          <Plus size={20} />
          New Project
        </button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h2 className="font-display text-2xl font-bold uppercase">
          Projects
        </h2>
        <button
          onClick={onNewProject}
          className="neo-btn neo-btn-primary"
        >
          <Plus size={18} />
          New Project
        </button>
      </div>

      {/* Filter Input and Sort */}
      <div className="flex gap-4 justify-end">
        <div className="relative" style={{ width: '225px' }}>
          <Search
            size={16}
            className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-neo-text-secondary)]"
          />
          <input
            type="text"
            value={filterValue}
            onChange={(e) => setFilterValue(e.target.value)}
            placeholder="Filter..."
            className="neo-input pl-9 pr-8 w-full"
          />
          {filterValue && (
            <button
              onClick={() => setFilterValue('')}
              className="absolute right-2 top-1/2 -translate-y-1/2 text-[var(--color-neo-text-secondary)] hover:text-[var(--color-neo-text)] transition-colors"
              title="Clear filter"
            >
              <X size={16} />
            </button>
          )}
        </div>
        <select
          value={sortOption}
          onChange={(e) => setSortOption(e.target.value)}
          className="neo-input w-48"
        >
          <option value="name-asc">Project Name A-Z</option>
          <option value="name-desc">Project Name Z-A</option>
          <option value="date-asc">Create Date ASC</option>
          <option value="date-desc">Create Date DESC</option>
          <option value="percent-asc">% Complete ASC</option>
          <option value="percent-desc">% Complete DESC</option>
        </select>
      </div>

      {/* Project List */}
      <div className="space-y-4">
        {filteredProjects.length === 0 && filterValue ? (
          <div className="neo-card p-6 text-center">
            <p className="text-[var(--color-neo-text-secondary)]">
              No projects match "{filterValue}"
            </p>
          </div>
        ) : (
          filteredProjects.map((project) => (
            <ProjectRow
              key={project.name}
              project={project}
              onSelect={() => onSelectProject(project.name)}
              onCreateSpec={() => handleCreateSpec(project.name)}
            />
          ))
        )}
      </div>

      {/* Spec Creation Modal - lifted to DashboardView to prevent flickering */}
      {specProjectName && (
        <SpecCreationModal
          projectName={specProjectName}
          isOpen={specModalOpen}
          onClose={handleSpecModalClose}
          onSpecCreated={handleSpecCreated}
          onNavigateToProject={() => onSelectProject(specProjectName)}
        />
      )}
    </div>
  )
}
