import { useState, useId, useRef, useCallback } from 'react'
import { X, Plus, Trash2, Loader2, AlertCircle, Sparkles, StopCircle, Upload, FileText } from 'lucide-react'
import { useCreateFeature } from '../hooks/useProjects'
import * as api from '../lib/api'

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

interface AddFeatureFormProps {
  projectName: string
  onClose: () => void
}

export function AddFeatureForm({ projectName, onClose }: AddFeatureFormProps) {
  const formId = useId()
  const [category, setCategory] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [priority, setPriority] = useState('')
  const [steps, setSteps] = useState<Step[]>([{ id: `${formId}-step-0`, value: '' }])
  const [error, setError] = useState<string | null>(null)
  const [stepCounter, setStepCounter] = useState(1)
  const [isExpanding, setIsExpanding] = useState(false)
  const [uploadedFiles, setUploadedFiles] = useState<UploadedFile[]>([])
  const [isDragging, setIsDragging] = useState(false)
  const abortControllerRef = useRef<AbortController | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

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
            path: (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name,
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

  const handleExpandWithAI = async () => {
    // If already expanding, cancel the operation
    if (isExpanding) {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort()
        abortControllerRef.current = null
      }
      setIsExpanding(false)
      return
    }

    if (!description.trim()) return
    setError(null)
    setIsExpanding(true)

    // Build full description including file contents
    let fullDescription = description
    if (uploadedFiles.length > 0) {
      fullDescription += '\n\n--- Related Files ---\n'
      for (const file of uploadedFiles) {
        fullDescription += `\n### File: ${file.name}\n\`\`\`\n${file.content}\n\`\`\`\n`
      }
    }

    // Create new AbortController for this request
    abortControllerRef.current = new AbortController()

    try {
      const result = await api.expandFeature(
        projectName,
        { description: fullDescription },
        abortControllerRef.current.signal
      )

      if (result.success) {
        // Populate form fields with AI-generated content
        if (result.name) setName(result.name)
        if (result.category) setCategory(result.category)
        if (result.description) setDescription(result.description)
        if (result.steps && result.steps.length > 0) {
          const newSteps = result.steps.map((s, i) => ({
            id: `${formId}-step-${i}`,
            value: s,
          }))
          setSteps(newSteps)
          setStepCounter(newSteps.length)
        }
      } else {
        setError(result.error || 'AI expansion failed')
      }
    } catch (err) {
      // Don't show error if it was cancelled by user
      if (err instanceof Error && err.name === 'AbortError') {
        // Request was cancelled - do nothing
      } else {
        setError(err instanceof Error ? err.message : 'AI expansion failed')
      }
    } finally {
      setIsExpanding(false)
      abortControllerRef.current = null
    }
  }

  const handleAddStep = () => {
    setSteps([...steps, { id: `${formId}-step-${stepCounter}`, value: '' }])
    setStepCounter(stepCounter + 1)
  }

  const handleRemoveStep = (id: string) => {
    setSteps(steps.filter(step => step.id !== id))
  }

  const handleStepChange = (id: string, value: string) => {
    setSteps(steps.map(step =>
      step.id === id ? { ...step, value } : step
    ))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    // Filter out empty steps
    const filteredSteps = steps
      .map(s => s.value.trim())
      .filter(s => s.length > 0)

    try {
      await createFeature.mutateAsync({
        category: category.trim(),
        name: name.trim(),
        description: description.trim(),
        steps: filteredSteps,
        priority: priority ? parseInt(priority, 10) : undefined,
      })
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create feature')
    }
  }

  const isValid = category.trim() && name.trim() && description.trim()

  return (
    <div className="neo-modal-backdrop">
      <div className="neo-modal w-full max-w-2xl">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b-3 border-[var(--color-neo-border)]">
          <h2 className="font-display text-2xl font-bold">
            Add Feature
          </h2>
          <button
            onClick={onClose}
            className="neo-btn neo-btn-ghost p-2"
          >
            <X size={24} />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
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

          {/* Category & Priority Row */}
          <div className="flex gap-4">
            <div className="flex-1">
              <label className="block font-display font-bold mb-2 uppercase text-sm">
                Category
              </label>
              <input
                type="text"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                placeholder="e.g., Authentication, UI, API"
                className="neo-input"
                required
              />
            </div>
            <div className="w-32">
              <label className="block font-display font-bold mb-2 uppercase text-sm">
                Priority
              </label>
              <input
                type="number"
                value={priority}
                onChange={(e) => setPriority(e.target.value)}
                placeholder="Auto"
                min="1"
                className="neo-input"
              />
            </div>
          </div>

          {/* Name */}
          <div>
            <label className="block font-display font-bold mb-2 uppercase text-sm">
              Feature Name
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g., User login form"
              className="neo-input"
              required
            />
          </div>

          {/* Description */}
          <div>
            <label className="block font-display font-bold mb-2 uppercase text-sm">
              Description
            </label>
            <div className="relative">
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Describe what this feature should do..."
                className="neo-input min-h-[100px] resize-y pr-12"
                required
              />
              {/* AI Expand Button - positioned in upper right of textarea */}
              <button
                type="button"
                onClick={handleExpandWithAI}
                disabled={!description.trim() && !isExpanding}
                className={`absolute top-2 right-2 p-1.5 rounded border-2 transition-all ${
                  isExpanding
                    ? 'bg-red-500 border-[var(--color-neo-border)] hover:scale-105 cursor-pointer'
                    : description.trim()
                    ? 'bg-[var(--color-neo-progress)] border-[var(--color-neo-border)] hover:scale-105 cursor-pointer'
                    : 'bg-gray-200 border-gray-300 opacity-50 cursor-not-allowed'
                }`}
                title={isExpanding ? 'Cancel AI expansion' : 'Expand with AI - fills in name, category, and steps'}
              >
                {isExpanding ? (
                  <StopCircle size={16} className="text-white" />
                ) : (
                  <Sparkles size={16} />
                )}
              </button>
            </div>
            <p className="text-xs text-[var(--color-neo-text-secondary)] mt-1">
              Type a description then click the <Sparkles size={12} className="inline" /> button to auto-fill the form with AI
            </p>
          </div>

          {/* File Upload Area */}
          <div>
            <label className="block font-display font-bold mb-2 uppercase text-sm">
              Related Files (Optional)
            </label>
            <div
              className={`border-3 border-dashed rounded-lg p-6 text-center transition-colors ${
                isDragging
                  ? 'border-[var(--color-neo-primary)] bg-blue-50'
                  : 'border-[var(--color-neo-border)] hover:border-[var(--color-neo-primary)]'
              }`}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
            >
              <Upload size={32} className="mx-auto mb-3 text-[var(--color-neo-text-secondary)]" />
              <p className="text-[var(--color-neo-text-secondary)] mb-2">
                Drag & drop files here, or
              </p>
              <button
                type="button"
                onClick={handleBrowseClick}
                className="neo-btn neo-btn-ghost text-sm"
              >
                Browse Files
              </button>
              <input
                ref={fileInputRef}
                type="file"
                multiple
                onChange={handleFileInputChange}
                className="hidden"
                accept=".ts,.tsx,.js,.jsx,.java,.py,.json,.xml,.yaml,.yml,.md,.css,.html,.sql,.log,.txt"
              />
              <p className="text-xs text-[var(--color-neo-text-secondary)] mt-3">
                Supports: .ts, .tsx, .js, .java, .py, .json, .md, .log, .txt and more
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

          {/* Steps */}
          <div>
            <label className="block font-display font-bold mb-2 uppercase text-sm">
              Test Steps (Optional)
            </label>
            <div className="space-y-2">
              {steps.map((step, index) => (
                <div key={step.id} className="flex gap-2">
                  <span className="neo-input w-12 text-center flex-shrink-0 flex items-center justify-center">
                    {index + 1}
                  </span>
                  <input
                    type="text"
                    value={step.value}
                    onChange={(e) => handleStepChange(step.id, e.target.value)}
                    placeholder="Describe this step..."
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

          {/* Actions */}
          <div className="flex gap-3 pt-4 border-t-3 border-[var(--color-neo-border)]">
            <button
              type="submit"
              disabled={!isValid || createFeature.isPending}
              className="neo-btn neo-btn-success flex-1"
            >
              {createFeature.isPending ? (
                <Loader2 size={18} className="animate-spin" />
              ) : (
                <>
                  <Plus size={18} />
                  Create Feature
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
      </div>
    </div>
  )
}
