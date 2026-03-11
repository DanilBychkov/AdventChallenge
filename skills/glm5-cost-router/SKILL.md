---
name: glm5-cost-router
description: Route software tasks between GLM-5, Codex, and Cloud Desktop/Claude Code to reduce token usage while preserving quality. Use when a user asks how to split work between cheaper and more expensive models, optimize subscription limits, decide who should implement/review a task, or define handoff prompts and quality gates for multi-model development workflows.
---

# GLM5 Cost Router

## Overview

Use this skill to choose the lowest-cost model that can complete a task safely. Keep Cloud Desktop as architecture and merge gate, use Codex for complex engineering, and use GLM-5 for high-volume low-risk implementation.

## Workflow

1. Classify task risk and complexity first.
2. Route to one model with a clear handoff contract.
3. Run required checks for that route.
4. Escalate only when checks fail.

## Classify Task

Score each incoming task:
- `Complexity`: how much design judgment is needed.
- `Risk`: possible user-facing, data, security, billing, auth, migration impact.
- `Scope`: number of files/modules and context size.

Use [routing-matrix.md](references/routing-matrix.md) for scoring levels, route rules, and escalation thresholds.

## Route Rules

Apply this default mapping:
- Send to `GLM-5`: low-risk repetitive code generation, DTOs, simple CRUD, boilerplate tests, documentation drafts.
- Send to `Codex`: tricky bugs, refactors across modules, performance work, CI/CD or migration logic, non-trivial test design.
- Send to `Cloud Desktop/Claude Code`: architecture decisions, final acceptance review, integration-sensitive edits, merge gate.

Always escalate to Cloud Desktop if auth, billing, security, data migration, or public API contracts are involved.

## Handoff Contract

Provide this contract to the target model:
- `Goal`: one-sentence outcome.
- `Allowed files`: explicit file list or directories.
- `Do not touch`: protected areas.
- `Definition of Done`: exact acceptance criteria.
- `Checks`: commands to run.
- `Output format`: short plan, diff, check results, risks.

Keep contracts short; pass schemas/spec links instead of re-describing large structures.

## Execution Loop

1. Generate implementation with routed model.
2. Run local checks and collect only key failures.
3. Fix once on same model.
4. Escalate to Codex or Cloud Desktop only if still failing or risk changes.
5. Run final Cloud Desktop review for architecture consistency.

## Cost Guardrails

- Avoid architecture discussion in expensive model sessions.
- Keep one task per session.
- Pass minimal context: target files, exact errors, compact diff summary.
- Batch repetitive low-risk tasks for GLM-5.
- Ask expensive model for focused review scope, not full rewrite.

## DTO Shortcut

For DTO-style tasks:
- Route to GLM-5 when schema already exists (OpenAPI, JSON Schema, DB model).
- Route to Cloud Desktop directly when only 1-2 DTOs are needed and schema is small.
- Require Cloud Desktop review only for public API or validation-rule changes.

Use templates in [routing-matrix.md](references/routing-matrix.md) to keep prompts compact.

## Deliverable Format

Return routing output as:
1. `Selected model` with short reason.
2. `Risk level` and escalation trigger.
3. `Handoff contract` ready to paste.
4. `Validation checklist` before merge.
