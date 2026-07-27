# Issue tracker: GitHub

Issues and PRDs for this project live as GitHub issues in `Lzyuuu/ai-livesaver`. Use the `gh` CLI for all operations.

## Conventions

- **Create an issue**: `gh issue create --repo Lzyuuu/ai-livesaver --title "..." --body "..."`. Use a heredoc for multi-line bodies.
- **Read an issue**: `gh issue view <number> --repo Lzyuuu/ai-livesaver --comments`, filtering comments by `jq` and also fetching labels.
- **List issues**: `gh issue list --repo Lzyuuu/ai-livesaver --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'` with appropriate `--label` and `--state` filters.
- **Comment on an issue**: `gh issue comment <number> --repo Lzyuuu/ai-livesaver --body "..."`
- **Apply / remove labels**: `gh issue edit <number> --repo Lzyuuu/ai-livesaver --add-label "..."` / `--remove-label "..."`
- **Close**: `gh issue close <number> --repo Lzyuuu/ai-livesaver --comment "..."`

Always pass `--repo Lzyuuu/ai-livesaver`; the current local directory may not have a Git remote.

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature requests; `/triage` reads this flag.)_

When set to `yes`, PRs run through the same labels and states as issues, using the `gh pr` equivalents:

- **Read a PR**: `gh pr view <number> --repo Lzyuuu/ai-livesaver --comments` and `gh pr diff <number> --repo Lzyuuu/ai-livesaver` for the diff.
- **List external PRs for triage**: `gh pr list --repo Lzyuuu/ai-livesaver --state open --json number,title,body,labels,author,authorAssociation,comments` then keep only `authorAssociation` of `CONTRIBUTOR`, `FIRST_TIME_CONTRIBUTOR`, or `NONE`.
- **Comment / label / close**: use `gh pr comment`, `gh pr edit`, and `gh pr close` with `--repo Lzyuuu/ai-livesaver`.

GitHub shares one number space across issues and PRs, so a bare `#42` may be either—resolve with `gh pr view 42 --repo Lzyuuu/ai-livesaver` and fall back to `gh issue view 42 --repo Lzyuuu/ai-livesaver`.

## When a skill says "publish to the issue tracker"

Create an issue in `Lzyuuu/ai-livesaver`.

## When a skill says "fetch the relevant ticket"

Run `gh issue view <number> --repo Lzyuuu/ai-livesaver --comments`.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single issue with **child** issues as tickets.

- **Map**: a single issue labelled `wayfinder:map`, holding the Notes / Decisions-so-far / Fog body. Create it with `gh issue create --repo Lzyuuu/ai-livesaver --label wayfinder:map`.
- **Child ticket**: an issue linked to the map as a GitHub sub-issue. Where sub-issues aren't enabled, add the child to a task list in the map body and put `Part of #<map>` at the top of the child body. Labels are `wayfinder:<type>` (`research`, `prototype`, `grilling`, or `task`).
- **Blocking**: use GitHub's native issue dependencies. Where unavailable, add `Blocked by: #<n>, #<n>` at the top of the child body.
- **Frontier query**: list the map's open children and choose the first unblocked, unassigned issue in map order.
- **Claim**: `gh issue edit <n> --repo Lzyuuu/ai-livesaver --add-assignee @me`.
- **Resolve**: comment with the answer, close the child, then append a context pointer to the map's Decisions-so-far.
