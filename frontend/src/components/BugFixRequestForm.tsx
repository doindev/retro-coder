import { useState, useRef, useCallback, useId } from 'react'
import { Bug, X, Loader2, Plus, Trash2, Search, CheckCircle, AlertCircle, Upload, FileText } from 'lucide-react'
import { useInvestigateBug, useCreateFeature } from '../hooks/useProjects'

interface BugFixRequestFormProps {
  projectName: string
  onClose: () => void
}

interface Step {
  id: string
  value: string
}

interface UploadedFile {
  id: string
  name: string
  path: string
  content: string
}

type FormPhase = 'describe' | 'investigating' | 'review'

export function BugFixRequestForm({ projectName, onClose }: BugFixRequestFormProps) {
  const formId = useId()

  // Form state
  const [phase, setPhase] = useState<FormPhase>('describe')
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [uploadedFiles, setUploadedFiles] = useState<UploadedFile[]>([])
  const [isDragging, setIsDragging] = useState(false)
  const [stepCounter, setStepCounter] = useState(0)

  // Review phase state (populated after investigation)
  const [name, setName] = useState('')
  const [rootCause, setRootCause] = useState('')
  const [affectedFiles, setAffectedFiles] = useState<string[]>([])
  const [steps, setSteps] = useState<Step[]>([{ id: `${formId}-step-0`, value: '' }])
  const [additionalNotes, setAdditionalNotes] = useState('')

  const fileInputRef = useRef<HTMLInputElement>(null)

  // Mutations
  const investigateBug = useInvestigateBug(projectName)
  const createFeature = useCreateFeature(projectName)

  // File handling
  const handleFiles = useCallback(async (files: FileList | File[]) => {
    const fileArray = Array.from(files)
    const newFiles: UploadedFile[] = []

    for (const file of fileArray) {
      // Only accept text-based files
      if (file.type.startsWith('text/') ||
          file.name.endsWith('.ts') ||
          file.name.endsWith('.tsx') ||
          file.name.endsWith('.js') ||
          file.name.endsWith('.jsx') ||
          file.name.endsWith('.java') ||
          file.name.endsWith('.py') ||
          file.name.endsWith('.json') ||
          file.name.endsWith('.xml') ||
          file.name.endsWith('.yaml') ||
          file.name.endsWith('.yml') ||
          file.name.endsWith('.md') ||
          file.name.endsWith('.css') ||
          file.name.endsWith('.html') ||
          file.name.endsWith('.sql') ||
          file.name.endsWith('.log') ||
          file.name.endsWith('.txt')) {
        try {
          const content = await file.text()
          newFiles.push({
            id: crypto.randomUUID(),
            name: file.name,
            path: (file as any).webkitRelativePath || file.name,
            content: content.slice(0, 10000), // Limit content size
          })
        } catch (err) {
          console.error('Failed to read file:', file.name, err)
        }
      }
    }

    setUploadedFiles(prev => [...prev, ...newFiles])
  }, [])

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(true)
  }, [])

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(false)
  }, [])

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(false)
    if (e.dataTransfer.files.length > 0) {
      handleFiles(e.dataTransfer.files)
    }
  }, [handleFiles])

  const handleBrowseClick = () => {
    fileInputRef.current?.click()
  }

  const handleFileInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      handleFiles(e.target.files)
    }
  }

  const removeFile = (id: string) => {
    setUploadedFiles(prev => prev.filter(f => f.id !== id))
  }

  // Handle investigation
  const handleInvestigate = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setPhase('investigating')

    // Build full description including file contents
    let fullDescription = description
    if (uploadedFiles.length > 0) {
      fullDescription += '\n\n--- Related Files ---\n'
      for (const file of uploadedFiles) {
        fullDescription += `\n### File: ${file.name}\n\`\`\`\n${file.content}\n\`\`\`\n`
      }
    }

    try {
      const result = await investigateBug.mutateAsync(fullDescription)

      if (result.success) {
        // Populate review form with investigation results
        setName(result.suggestedName || `Fix: ${description.slice(0, 50)}`)
        setRootCause(result.rootCause || '')
        setAffectedFiles(result.affectedFiles || [])
        const newSteps = result.steps && result.steps.length > 0
          ? result.steps.map((s, i) => ({ id: `${formId}-step-${i}`, value: s }))
          : [{ id: `${formId}-step-0`, value: '' }]
        setSteps(newSteps)
        setStepCounter(newSteps.length)
        setAdditionalNotes(result.additionalNotes || '')
        setPhase('review')
      } else {
        setError(result.error || 'Investigation failed')
        setPhase('describe')
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Investigation failed')
      setPhase('describe')
    }
  }

  // Handle creating the bugfix feature
  const handleCreateBugfix = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    const filteredSteps = steps
      .map((s) => s.value.trim())
      .filter((s) => s.length > 0)

    // Build description with root cause and notes
    let fullDescription = description
    if (rootCause) {
      fullDescription += `\n\nRoot Cause: ${rootCause}`
    }
    if (affectedFiles.length > 0) {
      fullDescription += `\n\nAffected Files:\n${affectedFiles.map(f => `- ${f}`).join('\n')}`
    }
    if (additionalNotes) {
      fullDescription += `\n\nNotes: ${additionalNotes}`
    }

    try {
      await createFeature.mutateAsync({
        category: 'bugfix',
        name: name.trim(),
        description: fullDescription.trim(),
        steps: filteredSteps,
      })
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create bugfix')
    }
  }

  // Step management
  const handleAddStep = () => {
    setSteps([...steps, { id: `${formId}-step-${stepCounter}`, value: '' }])
    setStepCounter(stepCounter + 1)
  }

  const handleRemoveStep = (id: string) => {
    if (steps.length > 1) {
      setSteps(steps.filter((s) => s.id !== id))
    }
  }

  const handleStepChange = (id: string, value: string) => {
    setSteps(steps.map((s) => (s.id === id ? { ...s, value } : s)))
  }

  // Validation
  const isDescribeValid = description.trim().length > 10
  const isReviewValid = name.trim().length > 0 && steps.some((s) => s.value.trim().length > 0)

  return (
    <div className="neo-modal-backdrop">
      <div
        className="neo-modal w-full max-w-2xl max-h-[90vh] overflow-hidden flex flex-col"
      >
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b-3 border-[var(--color-neo-border)]">
          <h2 className="font-display text-2xl font-bold flex items-center gap-3">
            <Bug size={28} className="text-red-600" />
            {phase === 'describe' && 'Report a Bug'}
            {phase === 'investigating' && 'Investigating...'}
            {phase === 'review' && 'Review Bug Fix'}
          </h2>
          <button
            onClick={onClose}
            className="neo-btn neo-btn-ghost p-2"
          >
            <X size={24} />
          </button>
        </div>

        {/* Content - scrollable */}
        <div className="flex-1 overflow-y-auto">
          {/* Phase 1: Describe the bug */}
          {phase === 'describe' && (
            <form onSubmit={handleInvestigate} className="p-6 space-y-4">
              {/* Error Message */}
              {error && (
                <div className="flex items-center gap-3 p-4 bg-[var(--color-neo-danger)] text-white border-3 border-[var(--color-neo-border)]">
                  <AlertCircle size={20} />
                  <span>{error}</span>
                  <button
                    type="button"
                    onClick={() => setError(null)}
                    className="ml-auto"
                  >
                    <X size={16} />
                  </button>
                </div>
              )}

              {/* Bug Description */}
              <div>
                <label className="block font-display font-bold mb-2 uppercase text-sm">
                  Bug Description
                </label>
                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Describe what's happening, what you expected, and any error messages you see..."
                  className="neo-input min-h-[150px] resize-y"
                  autoFocus
                  required
                />
                <p className="text-xs text-[var(--color-neo-text-secondary)] mt-2">
                  Be as specific as possible. Include error messages, steps to reproduce, and what you expected.
                </p>
              </div>

              {/* File Upload Area */}
              <div>
                <label className="block font-display font-bold mb-2 uppercase text-sm">
                  Related Files (Optional)
                </label>
                <div
                  className={`border-3 border-dashed rounded-lg p-3 transition-colors ${
                    isDragging
                      ? 'border-[var(--color-neo-primary)] bg-blue-50'
                      : 'border-[var(--color-neo-border)] hover:border-[var(--color-neo-primary)]'
                  }`}
                  onDragOver={handleDragOver}
                  onDragLeave={handleDragLeave}
                  onDrop={handleDrop}
                >
                  <div className="flex items-center justify-center gap-3">
                    <Upload size={20} className="text-[var(--color-neo-text-secondary)]" />
                    <span className="text-sm text-[var(--color-neo-text-secondary)]">
                      Drag & drop files here, or
                    </span>
                    <button
                      type="button"
                      onClick={handleBrowseClick}
                      className="neo-btn neo-btn-ghost text-sm py-1 px-2"
                    >
                      Browse
                    </button>
                  </div>
                  <input
                    ref={fileInputRef}
                    type="file"
                    multiple
                    onChange={handleFileInputChange}
                    className="hidden"
                    accept=".ts,.tsx,.js,.jsx,.java,.py,.json,.xml,.yaml,.yml,.md,.css,.html,.sql,.log,.txt"
                  />
                  <p className="text-xs text-[var(--color-neo-text-secondary)] text-center mt-2">
                    .ts, .js, .java, .py, .json, .md, .txt and more
                  </p>
                </div>

                {/* Uploaded Files List */}
                {uploadedFiles.length > 0 && (
                  <div className="mt-3 space-y-2">
                    {uploadedFiles.map((file) => (
                      <div
                        key={file.id}
                        className="flex items-center gap-3 p-3 bg-[var(--color-neo-bg)] border-2 border-[var(--color-neo-border)] rounded"
                      >
                        <FileText size={18} className="text-[var(--color-neo-text-secondary)] flex-shrink-0" />
                        <span className="flex-1 font-mono text-sm truncate">{file.name}</span>
                        <span className="text-xs text-[var(--color-neo-text-secondary)]">
                          {Math.round(file.content.length / 1024)}KB
                        </span>
                        <button
                          type="button"
                          onClick={() => removeFile(file.id)}
                          className="neo-btn neo-btn-ghost p-1 text-red-600"
                        >
                          <Trash2 size={16} />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Actions */}
              <div className="flex gap-3 pt-4 border-t-3 border-[var(--color-neo-border)]">
                <button
                  type="submit"
                  disabled={!isDescribeValid || investigateBug.isPending}
                  className="neo-btn neo-btn-primary flex-1"
                >
                  {investigateBug.isPending ? (
                    <Loader2 size={18} className="animate-spin" />
                  ) : (
                    <>
                      <Search size={18} />
                      Investigate Bug
                    </>
                  )}
                </button>
                <button
                  type="button"
                  onClick={onClose}
                  className="neo-btn neo-btn-ghost"
                >
                  Cancel
                </button>
              </div>
            </form>
          )}

          {/* Phase 2: Investigating */}
          {phase === 'investigating' && (
            <div className="p-6 text-center py-16">
              <Loader2 size={48} className="animate-spin mx-auto text-[var(--color-neo-primary)] mb-4" />
              <p className="font-display text-xl font-bold mb-2">Investigating the bug...</p>
              <p className="text-[var(--color-neo-text-secondary)]">
                Searching the codebase to identify the root cause and fix steps.
              </p>
              <p className="text-sm text-[var(--color-neo-text-secondary)] mt-4">
                This may take a minute or two.
              </p>
            </div>
          )}

          {/* Phase 3: Review and submit */}
          {phase === 'review' && (
            <form onSubmit={handleCreateBugfix} className="p-6 space-y-4">
              {/* Error Message */}
              {error && (
                <div className="flex items-center gap-3 p-4 bg-[var(--color-neo-danger)] text-white border-3 border-[var(--color-neo-border)]">
                  <AlertCircle size={20} />
                  <span>{error}</span>
                  <button
                    type="button"
                    onClick={() => setError(null)}
                    className="ml-auto"
                  >
                    <X size={16} />
                  </button>
                </div>
              )}

              {/* Success banner */}
              <div className="flex items-center gap-3 p-4 bg-green-100 border-3 border-green-600 rounded">
                <CheckCircle size={24} className="text-green-600 flex-shrink-0" />
                <div>
                  <p className="font-display font-bold text-green-800">Investigation Complete</p>
                  <p className="text-green-700 text-sm">Review the suggested fix steps below and submit to create a bugfix task.</p>
                </div>
              </div>

              {/* Name */}
              <div>
                <label className="block font-display font-bold mb-2 uppercase text-sm">
                  Bug Fix Name
                </label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="neo-input"
                  required
                />
              </div>

              {/* Root Cause */}
              {rootCause && (
                <div>
                  <label className="block font-display font-bold mb-2 uppercase text-sm">
                    Root Cause
                  </label>
                  <textarea
                    value={rootCause}
                    onChange={(e) => setRootCause(e.target.value)}
                    className="neo-input min-h-[80px] resize-y"
                  />
                </div>
              )}

              {/* Affected Files */}
              {affectedFiles.length > 0 && (
                <div>
                  <label className="block font-display font-bold mb-2 uppercase text-sm">
                    Affected Files
                  </label>
                  <div className="neo-input bg-[var(--color-neo-bg)] font-mono text-sm">
                    {affectedFiles.map((file, i) => (
                      <div key={i} className="py-1">{file}</div>
                    ))}
                  </div>
                </div>
              )}

              {/* Steps */}
              <div>
                <label className="block font-display font-bold mb-2 uppercase text-sm">
                  Fix Steps
                </label>
                <div className="space-y-2">
                  {steps.map((step, index) => (
                    <div key={step.id} className="flex gap-2">
                      <span className="neo-input w-12 text-center flex-shrink-0 flex items-center justify-center bg-[var(--color-neo-bg)]">
                        {index + 1}
                      </span>
                      <input
                        type="text"
                        value={step.value}
                        onChange={(e) => handleStepChange(step.id, e.target.value)}
                        placeholder={`Step ${index + 1}`}
                        className="neo-input flex-1"
                      />
                      {steps.length > 1 && (
                        <button
                          type="button"
                          onClick={() => handleRemoveStep(step.id)}
                          className="neo-btn neo-btn-ghost p-2"
                        >
                          <Trash2 size={18} />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
                <button
                  type="button"
                  onClick={handleAddStep}
                  className="neo-btn neo-btn-ghost mt-2 text-sm"
                >
                  <Plus size={16} />
                  Add Step
                </button>
              </div>

              {/* Additional Notes */}
              {additionalNotes && (
                <div>
                  <label className="block font-display font-bold mb-2 uppercase text-sm">
                    Additional Notes
                  </label>
                  <textarea
                    value={additionalNotes}
                    onChange={(e) => setAdditionalNotes(e.target.value)}
                    className="neo-input min-h-[60px] resize-y"
                  />
                </div>
              )}

              {/* Original Description (collapsed) */}
              <details className="text-sm">
                <summary className="cursor-pointer font-display font-bold uppercase text-sm text-[var(--color-neo-text-secondary)] hover:text-[var(--color-neo-text)]">
                  Original Bug Description
                </summary>
                <div className="mt-2 p-4 bg-[var(--color-neo-bg)] border-2 border-[var(--color-neo-border)] rounded whitespace-pre-wrap font-mono text-sm">
                  {description}
                </div>
              </details>

              {/* Actions */}
              <div className="flex gap-3 pt-4 border-t-3 border-[var(--color-neo-border)]">
                <button
                  type="button"
                  onClick={() => setPhase('describe')}
                  className="neo-btn neo-btn-ghost"
                >
                  Back
                </button>
                <button
                  type="submit"
                  disabled={!isReviewValid || createFeature.isPending}
                  className="neo-btn neo-btn-success flex-1"
                >
                  {createFeature.isPending ? (
                    <Loader2 size={18} className="animate-spin" />
                  ) : (
                    <>
                      <Plus size={18} />
                      Create Bug Fix Task
                    </>
                  )}
                </button>
                <button
                  type="button"
                  onClick={onClose}
                  className="neo-btn neo-btn-ghost"
                >
                  Cancel
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  )
}
