# Routing Matrix

## Scoring

### Complexity Levels
- `C1`: Mechanical edits, no design choices, existing pattern copy.
- `C2`: Small feature in one module, minor decisions.
- `C3`: Cross-module behavior, important tradeoffs.
- `C4`: Architecture-level decisions, uncertain requirements.

### Risk Levels
- `R1`: Internal non-critical behavior.
- `R2`: User-facing but reversible changes.
- `R3`: Data integrity, migration, auth, billing, security, external contract risk.
- `R4`: High blast radius or production-critical path.

### Scope Levels
- `S1`: 1-2 files, under 200 LOC changed.
- `S2`: 3-8 files, under 800 LOC changed.
- `S3`: 9+ files or multiple services.
- `S4`: Repo-wide change, platform concern.

## Route Decision

- Route to `GLM-5` when `C1-C2` and `R1`, with `S1-S2`.
- Route to `Codex` when `C2-C3` or `R2-R3`, especially for debugging/refactor.
- Route to `Cloud Desktop` when `C4`, `R3-R4`, or final architecture acceptance.

Escalate immediately to Cloud Desktop if any of these are present:
- Authentication or authorization logic
- Payment/billing logic
- Security controls or secret handling
- Schema/data migration
- Public API contract changes

## DTO Policy

### Use GLM-5 for DTOs when
- Schema source already exists (OpenAPI/JSON Schema/proto/entity).
- Task is batch-like (`>= 3` DTOs or repeated mappers).
- Validation rules are explicit and already documented.

### Use Cloud Desktop directly when
- Only `1-2` DTOs and fields are still being defined.
- DTO impacts public API contract and backward compatibility.
- Validation semantics are ambiguous.

### Mandatory Review Depth
- For internal DTOs: naming, nullability, serialization format.
- For external DTOs: all above plus versioning, compatibility, and error model.

## Prompt Templates

### Template: Route to GLM-5

```text
Task: <one-line result>
Source of truth: <schema link/path>
Generate only: <dto files list>
Do not touch: <protected files>
Rules: preserve naming conventions, strict nullability, no extra fields
Output: unified diff + short notes
```

### Template: Route to Codex

```text
Goal: <one-line>
Context: <bug/refactor constraints>
Files in scope: <list>
Acceptance: <tests and behavior>
Return: plan (3-5 steps), patch, test commands, residual risks
```

### Template: Final Cloud Desktop Gate

```text
Review only this diff: <summary>
Focus: architecture consistency, contracts, migration/security concerns
Do not rewrite unrelated code
Return: approve/block + required fixes
```

## Anti-Patterns

- Re-explaining full architecture in every handoff.
- Sending full chat history instead of concise contract.
- Asking expensive model to generate boilerplate at scale.
- Running final merge without Cloud Desktop gate for medium/high-risk tasks.
